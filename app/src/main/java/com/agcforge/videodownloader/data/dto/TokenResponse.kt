package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("token") val token: String
)

