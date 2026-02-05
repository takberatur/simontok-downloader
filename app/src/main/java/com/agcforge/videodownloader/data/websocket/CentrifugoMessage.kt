package com.agcforge.videodownloader.data.websocket

import com.google.gson.annotations.SerializedName

data class CentrifugoMessage(
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("data") val data: CentrifugoData? = null
)