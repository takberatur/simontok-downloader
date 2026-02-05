package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName

data class BootstrapResponse(
	@SerializedName("session_id") val sessionId: String,
	@SerializedName("session_secret") val sessionSecret: String,
	@SerializedName("expires_in") val expiresIn: Long
)

