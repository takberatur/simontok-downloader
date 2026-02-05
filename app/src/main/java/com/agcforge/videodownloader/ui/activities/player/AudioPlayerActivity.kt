package com.agcforge.videodownloader.ui.activities.player

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.databinding.ActivityAudioPlayerBinding
import com.agcforge.videodownloader.utils.showToast
import com.chibde.visualizer.BarVisualizer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioPlayerBinding
    private var mediaPlayer: MediaPlayer? = null
    private var audioVisualizer: BarVisualizer? = null

    private lateinit var audioUri: Uri
    private var audioTitle: String = ""
    private var isPlaying = false
    private var currentPosition = 0
    private var playbackSpeed = 1.0f
    private var repeatMode = RepeatMode.OFF

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            handler.postDelayed(this, 1000)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                mediaPlayer?.let { setupAudioVisualizer(it.audioSessionId) }
            } else {
                binding.audioVisualizer.visibility = View.GONE
                showToast("Permission denied, audio visualizer will not be shown.")
            }
        }

    enum class RepeatMode {
        OFF, ONE, ALL
    }

    companion object {
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_AUDIO_TITLE = "audio_title"
        private const val KEY_POSITION = "position"

        fun start(context: Context, audioUri: String, title: String) {
            val intent = Intent(context, AudioPlayerActivity::class.java).apply {
                putExtra(KEY_AUDIO_URI, audioUri)
                putExtra(KEY_AUDIO_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.action == Intent.ACTION_VIEW) {
            audioUri = intent.data ?: Uri.EMPTY
            audioTitle = audioUri.lastPathSegment ?: "External Audio"
        } else {
            audioUri = intent.getStringExtra(KEY_AUDIO_URI)?.toUri() ?: Uri.EMPTY
            audioTitle = intent.getStringExtra(KEY_AUDIO_TITLE) ?: getString(R.string.app_name)
        }

        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getInt(KEY_POSITION, 0)
        }

        setupUI()
        setupMediaPlayer()
        setupListeners()
    }

    private fun setupUI() {
        // Set toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Set song info
        binding.tvSongTitle.text = audioTitle
        binding.tvArtist.text = getString(R.string.unknown_artist)


        // Enable marquee for song title
        binding.tvSongTitle.isSelected = true
    }

    private fun setupMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(this@AudioPlayerActivity, audioUri)

                setOnPreparedListener { player ->
                    binding.progressLoading.visibility = android.view.View.GONE

                    // Set total time
                    binding.tvTotalTime.text = formatTime(player.duration)
                    binding.seekBar.max = player.duration

                    // Seek to saved position
                    if (currentPosition > 0) {
                        player.seekTo(currentPosition)
                    }

                    // Setup audio visualizer
                    if (ContextCompat.checkSelfPermission(this@AudioPlayerActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        setupAudioVisualizer(player.audioSessionId)
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }

                    // Start playing
                    play()
                }

                setOnCompletionListener {
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            seekTo(0)
                            start()
                        }
                        RepeatMode.ALL -> {
                            // TODO: Play next song in playlist
                            seekTo(0)
                            start()
                        }
                        RepeatMode.OFF -> {
                            pause()
                            seekTo(0)
                            updatePlayPauseButton()
                        }
                    }
                }

                setOnErrorListener { _, what, extra ->
                    showToast("Error playing audio: $what, $extra")
                    true
                }

                prepareAsync()
            }

        } catch (e: Exception) {
            showToast("Failed to load audio: ${e.message}")
            finish()
        }
    }

    private fun setupAudioVisualizer(audioSessionId: Int) {
        try {
            audioVisualizer = binding.audioVisualizer
            audioVisualizer?.setDensity(resources.displayMetrics.density)
            binding.audioVisualizer.setPlayer(audioSessionId)
        } catch (e: Exception) {
            // Visualizer not supported
            binding.audioVisualizer.visibility = android.view.View.GONE
        }
    }

    private fun setupListeners() {
        // Play/Pause
        binding.fabPlayPause.setOnClickListener {
            if (isPlaying) {
                pause()
            } else {
                play()
            }
        }

        // Previous
        binding.btnPrevious.setOnClickListener {
            mediaPlayer?.let {
                val newPosition = (it.currentPosition - 10000).coerceAtLeast(0)
                it.seekTo(newPosition)
                updateSeekBar()
            }
        }

        // Next
        binding.btnNext.setOnClickListener {
            mediaPlayer?.let {
                val newPosition = (it.currentPosition + 10000).coerceAtMost(it.duration)
                it.seekTo(newPosition)
                updateSeekBar()
            }
        }

        // Shuffle
        binding.btnShuffle.setOnClickListener {
            showToast("Shuffle feature coming soon")
        }

        // Repeat
        binding.btnRepeat.setOnClickListener {
            toggleRepeatMode()
        }

        // SeekBar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer?.seekTo(seekBar?.progress ?: 0)
                handler.post(updateSeekBarRunnable)
            }
        })

        // Speed control
        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }

        // Timer
        binding.btnTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        // Equalizer
        binding.btnEqualizer.setOnClickListener {
            showToast("Equalizer feature coming soon")
        }
    }

    private fun play() {
        mediaPlayer?.start()
        isPlaying = true
        updatePlayPauseButton()
        handler.post(updateSeekBarRunnable)
        audioVisualizer?.visibility = android.view.View.VISIBLE
    }

    private fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        updatePlayPauseButton()
        handler.removeCallbacks(updateSeekBarRunnable)
        audioVisualizer?.visibility = android.view.View.GONE
    }

    private fun updatePlayPauseButton() {
        if (isPlaying) {
            binding.fabPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            binding.fabPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                binding.seekBar.progress = player.currentPosition
                binding.tvCurrentTime.text = formatTime(player.currentPosition)
            }
        }
    }

    private fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> {
                binding.btnRepeat.alpha = 1f
                showToast("Repeat One")
                RepeatMode.ONE
            }
            RepeatMode.ONE -> {
                showToast("Repeat All")
                RepeatMode.ALL
            }
            RepeatMode.ALL -> {
                binding.btnRepeat.alpha = 0.5f
                showToast("Repeat Off")
                RepeatMode.OFF
            }
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.playback_speed))
            .setItems(speeds) { dialog, which ->
                playbackSpeed = speedValues[which]

                // For API 23+
                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(playbackSpeed)!!

                binding.tvSpeed.text = speeds[which]
                showToast("Speed set to ${speeds[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val timers = arrayOf("Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour")
        val timerMinutes = intArrayOf(0, 5, 10, 15, 30, 60)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sleep_timer))
            .setItems(timers) { dialog, which ->
                if (timerMinutes[which] > 0) {
                    val milliseconds = timerMinutes[which] * 60 * 1000L
                    handler.postDelayed({
                        pause()
                        showToast("Sleep timer ended")
                    }, milliseconds)
                    showToast("Sleep timer set to ${timers[which]}")
                } else {
                    showToast("Sleep timer off")
                }
                dialog.dismiss()
            }
            .show()
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_POSITION, mediaPlayer?.currentPosition ?: 0)
    }

    override fun onPause() {
        super.onPause()
        currentPosition = mediaPlayer?.currentPosition ?: 0
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        audioVisualizer?.release()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
