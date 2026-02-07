package com.agcforge.videodownloader.helper.converter

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class VideoToAudioConverter(private val context: Context) {

    private val TAG = "VideoToAudioConverter"

    private var transformer: Transformer? = null
    private var isConverting = false

    /**
     * Supported audio formats
     */
    enum class AudioFormat(val extension: String, val mimeType: String) {
        MP3(".mp3", MimeTypes.AUDIO_MPEG),
        M4A(".m4a", MimeTypes.AUDIO_AAC),
        AAC(".aac", MimeTypes.AUDIO_AAC),
        WAV(".wav", MimeTypes.AUDIO_WAV),
        FLAC(".flac", MimeTypes.AUDIO_FLAC),
        OGG(".ogg", "audio/ogg");

        companion object {
            fun fromExtension(extension: String): AudioFormat? {
                return entries.firstOrNull { it.extension == extension }
            }
            fun fromString(format: String): AudioFormat {
                return when (format.uppercase()) {
                    "MP3" -> MP3
                    "MP4" -> M4A
                    "M4A" -> M4A
                    "AAC" -> AAC
                    "WAV" -> WAV
                    "FLAC" -> FLAC
                    "OGG" -> OGG
                    else -> MP3 // Default to MP3
                }
            }
        }

    }

    /**
     * Audio quality settings
     */
    enum class AudioQuality(val bitrate: Int) {
        LOW(64_000),        // 64 kbps
        MEDIUM(128_000),    // 128 kbps
        HIGH(192_000),      // 192 kbps
        VERY_HIGH(320_000);  // 320 kbps

        companion object {
            fun fromString(quality: String): AudioQuality {
                val q = quality.trim().lowercase()
                return when {
                    q.startsWith("low") -> LOW
                    q.startsWith("medium") -> MEDIUM
                    q.startsWith("high") -> HIGH
                    q.startsWith("very_high") || q.startsWith("very high") || q.startsWith("veryhigh") -> VERY_HIGH
                    else -> MEDIUM
                }
            }
        }
    }

    /**
     * Convert video to audio with callbacks
     */
    fun convertVideoToAudio(
        sourceVideoPath: String,
        outputFormat: AudioFormat = AudioFormat.MP3,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        outputFileName: String? = null,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isConverting) {
            onError?.invoke("Conversion already in progress")
            return
        }

        try {
            val sourceFile = File(sourceVideoPath)
            if (!sourceFile.exists()) {
                onError?.invoke("Source video not found: $sourceVideoPath")
                return
            }

            // Generate output path in same folder
            val outputPath = generateOutputPath(sourceFile, outputFormat, outputFileName)

            // Start conversion
            startConversion(
                sourceUri = Uri.fromFile(sourceFile),
                outputPath = outputPath,
                outputFormat = outputFormat,
                audioQuality = audioQuality,
                onProgress = onProgress,
                onSuccess = onSuccess,
                onError = onError
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            onError?.invoke("Error: ${e.message}")
        }
    }

    /**
     * Convert video to audio using Uri
     */
    fun convertVideoToAudioWithUrl(
        sourceVideoUri: Uri,
        outputPath: String,
        outputFormat: AudioFormat = AudioFormat.MP3,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isConverting) {
            onError?.invoke("Conversion already in progress")
            return
        }

        startConversion(
            sourceUri = sourceVideoUri,
            outputPath = outputPath,
            outputFormat = outputFormat,
            audioQuality = audioQuality,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Convert video to audio using Uri
     */
    fun convertVideoToAudio(
        sourceVideoUri: Uri,
        outputPath: String,
        outputFormat: AudioFormat = AudioFormat.MP3,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isConverting) {
            onError?.invoke("Conversion already in progress")
            return
        }

        startConversion(
            sourceUri = sourceVideoUri,
            outputPath = outputPath,
            outputFormat = outputFormat,
            audioQuality = audioQuality,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Convert video to audio with suspend function (Coroutine)
     */
    suspend fun convertVideoToAudioSuspend(
        sourceVideoPath: String,
        outputFormat: AudioFormat = AudioFormat.MP3,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        outputFileName: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->

            convertVideoToAudio(
                sourceVideoPath = sourceVideoPath,
                outputFormat = outputFormat,
                audioQuality = audioQuality,
                outputFileName = outputFileName,
                onProgress = onProgress,
                onSuccess = { outputPath ->
                    continuation.resume(Result.success(outputPath))
                },
                onError = { error ->
                    continuation.resumeWithException(Exception(error))
                }
            )

            // Handle cancellation
            continuation.invokeOnCancellation {
                cancelConversion()
            }
        }
    }

    /**
     * Start conversion process
     */
    private fun startConversion(
        sourceUri: Uri,
        outputPath: String,
        outputFormat: AudioFormat,
        audioQuality: AudioQuality,
        onProgress: ((Int) -> Unit)?,
        onSuccess: ((String) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        isConverting = true

        try {
			val supportedAudioMimeTypes = setOf(MimeTypes.AUDIO_AAC)
			if (outputFormat.mimeType !in supportedAudioMimeTypes) {
				isConverting = false
				onError?.invoke("Format ${outputFormat.name} belum didukung di perangkat ini. Gunakan M4A/AAC.")
				return
			}

            // Create output file
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            // Build transformer
            transformer = Transformer.Builder(context)
                .setAudioMimeType(outputFormat.mimeType)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.d(TAG, "Conversion completed: $outputPath")
                        isConverting = false
                        onSuccess?.invoke(outputPath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Conversion error: ${exportException.message}", exportException)
                        isConverting = false

                        // Clean up failed output
                        outputFile.delete()

                        onError?.invoke(exportException.message ?: "Conversion failed")
                    }
                })
                .build()

            // Create media item with audio only
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)  // Remove video track, keep audio only
                .build()

            // Start export
            transformer?.start(editedMediaItem, outputPath)

            // Start progress monitoring
            startProgressMonitoring(onProgress)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting conversion: ${e.message}", e)
            isConverting = false
            onError?.invoke("Error: ${e.message}")
        }
    }


    /**
     * Monitor conversion progress
     */
    private fun startProgressMonitoring(onProgress: ((Int) -> Unit)?) {
        if (onProgress == null) return

        val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val progressHolder = androidx.media3.transformer.ProgressHolder()

        val progressRunnable = object : Runnable {
            override fun run() {
                if (!isConverting) {
                    progressHandler.removeCallbacks(this)
                    return
                }

                transformer?.let { transformer ->
                    val progressState = transformer.getProgress(progressHolder)

                    // PROGRESS_STATE_AVAILABLE = 0 (transformation in progress)
                    // PROGRESS_STATE_UNAVAILABLE = 1 (no progress info available)
                    // PROGRESS_STATE_WAITING_FOR_AVAILABILITY = 2 (waiting)
                    // PROGRESS_STATE_NOT_STARTED = 3 (not started)

                    if (progressState == 0) { // PROGRESS_STATE_AVAILABLE
                        val progressPercent = (progressHolder.progress * 100).toInt()
                        onProgress.invoke(progressPercent.coerceIn(0, 100))
                    }
                }

                // Update every 500ms
                progressHandler.postDelayed(this, 500)
            }
        }

        progressHandler.post(progressRunnable)
    }

    /**
     * Generate output path in same folder as source
     */
    /**
     * Generate output path in same folder as source
     */
    private fun generateOutputPath(
        sourceFile: File,
        format: AudioFormat,
        customFileName: String?
    ): String {
        val parentDir = sourceFile.parentFile ?: sourceFile.parentFile!!

        val baseFileName = if (customFileName != null) {
            customFileName
        } else {
            // Remove extension from source file
            val nameWithoutExt = sourceFile.nameWithoutExtension
            "${nameWithoutExt}_audio"
        }

        // Generate unique filename if file exists
        var outputFile = File(parentDir, "$baseFileName${format.extension}")
        var counter = 1

        while (outputFile.exists()) {
            outputFile = File(parentDir, "${baseFileName}_$counter${format.extension}")
            counter++
        }

        return outputFile.absolutePath
    }

    /**
     * Cancel ongoing conversion
     */
    fun cancelConversion() {
        if (isConverting) {
            Log.d(TAG, "Cancelling conversion")
            transformer?.cancel()
            isConverting = false
        }
    }

    /**
     * Check if conversion is in progress
     */
    fun isConverting(): Boolean = isConverting

    /**
     * Release resources
     */
    fun release() {
        cancelConversion()
        transformer = null
    }
}
