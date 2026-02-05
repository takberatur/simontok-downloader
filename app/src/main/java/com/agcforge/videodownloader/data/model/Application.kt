package com.agcforge.videodownloader.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Application(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("package_name") val packageName: String,
    @SerializedName("version") val version: String? = null,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("enable_monetization") val enableMonetization: Boolean,
    @SerializedName("enable_admob") val enableAdmob: Boolean,
    @SerializedName("enable_unity_ad") val enableUnityAd: Boolean,
    @SerializedName("enable_start_app") val enableStartApp: Boolean,
    @SerializedName("enable_in_app_purchase") val enableInAppPurchase: Boolean,
    @SerializedName("admob_ad_unit_id") val admobAdUnitId: String? = null,
    @SerializedName("unity_ad_unit_id") val unityAdUnitId: String? = null,
    @SerializedName("start_app_ad_unit_id") val startAppAdUnitId: String? = null,
    @SerializedName("admob_banner_ad_unit_id") val admobBannerAdUnitId: String? = null,
    @SerializedName("admob_interstitial_ad_unit_id") val admobInterstitialAdUnitId: String? = null,
    @SerializedName("admob_native_ad_unit_id") val admobNativeAdUnitId: String? = null,
    @SerializedName("admob_rewarded_ad_unit_id") val admobRewardedAdUnitId: String? = null,
    @SerializedName("unity_banner_ad_unit_id") val unityBannerAdUnitId: String? = null,
    @SerializedName("unity_interstitial_ad_unit_id") val unityInterstitialAdUnitId: String? = null,
    @SerializedName("unity_native_ad_unit_id") val unityNativeAdUnitId: String? = null,
    @SerializedName("unity_rewarded_ad_unit_id") val unityRewardedAdUnitId: String? = null,
    @SerializedName("one_signal_id") val oneSignalId: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("in_app_products") val inAppProducts: List<InAppProduct>? = null
) : Parcelable {

    fun isAdmobEnabled(): Boolean = enableMonetization && enableAdmob

    fun isUnityAdEnabled(): Boolean = enableMonetization && enableUnityAd

    fun isStartAppEnabled(): Boolean = enableMonetization && enableStartApp

    fun isInAppPurchaseEnabled(): Boolean = enableMonetization && enableInAppPurchase

    fun getAdmobBannerId(): String? = if (isAdmobEnabled()) admobBannerAdUnitId else null

    fun getAdmobInterstitialId(): String? = if (isAdmobEnabled()) admobInterstitialAdUnitId else null

    fun getAdmobRewardedId(): String? = if (isAdmobEnabled()) admobRewardedAdUnitId else null

    fun getUnityBannerId(): String? = if (isUnityAdEnabled()) unityBannerAdUnitId else null

    fun getUnityInterstitialId(): String? = if (isUnityAdEnabled()) unityInterstitialAdUnitId else null

    fun getUnityRewardedId(): String? = if (isUnityAdEnabled()) unityRewardedAdUnitId else null

    fun isValid(): Boolean = id.isNotEmpty() && name.isNotEmpty() && packageName.isNotEmpty()
}

@Parcelize
data class InAppProduct(
    @SerializedName("id") val id: String,
    @SerializedName("app_id") val appId: String,
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("product_type") val productType: String? = null,
    @SerializedName("sku_code") val skuCode: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("billing_period") val billingPeriod: String? = null,
    @SerializedName("trial_period_days") val trialPeriodDays: Int? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_featured") val isFeatured: Boolean,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) : Parcelable {

    fun isValid(): Boolean =
        id.isNotEmpty() &&
                appId.isNotEmpty() &&
                productId?.isNotEmpty() == true &&
                productType?.isNotEmpty() == true &&
                skuCode?.isNotEmpty() == true &&
                title?.isNotEmpty() == true &&
                description?.isNotEmpty() == true &&
                price != null &&
                currency?.isNotEmpty() == true &&
                billingPeriod?.isNotEmpty() == true
}
