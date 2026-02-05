package com.agcforge.videodownloader.data.websocket

sealed class CentrifugoEvent {
    object Connecting : CentrifugoEvent()
    object Connected : CentrifugoEvent()
    object Disconnected : CentrifugoEvent()
    data class Error(val message: String, val exception: Throwable? = null) : CentrifugoEvent()
    data class MessageReceived(val channel: String, val data: Any) : CentrifugoEvent()
    data class SubscriptionSuccess(val channel: String) : CentrifugoEvent()
    data class SubscriptionError(val channel: String, val error: String) : CentrifugoEvent()
}