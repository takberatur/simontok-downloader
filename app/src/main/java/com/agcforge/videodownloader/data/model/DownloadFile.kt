package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadFile(
    @SerializedName("id") val id: String,
    @SerializedName("download_id") val downloadId: String,
    @SerializedName("url") val url: String,
    @SerializedName("format_id") val formatId: String? = null,
    @SerializedName("resolution") val resolution: String? = null,
    @SerializedName("extension") val extension: String? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("created_at") val createdAt: String
) : Parcelable