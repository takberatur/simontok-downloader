package com.agcforge.videodownloader.helper.converter

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object UriToFileConverter {

    /**
     * Get file path from Uri
     * Works for both content:// and file:// URIs
     */
    fun getFilePathFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> getPathFromContentUri(context, uri)
            "file" -> uri.path
            else -> null
        }
    }

    /**
     * Get file path from content:// Uri
     */
    private fun getPathFromContentUri(context: Context, uri: Uri): String? {
        // Try MediaStore first
        val projection = arrayOf(MediaStore.Video.Media.DATA)

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }

        // If MediaStore doesn't work, copy to cache
        return null
    }

    /**
     * Copy Uri content to cache directory
     * Use this when direct file path is not available
     */
    suspend fun copyUriToCache(
        context: Context,
        uri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val extension = getFileExtension(fileName)

            // Create cache file
            val cacheFile = File(
                context.cacheDir,
                "temp_video_${System.currentTimeMillis()}$extension"
            )

            // Copy content
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    val fileSize = getFileSize(context, uri)

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Report progress
                        if (fileSize > 0) {
                            val progress = (totalBytes * 100 / fileSize).toInt()
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            Result.success(cacheFile)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get file name from Uri
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "video"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }

        return fileName
    }

    /**
     * Get file size from Uri
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return size
    }

    /**
     * Get file extension
     */
    private fun getFileExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex != -1) {
            fileName.substring(dotIndex)
        } else {
            ".mp4" // Default extension
        }
    }

    /**
     * Get MIME type from Uri
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> context.contentResolver.getType(uri)
            "file" -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
            else -> null
        }
    }

    /**
     * Get video duration from Uri
     */
    fun getVideoDuration(context: Context, uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

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
     * Get video thumbnail from Uri
     */
    fun getVideoThumbnail(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val thumbnail = retriever.frameAtTime

            retriever.release()
            thumbnail

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean up cache files
     */
    fun cleanupCacheFiles(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_video_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}