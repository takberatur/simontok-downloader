package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName
import java.util.Date

data class QueryParamsRequest(
    @SerializedName("search") val search: String? = null,
    @SerializedName("sort_by") val sortBy: String? = null,
    @SerializedName("order_by") val orderBy: String? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("include_deleted") val includeDeleted: Boolean? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("date_from") val dateFrom: Date? = null,
    @SerializedName("date_to") val dateTo: Date? = null,
    @SerializedName("extra") val extra: Map<String, Any>? = null
)

data class Pagination (
    @SerializedName("current_page") val currentPage: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("total_items") val total_items: Int? = null,
    @SerializedName("total_pages") val total_pages: Int? = null,
    @SerializedName("has_next") val hasNext: Boolean? = null,
    @SerializedName("has_prev") val hasPrev: Boolean? = null,
)