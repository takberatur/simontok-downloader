package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
@Parcelize
data class Role(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("created_at") val createdAt: String
) : Parcelable
