package com.agcforge.videodownloader.utils

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.database.getStringOrNull
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object LocalDownloadsScanner {

    data class PagedResult(
        val items: List<LocalDownloadItem>,
        val hasMore: Boolean,
        val nextOffset: Int
    )

    data class MediaInfo(
        val thumbnail: ByteArray? = null,
        val duration: Long = 0,
        val width: Int = 0,
        val height: Int = 0,
        val bitrate: Int = 0,
        val artist: String? = null,
        val album: String? = null,
        val year: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MediaInfo

            if (duration != other.duration) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (bitrate != other.bitrate) return false
            if (thumbnail != null) {
                if (other.thumbnail == null) return false
                if (!thumbnail.contentEquals(other.thumbnail)) return false
            } else if (other.thumbnail != null) return false
            if (artist != other.artist) return false
            if (album != other.album) return false
            if (year != other.year) return false

            return true
        }

        override fun hashCode(): Int {
            var result = duration.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + bitrate
            result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
            result = 31 * result + (artist?.hashCode() ?: 0)
            result = 31 * result + (album?.hashCode() ?: 0)
            result = 31 * result + (year?.hashCode() ?: 0)
            return result
        }
    }

    object ThumbnailCache {

        private const val MAX_MEMORY = 20 * 1024 * 1024
        private val memoryCache = object : LruCache<Long, ByteArray>(MAX_MEMORY) {
            override fun sizeOf(key: Long, value: ByteArray): Int {
                return value.size
            }
        }

        fun put(itemId: Long, thumbnail: ByteArray) {
            memoryCache.put(itemId, thumbnail)
        }

        fun get(itemId: Long): ByteArray? = memoryCache.get(itemId)

        fun remove(itemId: Long) {
            memoryCache.remove(itemId)
        }

        fun clear() {
            memoryCache.evictAll()
        }

        fun getSize(): Int = memoryCache.size()

        fun getMaxSize(): Int = memoryCache.maxSize()
    }


    private val MEDIA_EXTENSIONS = arrayOf(
        "%.mp4", "%.mkv", "%.webm", "%.avi", "%.3gp", "%.mov", "%.ts",
        "%.mp3", "%.wav", "%.ogg", "%.m4a", "%.aac", "%.flac"
    )

    suspend fun scan(context: Context, location: String): List<LocalDownloadItem> {
        return withContext(Dispatchers.IO) {
            when (location) {
                "downloads" -> scanDownloadsFolder(context)
                "app" -> scanAppStorage(context)
                else -> emptyList()
            }
        }
    }

    suspend fun scanPaged(
        context: Context,
        location: String,
        limit: Int = 20,
        offset: Int = 0
    ): PagedResult {
        return withContext(Dispatchers.IO) {
            when (location) {
                "downloads" -> scanDownloadsFolderPaged(context, limit, offset)
                "app" -> scanAppStoragePaged(context, limit, offset)
                else -> PagedResult(emptyList(), false, 0)
            }
        }
    }

    private suspend fun scanDownloadsFolderPaged(
        context: Context,
        limit: Int,
        offset: Int
    ): PagedResult {
        return withContext(Dispatchers.IO) {
            val items = mutableListOf<LocalDownloadItem>()
            var totalCount = 0

            try {
                val contentResolver = context.contentResolver

                val extensionSelection = MEDIA_EXTENSIONS.joinToString(" OR ") {
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                }

                val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR $extensionSelection)"

                val countProjection = arrayOf("COUNT(*)")
                val countSelection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
                val countArgs = arrayOf("video/%", "audio/%")

                val selectionArgs = arrayOf("video/%", "audio/%") + MEDIA_EXTENSIONS

                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATA
                )

                contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    totalCount = cursor.count

                    if (!cursor.moveToPosition(offset)) {
                        return@use
                    }

                    var processedCount = 0

                    do {
                        try {
                            if (processedCount >= limit) break

                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                                ?: continue
                            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                            var mimeType = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))
                            if (mimeType == null || mimeType == "application/octet-stream") {
                                mimeType = getMimeType(displayName)
                            }
                            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                            val data = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))

