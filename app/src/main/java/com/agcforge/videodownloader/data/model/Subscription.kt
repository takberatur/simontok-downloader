package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Subscription(
	@SerializedName("id") val id: String,
	@SerializedName("user_id") val userId: String? = null,
	@SerializedName("app_id") val appId: String? = null,
	@SerializedName("original_transaction_id") val originalTransactionId: String,
	@SerializedName("product_id") val productId: String,
	@SerializedName("purchase_token") val purchaseToken: String,
	@SerializedName("platform") val platform: String,
	@SerializedName("start_time") val startTime: String,
	@SerializedName("end_time") val endTime: String,
	@SerializedName("status") val status: String,
	@SerializedName("auto_renew") val autoRenew: Boolean,
	@SerializedName("created_at") val createdAt: String,
	@SerializedName("updated_at") val updatedAt: String,
) : Parcelable

