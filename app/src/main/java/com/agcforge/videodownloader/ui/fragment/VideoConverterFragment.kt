package com.agcforge.videodownloader.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import com.agcforge.videodownloader.databinding.FragmentVideoConverterBinding
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.helper.ads.BillingManager
import com.agcforge.videodownloader.helper.ads.NativeAdsHelper
import com.agcforge.videodownloader.helper.converter.AdvancedVideoToAudioConverter
import com.agcforge.videodownloader.helper.converter.ConversionManager
import com.agcforge.videodownloader.helper.converter.UriToFileConverter
import com.agcforge.videodownloader.helper.converter.VideoToAudioConverter
import com.agcforge.videodownloader.ui.activities.BaseActivity
import com.agcforge.videodownloader.ui.component.AppAlertDialog
import com.agcforge.videodownloader.utils.LocalDownloadsScanner
import com.agcforge.videodownloader.utils.MediaStorePublisher
import com.agcforge.videodownloader.utils.PermissionHelper
import com.agcforge.videodownloader.utils.PreferenceManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class VideoConverterFragment: Fragment() {
    private var _binding: FragmentVideoConverterBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager

    private var nativeAdsHelper: NativeAdsHelper? = null
    private var bannerAdsHelper: BannerAdsHelper? = null

    private var isPremium: Boolean = false

    private lateinit var advancedConverter: AdvancedVideoToAudioConverter
    private lateinit var conversionManager: ConversionManager

    private var localItem: LocalDownloadItem? = null
    private var selectedVideoUri: Uri? = null
    private var cachedVideoFile: File? = null

    private var selectedFormat: VideoToAudioConverter.AudioFormat = VideoToAudioConverter.AudioFormat.M4A
    private var selectedQuality: VideoToAudioConverter.AudioQuality = VideoToAudioConverter.AudioQuality.MEDIUM
    private var fileName: String = ""

    private lateinit var formatAdapter: ArrayAdapter<String>
    private lateinit var qualityAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoConverterBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        advancedConverter = AdvancedVideoToAudioConverter(requireContext())
        conversionManager = ConversionManager(requireContext())

        setupUI()
        setupListeners()
        setupAds()
        observePremiumAdsState()
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val formats = requireContext().resources.getStringArray(R.array.format_audio_list)
        formatAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_text, formats)
        (binding.actFormat as? AutoCompleteTextView)?.apply {
            setAdapter(formatAdapter)
            val first = formats.firstOrNull() ?: "M4A"
            setText(first, false)
            selectedFormat = VideoToAudioConverter.AudioFormat.fromString(first)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                selectedFormat = VideoToAudioConverter.AudioFormat.fromString(formats[position])
                setText(formats[position], false)
                updateQualityOptions()

                post { dismissDropDown() }
                clearFocus()
                binding.root.requestFocus()

                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)

                updateFileName()
                updateEstimatedSize()
            }
        }

        updateQualityOptions()
        // Setup file name input
        binding.etFileName.doAfterTextChanged { text ->
            fileName = text?.toString()?.trim() ?: ""
            validateInputs()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun updateQualityOptions() {
        val qualities = when (selectedFormat) {
            VideoToAudioConverter.AudioFormat.MP3 ->
                requireContext().resources.getStringArray(R.array.audio_quality_mp3)
            VideoToAudioConverter.AudioFormat.AAC,
            VideoToAudioConverter.AudioFormat.M4A ->
                requireContext().resources.getStringArray(R.array.audio_quality_aac)
            VideoToAudioConverter.AudioFormat.OGG ->
                requireContext().resources.getStringArray(R.array.audio_quality_mp3)
            else ->
                requireContext().resources.getStringArray(R.array.audio_quality_mp3)
        }

        qualityAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_text, qualities)
        (binding.actQuality as? AutoCompleteTextView)?.apply {
            setAdapter(qualityAdapter)
            val first = qualities.firstOrNull() ?: "MEDIUM"
            setText(first, false)
            selectedQuality = VideoToAudioConverter.AudioQuality.fromString(first)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                selectedQuality = VideoToAudioConverter.AudioQuality.fromString(qualities[position])
                setText(qualities[position], false)

                post { dismissDropDown() }
                clearFocus()
                binding.root.requestFocus()

                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)

                updateEstimatedSize()
            }
        }
    }

    private fun setupListeners() {
        binding.etVideoPath.setOnClickListener {
            pickVideo()
        }

        binding.tilVideo.setEndIconOnClickListener {
            pickVideo()
        }
        binding.btnConvert.setOnClickListener {
            if (validateInputs()) {
                val finalFileName = fileName.ifEmpty {
                    localItem?.displayName?.replace(Regex("[^a-zA-Z0-9\\s]"), "") ?: "audio"
                }

                showDialogAlert(
                    type = AppAlertDialog.AlertDialogType.WARNING,
                    title = getString(R.string.are_you_sure_you_want_to_convert_to_audio),
                    onAction = {
                        handlePremiumDownload {
                            startConversion(finalFileName)
                        }
                    }
                )
            }
        }
    }

    private fun pickVideo() {
        if (PermissionHelper.hasMediaPermissions(requireContext())) {
            pickVideoLauncher.launch(arrayOf("video/*"))
        } else {
            PermissionHelper.requestMediaPermissions(requireActivity())
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedVideo(it)
        }
    }

    private fun handleSelectedVideo(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true

                selectedVideoUri = uri

                // Get file info
                val fileName = UriToFileConverter.getFileName(requireContext(), uri)
                val fileSize = UriToFileConverter.getFileSize(requireContext(), uri)
                val duration = UriToFileConverter.getVideoDuration(requireContext(), uri)
                val mimeType = UriToFileConverter.getMimeType(requireContext(), uri)

                // Check if it's a video
                if (mimeType?.startsWith("video/") != true) {
                    binding.progressBar.isVisible = false
                    Toast.makeText(
                        requireContext(),
                        requireContext().getString(R.string.please_select_video_file),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Try to get direct file path first
                var filePath = UriToFileConverter.getFilePathFromUri(requireContext(), uri)

                // If direct path not available, copy to cache
                if (filePath == null) {
                    val result = UriToFileConverter.copyUriToCache(
                        context = requireContext(),
                        uri = uri,
                        onProgress = { progress ->
                            binding.progressBar.setProgress(progress)
                        }
                    )

                    if (result.isSuccess) {
                        cachedVideoFile = result.getOrNull()
                        filePath = cachedVideoFile?.absolutePath
                    } else {
                        binding.progressBar.isVisible = false
                        Toast.makeText(
                            requireContext(),
                            requireContext().getString(R.string.error_loading_video, result.exceptionOrNull()?.message),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                }

                binding.progressBar.isVisible = false

                // Update UI
                binding.etVideoPath.setText(fileName)
                binding.etFileName.setText(fileName.replace(Regex("[^a-zA-Z0-9\\s]"), ""))

                validateInputs()

                lifecycleScope.launch {
                    val mediaInfo = LocalDownloadsScanner.extractMediaMetadataLazy(
                        context = requireContext(),
                        uri = uri,
                        filePath = filePath,
                        true
                    )

                    localItem = LocalDownloadItem(
                        id = System.currentTimeMillis(),
                        displayName = fileName,
                        filePath = filePath,
                        uri = uri,
                        size = fileSize,
                        duration = duration,
                        mimeType = mimeType,
                        thumbnail = mediaInfo.thumbnail,
                        width = mediaInfo.width,
                        height = mediaInfo.height,
                        dateAdded = System.currentTimeMillis(),
                    )

                }


                // Setup data
                setupData()

                // Load thumbnail
                loadThumbnail(uri)

                Toast.makeText(
                    requireContext(),
                    requireContext().getString(R.string.video_loaded_successfully),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                Toast.makeText(
                    requireContext(),
                    requireContext().getString(R.string.error_loading_video, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadThumbnail(uri: Uri) {
        lifecycleScope.launch {
            try {
                val thumbnail = UriToFileConverter.getVideoThumbnail(requireContext(), uri)

                if (thumbnail != null) {
                    Glide.with(requireContext())
                        .load(thumbnail)
                        .placeholder(R.drawable.ic_video)
                        .error(R.drawable.ic_video)
                        .into(binding.ivVideoThumbnail)
                } else {
                    // Load from Uri directly
                    Glide.with(requireContext())
                        .load(uri)
                        .placeholder(R.drawable.ic_video)
                        .error(R.drawable.ic_video)
                        .into(binding.ivVideoThumbnail)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupData() {
        val item = localItem
        item?.let {
            binding.tvVideoTitle.text = it.displayName

            it.duration.let { duration ->
                binding.tvVideoDuration.text = it.getFormattedDuration()
            }

            val baseName = it.displayName.substringBeforeLast(".")
            binding.etFileName.setText(baseName)
            fileName = baseName

            updateEstimatedSize()
        }

        validateInputs()
    }


    private fun setupAds() {
        isPremium = BillingManager.isPremiumNow()
        applyPremiumAdsState(isPremium)
    }

    private fun observePremiumAdsState() {
        viewLifecycleOwner.lifecycleScope.launch {
            BillingManager.isPremium.collect { premiumStatus ->
                if (isPremium != premiumStatus) {
                    isPremium = premiumStatus
                    applyPremiumAdsState(premiumStatus)
                }
            }
        }
    }

    private fun applyPremiumAdsState(isPremium: Boolean) {
        if (isPremium) {
            binding.adsBanner.visibility = View.GONE
            binding.adsNative.visibility = View.GONE
            destroyAdsHelpers()
        } else {
            binding.adsBanner.visibility = View.VISIBLE
            binding.adsNative.visibility = View.VISIBLE

            val act = activity ?: return

            if (bannerAdsHelper == null) {
                bannerAdsHelper = BannerAdsHelper(act)
            }
            if (nativeAdsHelper == null) {
                nativeAdsHelper = NativeAdsHelper(act)
            }

            bannerAdsHelper?.loadAndAttachBanner(binding.adsBanner)
            nativeAdsHelper?.loadAndAttachNativeAd(
                binding.adsNative,
                NativeAdsHelper.NativeAdSize.MEDIUM
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateFileName() {
        if (fileName.isEmpty()) {
            val baseName = localItem?.displayName?.substringBeforeLast(".") ?: "audio"
            binding.etFileName.setText("$baseName${selectedFormat.extension}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateEstimatedSize() {
        localItem?.let {
            val duration = it.duration.toFloat()
            if (duration > 0 && it.filePath != null) {
                val estimatedSize = advancedConverter.estimateOutputSize(
                    sourceVideoPath = it.filePath!!,
                    audioQuality = selectedQuality
                )

                binding.tvEstimatedSize.text = requireContext().getString(
                    R.string.estimated_size,
                    formatFileSize(estimatedSize)
                )
            }
        }
    }

    private fun validateInputs(): Boolean {
        val hasVideo = localItem != null && localItem?.filePath != null
        val hasFileName = fileName.isNotEmpty()

        val isValid = hasVideo && hasFileName

        binding.btnConvert.isEnabled = isValid

        when {
            !hasVideo -> {
                binding.etVideoPath.error = requireContext().getString(R.string.please_select_video_file)
            }
            !hasFileName -> {
                binding.etFileName.error = requireContext().getString(R.string.file_name_is_required)
            }
            else -> {
                binding.etVideoPath.error = null
                binding.etFileName.error = null
            }
        }

        return isValid
    }

    @SuppressLint("DefaultLocale")
    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1_000_000_000 -> String.format("%.1f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.1f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.1f KB", size / 1_000.0)
            else -> "$size B"
        }
    }

    private fun startConversion(finalFileName: String) {
        val filePath = localItem?.filePath

        if (filePath == null) {
            Toast.makeText(
                requireContext(),
                "Video file not found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        advancedConversionToAudio(
            filePath = filePath,
            format = selectedFormat,
            quality = selectedQuality,
            fileName = finalFileName
        )
    }

    private fun destroyAdsHelpers() {
        bannerAdsHelper?.destroy()
        nativeAdsHelper?.destroy()
        bannerAdsHelper = null
        nativeAdsHelper = null
    }

    override fun onPause() {
        super.onPause()
        bannerAdsHelper?.pause()
    }

    override fun onResume() {
        super.onResume()
        bannerAdsHelper?.resume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        destroyAdsHelpers()

        cachedVideoFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        UriToFileConverter.cleanupCacheFiles(requireContext())

        _binding = null
    }

    fun Fragment.getBaseActivity(): BaseActivity? {
        return activity as? BaseActivity
    }

    fun Fragment.showInterstitialFromBase(onClosed: () -> Unit) {
        getBaseActivity()?.showInterstitial(onClosed)
    }

    private fun handlePremiumDownload(onComplete: () -> Unit) {
        val base = activity as? BaseActivity
        if (base == null) {
            // if base activity null,
            onComplete()
            return
        }

        base.showRewardAd { onRewarded ->
            if (onRewarded) {
                onComplete()
            } else {
                Toast.makeText(context, "Please watch the ad to download!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun advancedConversionToAudio(
        filePath: String,
        format: VideoToAudioConverter.AudioFormat,
        quality: VideoToAudioConverter.AudioQuality,
        fileName: String
    ) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true

            val config = AdvancedVideoToAudioConverter.ConversionConfig(
                outputFormat = format,
                audioQuality = quality,
                sampleRate = 44100,
                channels = 2,
                normalizeAudio = true,
                preserveMetadata = true,
                customFileName = fileName
            )

            try {
                val result = advancedConverter.convert(
                    sourceVideoPath = filePath,
                    config = config,
                    onProgress = { progress ->
                        binding.progressBar.setProgress(progress)
                    }
                )

                binding.progressBar.isVisible = false

                if (result.success) {
					val outputFile = result.outputPath?.let { File(it) }
					val publishedUri = if (outputFile != null && outputFile.exists()) {
						MediaStorePublisher.publishAudioToDownloads(requireContext(), outputFile)
					} else {
						null
					}
					if (outputFile != null && outputFile.absolutePath.startsWith(requireContext().cacheDir.absolutePath)) {
						runCatching { outputFile.delete() }
					}

                    showInterstitialFromBase {
                        AppAlertDialog.Builder(requireContext())
                            .setType(AppAlertDialog.AlertDialogType.SUCCESS)
                            .setTitle(requireContext().getString(R.string.converter_complete))
							.setMessage(requireContext().getString(R.string.output, publishedUri ?: result.outputPath))
                            .setPositiveButtonText(requireContext().getString(R.string.go_to_downloads))
                            .setNegativeButtonText(requireContext().getString(R.string.close))
                            .setOnPositiveClick {
                                findNavController().navigate(R.id.downloadsFragment)
                            }
                            .show()

                        clearUI()
                    }
                } else {
                    showDialogAlert(
                        AppAlertDialog.AlertDialogType.ERROR,
                        requireContext().getString(R.string.error),
                        requireContext().getString(R.string.convert_failed, result.error),
                        {
                            clearUI()
                        }
                    )

                }

            } catch (e: Exception) {
                binding.progressBar.isVisible = false

                showDialogAlert(
                    AppAlertDialog.AlertDialogType.ERROR,
                    requireContext().getString(R.string.error),
                     requireContext().getString(R.string.error_unknown ) + " " + e.message,
                    {
                        clearUI()
                    }
                )
            }
        }
    }

    private fun clearUI() {
        binding.etVideoPath.setText("")
        binding.etFileName.setText("")
        binding.tvVideoTitle.text = ""
        binding.tvVideoDuration.text = ""
        binding.tvEstimatedSize.text = ""
        binding.ivVideoThumbnail.setImageResource(R.drawable.ic_video)

        localItem = null
        selectedVideoUri = null
        fileName = ""

        // Clean up cached file
        cachedVideoFile?.delete()
        cachedVideoFile = null

        validateInputs()
    }

    private fun showDialogAlert(
        type: AppAlertDialog.AlertDialogType = AppAlertDialog.AlertDialogType.INFO,
        title: String? = null,
        message: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val dialog = AppAlertDialog
            .Builder(requireContext())
            .setNegativeButtonText(getString(R.string.cancel))
            .setPositiveButtonText(getString(R.string.ok))
            .setType(type)

        if (title != null) {
            dialog.setTitle(title)
        }
        if (message != null) {
            dialog.setMessage(message)
        }
        if (onAction != null) {
            dialog.setOnPositiveClick(onAction)
        }
        dialog.show()
    }

}
