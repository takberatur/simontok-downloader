package com.agcforge.videodownloader.data.websocket

import com.agcforge.videodownloader.data.model.DownloadFormat
import com.google.gson.annotations.SerializedName

data class BackendDownloadEvent(
	@SerializedName("type") val type: String? = null,
	@SerializedName("task_id") val taskId: String? = null,
	@SerializedName("status") val status: String? = null,
	@SerializedName("progress") val progress: Int? = null,
	@SerializedName("message") val message: String? = null,
	@SerializedName("error") val error: String? = null,
	@SerializedName("created_at") val createdAt: String? = null,
	@SerializedName("payload") val payload: BackendDownloadPayload? = null
)

data class BackendDownloadPayload(
	@SerializedName("id") val id: String? = null,
	@SerializedName("status") val status: String? = null,
	@SerializedName("progress") val progress: Int? = null,
	@SerializedName("title") val title: String? = null,
	@SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
	@SerializedName("type") val type: String? = null,
	@SerializedName("created_at") val createdAt: String? = null,
	@SerializedName("file_path") val filePath: String? = null,
	@SerializedName("formats") val formats: List<DownloadFormat>? = null
)
