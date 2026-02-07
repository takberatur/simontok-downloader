package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName

data class DownloadRequest(
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("platform_id") val platformId: String? = null,
    @SerializedName("app_id") val appId: String? = null
)
