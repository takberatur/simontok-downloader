package com.agcforge.videodownloader.data.dto

import com.agcforge.videodownloader.data.model.User
import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("access_token") val token: String,
    @SerializedName("user") val user: User
)
data class CsrfResponse(
    @SerializedName("csrf_token") val csrfToken: String
)
