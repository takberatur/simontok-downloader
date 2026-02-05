package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadTask(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("app_id") val appId: String? = null,
    @SerializedName("platform_id") val platformId: String,
    @SerializedName("platform_type") val platformType: String,
    @SerializedName("original_url") val originalUrl: String,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("ip_address") val ipAddress: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("formats") val formats: List<DownloadFormat>? = null,
    @SerializedName("user") val user: User? = null,
    @SerializedName("application") val application: Application? = null,
    @SerializedName("platform") val platform: Platform? = null,
    @SerializedName("download_files") val downloadFiles: List<DownloadFile>? = null
) : Parcelable {
    fun getFormattedDuration(): String {
        val dur = duration ?: return "N/A"
        val minutes = dur / 60
        val seconds = dur % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DownloadTask
        return originalUrl == other.originalUrl
    }

    override fun hashCode(): Int {
        return originalUrl.hashCode()
    }

    override fun toString(): String {
        return super.toString()
    }
}

object FormatMerger {
    fun mergeTaskFormatsWithFilePath(tasks: List<DownloadTask>): List<DownloadFormat> {
        return tasks.flatMap { task ->
            val formats = task.formats ?: emptyList()

            task.filePath?.let { path ->
                val filePathFormat = createFormatFromTask(task, path)

                formats + filePathFormat
            } ?: formats
        }
            .map { format ->

                if (format.height == null) {
                    format.copy(height = format.extractHeight())
                } else {
                    format
                }
            }
            .distinctBy { format ->
                format.url.hashCode()
            }
    }

    fun getBestFormatFromTask(task: DownloadTask): DownloadFormat? {
        val allFormats = mutableListOf<DownloadFormat>()

        task.formats?.let { allFormats.addAll(it) }

        task.filePath?.let { path ->
            allFormats.add(createFormatFromTask(task, path))
        }

        return allFormats.maxByOrNull { format ->
            val height = format.extractHeight() ?: 0
            val filesize = format.filesize ?: 0L

            height * 1000000L + filesize
        }
    }

    fun createFormatFromTask(task: DownloadTask, filePath: String): DownloadFormat {
        val resolution = extractResolutionFromUrl(filePath)
        val fileExtension = filePath.substringAfterLast(".", "").takeIf { it.isNotEmpty() }
            ?: task.format
            ?: "mp4"

        return DownloadFormat(
            url = filePath,
            filesize = task.fileSize,
			formatId = "best",
            acodec = guessAudioCodec(fileExtension),
            vcodec = guessVideoCodec(fileExtension),
            ext = fileExtension,
            height = resolution,
            width = calculateWidth(resolution),
            tbr = calculateBitrate(task.fileSize, task.duration)
        )
    }

    private fun extractResolutionFromUrl(url: String): Int? {
        val patterns = listOf(
            ".*/(\\d{3,4})p.*".toRegex(),
            ".*[_-](\\d{3,4})p.*".toRegex(),
            ".*[_-](\\d{3,4})[_-].*".toRegex(),
            ".*best.*".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                if (url.contains("best", ignoreCase = true)) {
                    return 1080
                }
                return match.groupValues.getOrNull(1)?.toIntOrNull()
            }
        }
        return null
    }

    private fun guessAudioCodec(extension: String): String? {
        return when (extension.lowercase()) {
            "mp4", "m4a", "m4v" -> "aac"
            "webm" -> "opus"
            "mp3", "m4b" -> "mp3"
            "aac" -> "aac"
            "flac" -> "flac"
            else -> null
        }
    }

    private fun guessVideoCodec(extension: String): String? {
        return when (extension.lowercase()) {
            "mp4", "m4v" -> "h264"
            "webm" -> "vp9"
            "mkv", "avi" -> "h265"
            "mov" -> "prores"
            else -> null
        }
    }

    private fun calculateWidth(height: Int?): Int? {
        return height?.let {
            (it * 16) / 9
        }
    }

    private fun calculateBitrate(fileSize: Long?, duration: Int?): Double? {
        if (fileSize == null || duration == null || duration == 0) return null
        return (fileSize * 8.0) / (duration * 1000.0)
    }
}
