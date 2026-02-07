package com.agcforge.videodownloader.ui.component

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.util.UnstableApi
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import com.agcforge.videodownloader.databinding.DialogConvertSettingsBinding
import com.agcforge.videodownloader.helper.converter.AdvancedVideoToAudioConverter
import com.agcforge.videodownloader.helper.converter.VideoToAudioConverter
import com.bumptech.glide.Glide

@UnstableApi
class ConvertSettingsDialog(
    context: Context,
    private val item: LocalDownloadItem? = null,
    private val onConvert: (
        format: VideoToAudioConverter.AudioFormat,  // ✅ Changed to enum
        quality: VideoToAudioConverter.AudioQuality, // ✅ Changed to enum
        name: String?
    ) -> Unit
) : Dialog(context) {
    private lateinit var binding: DialogConvertSettingsBinding
	private var selectedFormat: VideoToAudioConverter.AudioFormat = VideoToAudioConverter.AudioFormat.M4A
    private var selectedQuality: VideoToAudioConverter.AudioQuality = VideoToAudioConverter.AudioQuality.MEDIUM
    private var fileName: String = ""

    private lateinit var formatAdapter: ArrayAdapter<String>
    private lateinit var qualityAdapter: ArrayAdapter<String>

    private lateinit var audioConverter: AdvancedVideoToAudioConverter

    companion object {
        fun create(
            context: Context,
            item: LocalDownloadItem? = null,
            onConvert: (
                format: VideoToAudioConverter.AudioFormat,
                quality: VideoToAudioConverter.AudioQuality,
                name: String?
            ) -> Unit
        ): ConvertSettingsDialog {
            return ConvertSettingsDialog(context, item, onConvert)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogConvertSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioConverter = AdvancedVideoToAudioConverter(context)

        setupWindow()
        setupUI()
        setupListeners()
        setupData()
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val formats = context.resources.getStringArray(R.array.format_audio_list)
        formatAdapter = ArrayAdapter(context, R.layout.dropdown_item_text, formats)
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
                context.resources.getStringArray(R.array.audio_quality_mp3)
            VideoToAudioConverter.AudioFormat.AAC,
            VideoToAudioConverter.AudioFormat.M4A ->
                context.resources.getStringArray(R.array.audio_quality_aac)
            VideoToAudioConverter.AudioFormat.OGG ->
                context.resources.getStringArray(R.array.audio_quality_mp3)
            else ->
                context.resources.getStringArray(R.array.audio_quality_mp3)
        }

        qualityAdapter = ArrayAdapter(context, R.layout.dropdown_item_text, qualities)
		(binding.actQuality as? AutoCompleteTextView)?.apply {
            setAdapter(qualityAdapter)
            val first = qualities.firstOrNull() ?: "MEDIUM"
            setText(first, false)
            selectedQuality = VideoToAudioConverter.AudioQuality.fromString(first)
			setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                // ✅ Convert string to enum
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
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConvert.setOnClickListener {
            if (validateInputs()) {
                val finalFileName = fileName.ifEmpty {
                    item?.displayName?.replace(Regex("[^a-zA-Z0-9\\s]"), "") ?: "audio"
                }

                // ✅ Pass enums directly
                onConvert(selectedFormat, selectedQuality, finalFileName)
                dismiss()
            }
        }
    }

    private fun setupData() {
        item?.let {
            binding.tvVideoTitle.text = it.displayName

            // Set video duration
            it.duration.let { duration ->
                binding.tvVideoDuration.text = item.getFormattedDuration()
            }

            // Load thumbnail
            it.thumbnail?.let { thumbnailUrl ->
                Glide.with(context)
                    .load(thumbnailUrl)
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .into(binding.ivVideoThumbnail)
            }

            // Set initial file name
            val baseName = it.displayName.substringBeforeLast(".")
            binding.etFileName.setText(baseName)
            fileName = baseName

            // Update estimated size
            updateEstimatedSize()
        }

        validateInputs()
    }

    @SuppressLint("SetTextI18n")
    private fun updateFileName() {
        if (fileName.isEmpty()) {
            val baseName = item?.displayName?.substringBeforeLast(".") ?: "audio"
            binding.etFileName.setText("$baseName${selectedFormat.extension}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateEstimatedSize() {
        item?.let {
            val duration = it.duration.toFloat()
            if (duration > 0) {
                val estimatedSize = audioConverter.estimateOutputSize(
                    sourceVideoPath = it.filePath ?: "",
                    audioQuality = selectedQuality // ✅ Already enum
                )

                binding.tvEstimatedSize.text = context.getString(
                    R.string.estimated_size,
                    formatFileSize(estimatedSize)
                )
            }
        }
    }

    private fun validateInputs(): Boolean {
        val isValid = fileName.isNotEmpty()

        binding.btnConvert.isEnabled = isValid

        if (!isValid) {
            binding.etFileName.error = if (fileName.isEmpty()) "File name is required" else null
        } else {
            binding.etFileName.error = null
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

    @SuppressLint("DefaultLocale")
    private fun formatDuration(durationMs: Long): String {
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
}
