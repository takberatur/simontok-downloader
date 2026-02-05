package com.agcforge.videodownloader.data.websocket

import com.google.gson.annotations.SerializedName

data class DownloadStatusUpdate(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null
)