package com.agcforge.videodownloader.ui.activities.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.databinding.ActivityVideoPlayerBinding
import com.agcforge.videodownloader.ui.activities.BaseActivity
import com.agcforge.videodownloader.ui.component.AppAlertDialog
import com.agcforge.videodownloader.utils.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@UnstableApi
class VideoPlayerActivity : BaseActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    private var videoUri: Uri? = null
    private var videoTitle: String = ""
    private var currentPlaybackPosition: Long = 0
    private var isLocked = false
    private var isFullscreen = false
    private var isInterstitialLoaded = false

    private var backPressedTime: Long = 0
    private val EXIT_DELAY = 2000L // 2 second interval for double tap exit

    private var lastTouchTime: Long = 0
    private val DOUBLE_TAP_DELAY = 300L // 300ms

    companion object {
        private const val KEY_VIDEO_URI = "video_uri"
        private const val KEY_VIDEO_TITLE = "video_title"
        private const val KEY_PLAYBACK_POSITION = "playback_position"

        fun start(context: Context, videoUri: String, title: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(KEY_VIDEO_URI, videoUri)
                putExtra(KEY_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressedCallback()
        initializeData(savedInstanceState)
        setupUI()
        setupPlayer()
        setupListeners()
    }

    private fun initializeData(savedInstanceState: Bundle?) {
        if (intent.action == Intent.ACTION_VIEW) {
            videoUri = intent.data
            videoTitle = videoUri?.lastPathSegment ?: "External Video"
        } else {
            videoUri = intent.getStringExtra(KEY_VIDEO_URI)?.toUri()
            videoTitle = intent.getStringExtra(KEY_VIDEO_TITLE) ?: getString(R.string.app_name)
        }

        if (videoUri == null || videoUri.toString().isEmpty()) {
            showToast("Invalid video URL")
            finish()
            return
        }

        // Restore saved state
        if (savedInstanceState != null) {
            currentPlaybackPosition = savedInstanceState.getLong(KEY_PLAYBACK_POSITION, 0)
            isFullscreen = savedInstanceState.getBoolean("isFullscreen", false)
            isLocked = savedInstanceState.getBoolean("isLocked", false)
        }
    }

    private fun setupUI() {
        binding.tvVideoTitle.text = videoTitle
        hideSystemUI()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupTouchGestures()
        setupCustomControls()
    }

    private fun setupCustomControls() {
        binding.btnBack.setOnClickListener {
            handleBackPress()
        }
        binding.btnLock.setOnClickListener {
            toggleLock()
        }
        binding.btnRetry.setOnClickListener {
            retryPlayback()
        }
    }

    private fun initializePlayer() {
        showInterstitial {
            // Debug: Interstitial callback triggered
            showToast("Interstitial shown, initializing player")
            setupPlayer()
            setupListeners()
            isInterstitialLoaded = true
        }

        // Fallback: If interstitial does not show within 3 seconds, initialize player anyway
        binding.playerView.postDelayed({
            if (!isInterstitialLoaded) {
                showToast("Interstitial not loaded, initializing player anyway")
                setupPlayer()
                setupListeners()
                isInterstitialLoaded = true
            }
        }, 3000)
    }

    private fun setupPlayer() {
        try {
            // Create track selector
            trackSelector = DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            // Initialize ExoPlayer
            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector!!)
                .build()
                .also { exoPlayer ->
                    // Setup PlayerView
                    setupPlayerView(exoPlayer)

                    // Setup media
                    setupMedia(exoPlayer)

                    // Setup listeners
                    setupPlayerListeners(exoPlayer)
                }
        } catch (e: Exception) {
            showError(getString(R.string.failed_to_initialize_player, e.localizedMessage ?: getString(R.string.unknown_error)))
        }
    }

    private fun setupPlayerView(exoPlayer: ExoPlayer) {
        binding.playerView.player = exoPlayer
        binding.playerView.apply {
            useController = true
            controllerShowTimeoutMs = 3000
            controllerHideOnTouch = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
    }

    private fun setupMedia(exoPlayer: ExoPlayer) {
        videoUri?.let { uri ->
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // Restore playback position
            if (currentPlaybackPosition > 0) {
                exoPlayer.seekTo(currentPlaybackPosition)
            }

            exoPlayer.playWhenReady = true
        }
    }

    private fun setupPlayerListeners(exoPlayer: ExoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        binding.progressLoading.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        binding.progressLoading.visibility = View.GONE
                        binding.llError.visibility = View.GONE
                        updateVideoInfo()
                    }
                    Player.STATE_ENDED -> {
                        showVideoEndedDialog()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showError(getString(R.string.playback_error, error.localizedMessage ?: getString(R.string.unknown_error)))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateVideoInfo() {
        player?.let { exoPlayer ->
            val duration = exoPlayer.duration
            if (duration > 0) {
                binding.tvVideoTitle.text = "$videoTitle • ${formatDuration(duration)}"
            }
        }
    }

    private fun setupListeners() {
        setupPlayerViewControls()

        binding.playerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            handleOrientationChange()
        }
    }

    private fun setupPlayerViewControls() {
        setupCustomControllerButtons()
        addQualityButtonToController()
    }

    private fun setupCustomControllerButtons() {
        val fullscreenButton = binding.playerView.findViewById<ImageButton>(R.id.btnFullscreen)
        val qualityButton = binding.playerView.findViewById<ImageButton>(R.id.btnQuality)
        val speedButton = binding.playerView.findViewById<TextView>(R.id.btnSpeed)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btnSubtitle)

        fullscreenButton?.setOnClickListener {
            toggleFullscreen()
        }

        qualityButton?.setOnClickListener {
            showQualityDialog()
        }

        speedButton?.setOnClickListener {
            showSpeedDialog()
        }

        subtitleButton?.setOnClickListener {
            showToast(getString(R.string.subtitle_feature_coming_soon))
        }
    }

    private fun addQualityButtonToController() {
        // If custom controller doesn't have quality button, add it
        val controllerView = binding.playerView.findViewById<ViewGroup>(R.id.llBottomControls)
        if (controllerView?.findViewById<ImageButton>(R.id.btnQuality) == null) {
            val qualityButton = createQualityButton()
            controllerView?.addView(qualityButton)
        }
    }

    private fun createQualityButton(): ImageButton {
        return ImageButton(this).apply {
            id = R.id.btnQuality
            setImageResource(R.drawable.ic_settings)
            contentDescription = getString(R.string.quality)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                showQualityDialog()
            }
        }
    }

    private fun handleOrientationChange() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape != isFullscreen) {
            isFullscreen = isLandscape
            updateFullscreenUI()
        }
    }

    private fun toggleLock() {
        isLocked = !isLocked

        if (isLocked) {
            enterLockMode()
        } else {
            exitLockMode()
        }
    }

    private fun enterLockMode() {
        binding.playerView.hideController()
        binding.playerView.useController = false
        binding.btnBack.visibility = View.GONE
        binding.tvVideoTitle.visibility = View.GONE
        binding.btnLock.setImageResource(R.drawable.ic_lock_white)
        showToast(getString(R.string.controls_locked))
    }

    private fun exitLockMode() {
        binding.playerView.useController = true
        binding.playerView.showController()
        binding.btnBack.visibility = View.VISIBLE
        binding.tvVideoTitle.visibility = View.VISIBLE
        binding.btnLock.setImageResource(R.drawable.ic_lock_open_white)
        showToast(getString(R.string.controls_unlocked))
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen

        requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        if (isFullscreen) {
            hideSystemUI()
        } else {
            showSystemUI()
        }

        updateFullscreenUI()
    }

    private fun updateFullscreenUI() {
        if (isFullscreen) {
            binding.btnBack.visibility = View.GONE
            binding.tvVideoTitle.visibility = View.GONE
            binding.btnLock.visibility = View.GONE
        } else {
            binding.btnBack.visibility = View.VISIBLE
            binding.tvVideoTitle.visibility = View.VISIBLE
            binding.btnLock.visibility = View.VISIBLE
        }
    }

    private fun showQualityDialog() {
        trackSelector?.let { selector ->
            val mappedTrackInfo = selector.currentMappedTrackInfo
            if (mappedTrackInfo == null) {
                showToast(getString(R.string.no_quality_options_available))
                return
            }

            val videoTrackGroups = mappedTrackInfo.getTrackGroups(0)
            if (videoTrackGroups.length == 0) {
                showToast(getString(R.string.no_quality_options_available))
                return
            }

            val qualityOptions = mutableListOf<String>()
            qualityOptions.add("Auto")

            for (i in 0 until videoTrackGroups.length) {
                val trackGroup = videoTrackGroups.get(i)
                for (j in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(j)
                    format.height?.let { height ->
                        val quality = "${height}p"
                        if (!qualityOptions.contains(quality)) {
                            qualityOptions.add(quality)
                        }
                    }
                }
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.video_quality))
                .setItems(qualityOptions.toTypedArray()) { dialog, which ->
                    handleQualitySelection(which, selector, qualityOptions)
                    dialog.dismiss()
                }
                .show()
        } ?: showToast(getString(R.string.quality_selection_no_available))
    }

    private fun handleQualitySelection(
        which: Int,
        selector: DefaultTrackSelector,
        qualityOptions: List<String>
    ) {
        if (which == 0) {
            selector.parameters = selector.parameters
                .buildUpon()
                .clearSelectionOverrides()
                .build()
            showToast(getString(R.string.auto_quality_selected))
        } else {
            val selectedQuality = qualityOptions[which]
            showToast(getString(R.string.quality_selected) + selectedQuality)
            // Implement specific quality selection here
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedValues = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        val currentSpeed = player?.playbackParameters?.speed ?: 1.0f
        val currentIndex = speedValues.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 } ?: 3

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.playback_speed))
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                player?.setPlaybackSpeed(speedValues[which])
                showToast(getString(R.string.speed_set_to, speeds[which]))
                dialog.dismiss()
            }
            .show()
    }

    private fun showVideoEndedDialog() {
        AppAlertDialog.Builder(this)
            .setTitle(getString(R.string.video_ended))
            .setMessage(getString(R.string.do_you_want_to_replay_video))
            .setPositiveButtonText(getString(R.string.replay))
            .setNegativeButtonText(getString(R.string.close))
            .setOnPositiveClick {
                player?.seekTo(0)
                player?.playWhenReady = true
            }
            .show()
    }

    private fun retryPlayback() {
        binding.llError.visibility = View.GONE
        binding.progressLoading.visibility = View.VISIBLE
        releasePlayer()
        setupPlayer()
    }

    private fun showError(message: String) {
        binding.llError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        binding.progressLoading.visibility = View.GONE
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"

        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun handleBackPress() {
        when {
            isLocked -> {
                toggleLock()
            }
            isFullscreen -> {
                toggleFullscreen()
            }
            player?.isPlaying == true -> {
                showExitConfirmationDialog()
            }
            else -> {
                handleDoubleTapExit()
            }
        }
    }

    private fun showExitConfirmationDialog() {
        AppAlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_player))
            .setMessage(getString(R.string.video_is_still_playing_what_would_you_like_to_do))
            .setPositiveButtonText(getString(R.string.exit))
            .setNegativeButtonText(getString(R.string.cancel))
            .setOnPositiveClick {
                savePlaybackPosition()
                exitPlayer()
            }
            .setOnNegativeClick {
                player?.pause()
                savePlaybackPosition()
            }
            .show()
    }

    private fun handleDoubleTapExit() {
        if (backPressedTime + EXIT_DELAY > System.currentTimeMillis()) {
            exitPlayer()
        } else {
            showToast(getString(R.string.press_back_again_to_exit))
            backPressedTime = System.currentTimeMillis()
        }
    }

    private fun exitPlayer() {
        savePlaybackPosition()
        releasePlayer()
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun savePlaybackPosition() {
        player?.let {
            currentPlaybackPosition = it.currentPosition
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        binding.playerView.setOnTouchListener { view, event ->
            if (isLocked && event.action == MotionEvent.ACTION_DOWN) {
                handleLockedTouch(event)
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    private fun handleLockedTouch(event: MotionEvent) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTouchTime < DOUBLE_TAP_DELAY) {
            toggleLock()
        }
        lastTouchTime = currentTime
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savePlaybackPosition()
        outState.putLong(KEY_PLAYBACK_POSITION, currentPlaybackPosition)
        outState.putBoolean("isFullscreen", isFullscreen)
        outState.putBoolean("isLocked", isLocked)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isFullscreen = savedInstanceState.getBoolean("isFullscreen", false)
        isLocked = savedInstanceState.getBoolean("isLocked", false)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        savePlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        savePlaybackPosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        trackSelector = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newIsFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (newIsFullscreen != isFullscreen) {
            isFullscreen = newIsFullscreen
            updateFullscreenUI()
        }
    }
}