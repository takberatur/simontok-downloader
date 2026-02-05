package com.agcforge.videodownloader.data.websocket

data class SubscriptionState(
    val channel: String,
    val isSubscribed: Boolean,
    val error: String? = null
)