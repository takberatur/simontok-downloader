package com.agcforge.videodownloader.data.websocket

import com.google.gson.annotations.SerializedName

data class DownloadProgress(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("progress") val progress: Int,
    @SerializedName("downloaded_bytes") val downloadedBytes: Long,
    @SerializedName("total_bytes") val totalBytes: Long,
    @SerializedName("speed") val speed: Long? = null,
    @SerializedName("eta") val eta: Long? = null
)