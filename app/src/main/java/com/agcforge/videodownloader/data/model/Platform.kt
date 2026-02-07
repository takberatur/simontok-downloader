package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Platform(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("type") val type: String,
    @SerializedName("category") val category: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String,
    @SerializedName("url_pattern") val urlPattern: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_premium") val isPremium: Boolean,
    @SerializedName("config") val config: Map<String, String>? = null,
    @SerializedName("created_at") val createdAt: String
) : Parcelable

// API Response wrappers


