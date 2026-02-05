package com.agcforge.videodownloader.data.model

import com.google.gson.annotations.SerializedName

data class DownloadTaskView (
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("formats") val formats: List<DownloadFormat>? = null,
)

data class DownloadEvent (
    @SerializedName("type") val type: String,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("user_id") val userId: String ?= null,
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("message") val message: String,
    @SerializedName("error") val error: String,
    @SerializedName("payload") val payload: DownloadTaskView? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class DownloadState(
    val tasks: Map<String, DownloadTaskView>
)