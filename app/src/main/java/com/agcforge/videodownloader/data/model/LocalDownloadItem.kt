package com.agcforge.videodownloader.data.model

import android.net.Uri
import com.agcforge.videodownloader.utils.LocalDownloadsScanner
import com.agcforge.videodownloader.utils.formatFileSize

data class LocalDownloadItem(
    val id: Long,
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val dateAdded: Long,
    val uri: Uri,
    val filePath: String?,
    val duration: Long = 0, // dalam milidetik
    val thumbnail: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val bitrate: Int = 0
) {

    fun getFormattedSize(): String = size.formatFileSize()

    fun getFormattedDuration(): String {
        return LocalDownloadsScanner.formatDuration(duration)
    }

    fun getFormattedDate(): String {
        return LocalDownloadsScanner.formatDate(dateAdded)
    }

    fun isVideo(): Boolean = mimeType?.startsWith("video/") == true

    fun isAudio(): Boolean = mimeType?.startsWith("audio/") == true

    fun getResolution(): String {
        return if (width > 0 && height > 0) {
            "${width}x$height"
        } else {
            "Unknown"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocalDownloadItem

        if (id != other.id) return false
        if (size != other.size) return false
        if (dateAdded != other.dateAdded) return false
        if (duration != other.duration) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (bitrate != other.bitrate) return false
        if (uri != other.uri) return false
        if (displayName != other.displayName) return false
        if (mimeType != other.mimeType) return false
        if (filePath != other.filePath) return false
        if (!thumbnail.contentEquals(other.thumbnail)) return false
        if (artist != other.artist) return false
        if (album != other.album) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + bitrate
        result = 31 * result + uri.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        return result
    }
}
