package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadFormat(
    @SerializedName("url") val url: String,
    @SerializedName("filesize") val filesize: Long? = null,
    @SerializedName("format_id") val formatId: String? = null,
    @SerializedName("acodec") val acodec: String? = null,
    @SerializedName("vcodec") val vcodec: String? = null,
    @SerializedName("ext") val ext: String? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("tbr") val tbr: Double? = null
) : Parcelable {
    fun getQualityLabel(): String {
        return when {
            height != null -> "${height}p"
            vcodec == "none" -> "Audio"
            else -> "Unknown"
        }
    }

    fun getFormatDescription(): String {
        val quality = getQualityLabel()
        val size = filesize?.let { formatFileSize(it) } ?: "Unknown size"
        return "$quality • $size • ${ext ?: "mp4"}"
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    fun getCodecInfo(): String {
        return when {
            acodec != null && vcodec != null -> "$acodec + $vcodec"
            acodec != null -> "Audio: $acodec"
            vcodec != null -> "Video: $vcodec"
            else -> "Unknown codec"
        }
    }

    fun getVideoCodecInfo(): String {
        return when {
            vcodec != null -> "Video: $vcodec"
            else -> "Unknown video codec"
        }
    }

   fun getTbrInfo(): String {
        return when {
            tbr != null -> "TBR: ${tbr} Mbps"
            else -> "Unknown TBR"
        }
    }

    fun extractHeight(): Int? {
        if (height != null) return height

        formatId?.let {
            val pattern = "(\\d+)p".toRegex()
            return pattern.find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        val urlPattern = ".*?(\\d{3,4})p.*".toRegex()
        return urlPattern.find(url)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getFileSizeFormatted(): String {
        return if (filesize != null) {
            when {
                filesize >= 1_000_000_000 -> "${filesize / 1_000_000_000} GB"
                filesize >= 1_000_000 -> "${filesize / 1_000_000} MB"
                filesize >= 1_000 -> "${filesize / 1_000} KB"
                else -> "$filesize B"
            }
        } else {
            "Unknown size"
        }
    }
}

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumArt: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioMetadata

        if (duration != other.duration) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (!albumArt.contentEquals(other.albumArt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = duration.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (albumArt?.contentHashCode() ?: 0)
        return result
    }
}