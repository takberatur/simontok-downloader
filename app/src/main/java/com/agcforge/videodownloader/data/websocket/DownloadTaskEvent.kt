package com.agcforge.videodownloader.data.websocket

import com.agcforge.videodownloader.data.model.DownloadTask

sealed class DownloadTaskEvent {
    data class Created(val task: DownloadTask) : DownloadTaskEvent()
    data class Updated(val task: DownloadTask) : DownloadTaskEvent()
    data class StatusChanged(
        val taskId: String,
        val status: String,
        val progress: Int? = null,
        val errorMessage: String? = null
    ) : DownloadTaskEvent()
    data class ProgressUpdate(
        val taskId: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadTaskEvent()
    data class Completed(val task: com.agcforge.videodownloader.data.model.DownloadTask) : DownloadTaskEvent()
    data class Failed(val taskId: String, val error: String) : DownloadTaskEvent()
}