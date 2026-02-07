package com.agcforge.videodownloader.data.websocket

data class CentrifugoConfig(
    val url: String,
    val token: String? = null,
    val userId: String? = null
)