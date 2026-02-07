package com.agcforge.videodownloader.data.dto

import com.google.gson.annotations.SerializedName

data class SubscriptionUpsertRequest(
	@SerializedName("original_transaction_id") val originalTransactionId: String,
	@SerializedName("product_id") val productId: String,
	@SerializedName("purchase_token") val purchaseToken: String,
	@SerializedName("platform") val platform: String = "android",
	@SerializedName("start_time_ms") val startTimeMs: Long,
	@SerializedName("end_time_ms") val endTimeMs: Long,
	@SerializedName("status") val status: String = "active",
	@SerializedName("auto_renew") val autoRenew: Boolean = true,
)

