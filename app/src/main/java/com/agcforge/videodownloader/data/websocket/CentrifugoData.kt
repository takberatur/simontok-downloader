package com.agcforge.videodownloader.data.websocket

import com.google.gson.annotations.SerializedName

data class CentrifugoData(
    @SerializedName("event") val event: String,
    @SerializedName("payload") val payload: Any
)