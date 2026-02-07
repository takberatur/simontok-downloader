package com.agcforge.videodownloader.helper.converter

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10

object FileUtils {

    /**
     * Get file path from Uri
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        var filePath: String? = null

        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                filePath = it.getString(columnIndex)
            }
        }

        return filePath
    }

    /**
     * Get Uri from file path
     */
    fun getUriFromPath(filePath: String): Uri {
        return Uri.fromFile(File(filePath))
    }

    /**
     * Get file name from path
     */
    fun getFileName(filePath: String): String {
        return File(filePath).name
    }

    /**
     * Get file name without extension
     */
    fun getFileNameWithoutExtension(filePath: String): String {
        return File(filePath).nameWithoutExtension
    }

    /**
     * Get file extension
     */
    fun getFileExtension(filePath: String): String {
        return File(filePath).extension
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(filePath: String): Long {
        return File(filePath).length()
    }

    /**
     * Format file size to human readable format
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

        val format = DecimalFormat("#,##0.#")
        return format.format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * Check if file exists
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * Delete file
     */
    fun deleteFile(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create directory if not exists
     */
    fun createDirectory(dirPath: String): Boolean {
        return try {
            val dir = File(dirPath)
            if (!dir.exists()) {
                dir.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get MIME type from file
     */
    fun getMimeType(filePath: String): String? {
        val extension = getFileExtension(filePath)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }

    /**
     * Check if file is video
     */
    fun isVideoFile(filePath: String): Boolean {
        val mimeType = getMimeType(filePath) ?: return false
        return mimeType.startsWith("video/")
    }

    /**
     * Check if file is audio
     */
    fun isAudioFile(filePath: String): Boolean {
        val mimeType = getMimeType(filePath) ?: return false
        return mimeType.startsWith("audio/")
    }

    /**
     * Get video duration using MediaMetadataRetriever
     */
    fun getVideoDuration(filePath: String): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            retriever.release()
            duration

        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Format duration to MM:SS or HH:MM:SS
     */
    @SuppressLint("DefaultLocale")
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Get video resolution
     */
    fun getVideoResolution(filePath: String): Pair<Int, Int>? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            val width = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0

            val height = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0

            retriever.release()

            if (width > 0 && height > 0) {
                Pair(width, height)
            } else {
                null
            }

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get available storage space
     */
    fun getAvailableStorageSpace(path: String): Long {
        return try {
            val file = File(path)
            val parentDir = file.parentFile ?: return 0L
            parentDir.freeSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if has enough storage space
     */
    fun hasEnoughStorageSpace(path: String, requiredBytes: Long): Boolean {
        val availableSpace = getAvailableStorageSpace(path)
        return availableSpace >= requiredBytes
    }
}