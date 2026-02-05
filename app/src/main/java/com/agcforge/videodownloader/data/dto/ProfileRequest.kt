package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName

data class ProfileRequest (
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String,
)
