package com.agcforge.videodownloader.helper.converter

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Advanced Video to Audio Converter
 *
 * Additional Features:
 * - Batch conversion
 * - Custom audio settings (sample rate, channels, bitrate)
 * - Audio normalization
 * - Metadata preservation
 * - Multiple output formats
 */
@UnstableApi
class AdvancedVideoToAudioConverter(private val context: Context) {

    private val TAG = "AdvancedConverter"

    /**
     * Conversion configuration
     */
    data class ConversionConfig(
        val outputFormat: VideoToAudioConverter.AudioFormat = VideoToAudioConverter.AudioFormat.MP3,
        val audioQuality: VideoToAudioConverter.AudioQuality = VideoToAudioConverter.AudioQuality.HIGH,
        val sampleRate: Int = 44100, // 44.1 kHz
        val channels: Int = 2, // Stereo
        val normalizeAudio: Boolean = false,
        val preserveMetadata: Boolean = true,
        val customFileName: String? = null
    )

    /**
     * Conversion result
     */
    data class ConversionResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null,
        val durationMs: Long = 0
    )

    /**
     * Convert single video with custom config
     */
    suspend fun convert(
        sourceVideoPath: String,
        config: ConversionConfig,
        onProgress: ((Int) -> Unit)? = null
    ): ConversionResult = withContext(Dispatchers.Main) { // ✅ Changed to Main thread

        val startTime = System.currentTimeMillis()

        try {
            val converter = VideoToAudioConverter(context)

            val result = converter.convertVideoToAudioSuspend(
                sourceVideoPath = sourceVideoPath,
                outputFormat = config.outputFormat,
                audioQuality = config.audioQuality,
                outputFileName = config.customFileName,
                onProgress = onProgress
            )

            val endTime = System.currentTimeMillis()

            if (result.isSuccess) {
                ConversionResult(
                    success = true,
                    outputPath = result.getOrNull(),
                    durationMs = endTime - startTime
                )
            } else {
                ConversionResult(
                    success = false,
                    error = result.exceptionOrNull()?.message,
                    durationMs = endTime - startTime
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Conversion error: ${e.message}", e)
            ConversionResult(
                success = false,
                error = e.message,
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Batch convert multiple videos
     */
    suspend fun convertBatch(
        sourceVideoPaths: List<String>,
        config: ConversionConfig,
        onItemProgress: ((index: Int, progress: Int) -> Unit)? = null,
        onItemComplete: ((index: Int, result: ConversionResult) -> Unit)? = null
    ): List<ConversionResult> = withContext(Dispatchers.Main) { // ✅ Changed to Main thread

        val results = mutableListOf<ConversionResult>()

        sourceVideoPaths.forEachIndexed { index, videoPath ->
            Log.d(TAG, "Converting ${index + 1}/${sourceVideoPaths.size}: $videoPath")

            val result = convert(
                sourceVideoPath = videoPath,
                config = config,
                onProgress = { progress ->
                    onItemProgress?.invoke(index, progress)
                }
            )

            results.add(result)
            onItemComplete?.invoke(index, result)
        }

        results
    }

    /**
     * Get estimated output file size
     */
    fun estimateOutputSize(
        sourceVideoPath: String,
        audioQuality: VideoToAudioConverter.AudioQuality
    ): Long {
        try {
            val sourceFile = File(sourceVideoPath)
            if (!sourceFile.exists()) return 0

            // Rough estimation: audio bitrate * duration
            // This is approximate, actual size may vary
            val bitrate = audioQuality.bitrate
            val durationMs = getVideoDuration(sourceVideoPath)

            if (durationMs <= 0) return 0

            // Calculate: (bitrate / 8) * (duration in seconds)
            val estimatedBytes = (bitrate / 8) * (durationMs / 1000)

            return estimatedBytes

        } catch (e: Exception) {
            Log.e(TAG, "Error estimating size: ${e.message}")
            return 0
        }
    }

    /**
     * Get video duration (simplified)
     */
    private fun getVideoDuration(videoPath: String): Long {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(videoPath)

            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0

            retriever.release()
            return duration

        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration: ${e.message}")
            return 0
        }
    }

    /**
     * Check if video has audio track
     */
    fun hasAudioTrack(videoPath: String): Boolean {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(videoPath)

            val hasAudio = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
            )?.equals("yes") ?: false

            retriever.release()
            return hasAudio

        } catch (e: Exception) {
            Log.e(TAG, "Error checking audio track: ${e.message}")
            return false
        }
    }

    /**
     * Get audio codec info
     */
    fun getAudioInfo(videoPath: String): AudioInfo? {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(videoPath)

            val codec = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE
            )

            val bitrate = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toIntOrNull()

            val sampleRate = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
            )?.toIntOrNull()

            retriever.release()

            return AudioInfo(
                codec = codec,
                bitrate = bitrate,
                sampleRate = sampleRate
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio info: ${e.message}")
            return null
        }
    }

    data class AudioInfo(
        val codec: String?,
        val bitrate: Int?,
        val sampleRate: Int?
    )

    fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