//                            val uri = ContentUris.withAppendedId(
//                                MediaStore.Files.getContentUri("external"),
//                                id
//                            )

                            val dataPath = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                            val uri = if (dataPath != null) {
                                Uri.fromFile(File(dataPath))
                            } else {
                                ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                            }

                            val mediaInfo = if (offset == 0) {
                                extractMediaMetadataLazy(context, uri, data, true)
                            } else {
                                extractMediaMetadataLazy(context, uri, data, false)
                            }

                            val item = LocalDownloadItem(
                                id = id,
                                displayName = displayName,
                                size = size,
                                mimeType = mimeType,
                                dateAdded = dateAdded,
                                uri = uri,
                                filePath = data,
                                duration = mediaInfo.duration,
                                thumbnail = mediaInfo.thumbnail,
                                width = mediaInfo.width,
                                height = mediaInfo.height
                            )

                            items.add(item)
                            processedCount++
                        } catch (e: Exception) {
                            continue
                        }
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val hasMore = (offset + limit) < totalCount
            val nextOffset = if (hasMore) offset + limit else offset

            PagedResult(items, hasMore, nextOffset)
        }
    }

    private suspend fun scanAppStoragePaged(
        context: Context,
        limit: Int,
        offset: Int
    ): PagedResult {
        return withContext(Dispatchers.IO) {
            val items = mutableListOf<LocalDownloadItem>()
            var totalCount = 0

            try {
                val appDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    File(Environment.getExternalStorageDirectory(), "Download")
                }

                appDir?.let { directory ->
                    if (directory.exists() && directory.isDirectory) {
                        val allFiles = directory.listFiles()?.filter { file ->
                            file.isFile && (isMediaFile(file.name, null) || hasMediaExtension(file.name))
                        }?.sortedByDescending { it.lastModified() } ?: emptyList()

                        totalCount = allFiles.size
                        val startIndex = offset.coerceAtMost(totalCount - 1)
                        val endIndex = (offset + limit).coerceAtMost(totalCount)

                        for (i in startIndex until endIndex) {
                            try {
                                val file = allFiles[i]
                                val uri = Uri.fromFile(file)
                                val mimeType = getMimeType(file.name) ?: "video/mp4"

                                // Lazy metadata extraction
                                val mediaInfo = if (offset == 0 && i < 10) {
                                    extractMediaMetadataLazy(context, uri, file.absolutePath, true)
                                } else {
                                    extractMediaMetadataLazy(context, uri, file.absolutePath, false)
                                }


                                val item = LocalDownloadItem(
                                    id = file.hashCode().toLong(),
                                    displayName = file.name,
                                    size = file.length(),
                                    mimeType = mimeType,
                                    dateAdded = file.lastModified() / 1000,
                                    uri = uri,
                                    filePath = file.absolutePath,
                                    duration = mediaInfo.duration,
                                    thumbnail = mediaInfo.thumbnail,
                                    width = mediaInfo.width,
                                    height = mediaInfo.height
                                )

                                items.add(item)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val hasMore = (offset + items.size) < items.size
            val nextOffset = if (hasMore) offset + items.size else offset

            PagedResult(items, hasMore, nextOffset)
        }
    }

    suspend fun extractMediaMetadataLazy(
        context: Context,
        uri: Uri,
        filePath: String?,
        extractThumbnail: Boolean
    ): MediaInfo = withContext(Dispatchers.IO) {
        if (!extractThumbnail) {
            return@withContext extractDurationOnly(context, uri, filePath)
        }

        val retriever = MediaMetadataRetriever()

        try {
            if (filePath != null && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            val widthStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )
            val heightStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )

            val thumbnailBitmap = retriever.getFrameAtTime(
                1000000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            val thumbnailByteArray = thumbnailBitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }

            MediaInfo(
                thumbnail = thumbnailByteArray,
                duration = durationStr?.toLongOrNull() ?: 0,
                width = widthStr?.toIntOrNull() ?: 0,
                height = heightStr?.toIntOrNull() ?: 0
            )

        } catch (e: Exception) {
            MediaInfo()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun extractDurationOnly(
        context: Context,
        uri: Uri,
        filePath: String?
    ): MediaInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (filePath != null && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )

            MediaInfo(duration = durationStr?.toLongOrNull() ?: 0)

        } catch (e: Exception) {
            MediaInfo()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun loadThumbnailForItem(
        context: Context,
        item: LocalDownloadItem
    ): ByteArray? = withContext(Dispatchers.IO) {

        ThumbnailCache.get(item.id)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()

        try {
            if (item.filePath != null && File(item.filePath).exists()) {
                retriever.setDataSource(item.filePath)
            } else {
                retriever.setDataSource(context, item.uri)
            }

            val thumbnailBitmap = retriever.getFrameAtTime(
                1000000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            val thumbnailByteArray = thumbnailBitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                stream.toByteArray()
            }

            thumbnailByteArray?.let {
                ThumbnailCache.put(item.id, it)
            }

            thumbnailByteArray ?: item.thumbnail
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun File.hasSupportedExtension(): Boolean = name.hasSupportedExtension()

    private fun String.hasSupportedExtension(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mp3")
    }

    private fun File.toMimeType(): String = name.toMimeType()

    private fun String.toMimeType(): String {
        val lower = lowercase()
        return when {
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private suspend fun scanDownloadsFolder(context: Context): List<LocalDownloadItem> {
        return withContext(Dispatchers.IO) {
            val items = mutableListOf<LocalDownloadItem>()

            println("DEBUG [Scanner]: Starting scanDownloadsFolder")

            try {
                val contentResolver = context.contentResolver
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATA
                )

                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
                val selectionArgs = arrayOf("video/%", "audio/%")

                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                println("DEBUG [Scanner]: Querying MediaStore...")

                contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    println("DEBUG [Scanner]: Cursor count: ${cursor.count}")

                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idColumn)
                            val displayName = cursor.getString(nameColumn) ?: continue
                            val size = cursor.getLong(sizeColumn)
                            val mimeType = cursor.getStringOrNull(mimeTypeColumn)
                            val dateAdded = cursor.getLong(dateColumn)
                            val data = cursor.getStringOrNull(dataColumn)

                            println("DEBUG [Scanner]: Found file: $displayName, mime: $mimeType, path: $data")

                            val uri = ContentUris.withAppendedId(
                                MediaStore.Files.getContentUri("external"),
                                id
                            )

                            val item = LocalDownloadItem(
                                id = id,
                                displayName = displayName,
                                size = size,
                                mimeType = mimeType,
                                dateAdded = dateAdded,
                                uri = uri,
                                filePath = data,
                                duration = 0,
                                thumbnail = null,
                                width = 0,
                                height = 0
                            )

                            items.add(item)
                        } catch (e: Exception) {
                            println("DEBUG [Scanner]: Error processing row: ${e.message}")
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                println("DEBUG [Scanner]: Exception in scanDownloadsFolder: ${e.message}")
                e.printStackTrace()
            }

            println("DEBUG [Scanner]: Total items found: ${items.size}")
            items
        }
    }

    private suspend fun scanAppStorage(context: Context): List<LocalDownloadItem> {
        return withContext(Dispatchers.IO) {
            val items = mutableListOf<LocalDownloadItem>()

            try {
                val appDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    File(Environment.getExternalStorageDirectory(), "Download")
                }

                appDir?.let { directory ->
                    if (directory.exists() && directory.isDirectory) {
                        directory.listFiles()?.filter { file ->
                            isMediaFile(file.name, null) && file.isFile
                        }?.forEach { file ->
                            try {
                                val uri = Uri.fromFile(file)

                                val mediaInfo = extractMediaMetadata(context, uri, file.absolutePath)

                                val item = LocalDownloadItem(
                                    id = file.hashCode().toLong(),
                                    displayName = file.name,
                                    size = file.length(),
                                    mimeType = getMimeType(file.name),
                                    dateAdded = file.lastModified() / 1000,
                                    uri = uri,
                                    filePath = file.absolutePath,
                                    duration = mediaInfo.duration,
                                    thumbnail = mediaInfo.thumbnail,
                                    width = mediaInfo.width,
                                    height = mediaInfo.height
                                )

                                items.add(item)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            items.sortedByDescending { it.dateAdded }
        }
    }

    private fun isMediaFile(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())

        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "m4v")
        val audioExtensions = listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")

        return when {
            mimeType != null -> mimeType.startsWith("video/") || mimeType.startsWith("audio/")
            else -> extension in videoExtensions || extension in audioExtensions
        }
    }

    private fun getMimeType(fileName: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            "video/mp4"
        }

//        return when (fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())) {
//            "mp4", "m4v" -> "video/mp4"
//            "mkv" -> "video/x-matroska"
//            "avi" -> "video/x-msvideo"
//            "mov" -> "video/quicktime"
//            "wmv" -> "video/x-ms-wmv"
//            "flv" -> "video/x-flv"
//            "3gp" -> "video/3gpp"
//            "webm" -> "video/webm"
//            "mp3" -> "audio/mpeg"
//            "wav" -> "audio/wav"
//            "aac" -> "audio/aac"
//            "flac" -> "audio/flac"
//            "ogg" -> "audio/ogg"
//            "m4a" -> "audio/mp4"
//            "wma" -> "audio/x-ms-wma"
//            else -> "application/octet-stream"
//        }
    }

    private suspend fun extractMediaMetadata(
        context: Context,
        uri: Uri,
        filePath: String?
    ): MediaInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (filePath != null && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            val widthStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )
            val heightStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )
            val bitrateStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )
            val artist = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            )
            val album = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            )
            val year = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_YEAR
            )

            val thumbnailBitmap = retriever.getFrameAtTime(
                1000000, // 1 second
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            val thumbnailByteArray = thumbnailBitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }

            return@withContext MediaInfo(
                thumbnail = thumbnailByteArray,
                duration = durationStr?.toLongOrNull() ?: 0,
                width = widthStr?.toIntOrNull() ?: 0,
                height = heightStr?.toIntOrNull() ?: 0,
                bitrate = bitrateStr?.toIntOrNull() ?: 0,
                artist = artist,
                album = album,
                year = year
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext MediaInfo()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    @SuppressLint("DefaultLocale")
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"

        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(timestamp * 1000)
    }

    private fun hasMediaExtension(fileName: String): Boolean {
        val extensions = arrayOf(
            "mp4", "mkv", "webm", "avi", "3gp", "mov", "ts", "m3u8",
            "mp3", "wav", "ogg", "m4a", "aac", "flac"
        )
        val fileExtension = fileName.substringAfterLast('.', "").lowercase()
        return extensions.contains(fileExtension)
    }
}