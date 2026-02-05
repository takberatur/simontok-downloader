package com.agcforge.videodownloader.helper

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.agcforge.videodownloader.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

class NativeAdsHelper(private val activity: Activity) {

    private val TAG = "NativeAdsHelper"

    private var nativeAd: NativeAd? = null
    private var isLoading = false

    enum class NativeAdSize {
        SMALL,
        MEDIUM
    }

    fun loadAndAttachNativeAd(
        container: ViewGroup,
        adSize: NativeAdSize = NativeAdSize.SMALL,
        onAdLoaded: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onAdFailed: ((AdsConfig.AdsProvider) -> Unit)? = null
    ) {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            container.visibility = View.GONE
            return
        }

        loadNativeAd(
            onSuccess = { nativeAd ->
                val adView = populateNativeAdView(nativeAd, adSize)
                container.removeAllViews()
                container.addView(adView)
                container.visibility = View.VISIBLE
                onAdLoaded?.invoke(AdsConfig.AdsProvider.ADMOB)
            },
            onFailure = {
                container.visibility = View.GONE
                onAdFailed?.invoke(AdsConfig.AdsProvider.ADMOB)
            }
        )
    }

    fun loadNativeAd(
        onSuccess: ((NativeAd) -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        if (isLoading) {
            Log.d(TAG, "Already loading native ad")
            return
        }

        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            onFailure?.invoke()
            return
        }

		val nativeId = AdsConfig.admobConfig.nativeId
		if (!AdsConfig.admobConfig.isNativeEnabled() || nativeId.isNullOrBlank()) {
			isLoading = false
			onFailure?.invoke()
			return
		}

		isLoading = true

		val adLoader = AdLoader.Builder(activity, nativeId)
			.forNativeAd { ad ->
                Log.d(TAG, "Native ad loaded")

                // If current ad exists, destroy it
                nativeAd?.destroy()
                nativeAd = ad

                isLoading = false
                onSuccess?.invoke(ad)
            }
			.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Native ad failed: ${error.message}")
                    isLoading = false
                    onFailure?.invoke()
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Native ad clicked")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Native ad impression")
                }
            })
				.build()

		adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adSize: NativeAdSize): NativeAdView {
        val layoutId = when (adSize) {
            NativeAdSize.SMALL -> R.layout.item_native_ad_small
            NativeAdSize.MEDIUM -> R.layout.item_native_ad_medium
        }

        val adView = LayoutInflater.from(activity)
            .inflate(layoutId, null) as NativeAdView

        when (adSize) {
            NativeAdSize.SMALL -> populateSmallNativeAd(adView, nativeAd)
            NativeAdSize.MEDIUM -> populateMediumNativeAd(adView, nativeAd)
        }

        return adView
    }

    private fun populateSmallNativeAd(adView: NativeAdView, nativeAd: NativeAd) {
        // Set the media view (icon)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        // Set the headline
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.text = nativeAd.headline

        // Set the body
        adView.bodyView = adView.findViewById(R.id.ad_body)
        nativeAd.body?.let {
            (adView.bodyView as? TextView)?.text = it
        } ?: run {
            adView.bodyView?.visibility = View.GONE
        }

        // Set the call to action
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        nativeAd.callToAction?.let {
            (adView.callToActionView as? Button)?.text = it
        } ?: run {
            adView.callToActionView?.visibility = View.INVISIBLE
        }

        // Set the icon
        nativeAd.icon?.let {
            (adView.iconView as? ImageView)?.setImageDrawable(it.drawable)
        }

        // Register the native ad
        adView.setNativeAd(nativeAd)
    }

    private fun populateMediumNativeAd(adView: NativeAdView, nativeAd: NativeAd) {
        // Set the media view
        adView.mediaView = adView.findViewById(R.id.ad_media)
        nativeAd.mediaContent?.let {
            adView.mediaView?.setMediaContent(it)
        }

        // Set the app icon
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        nativeAd.icon?.let {
            (adView.iconView as? ImageView)?.setImageDrawable(it.drawable)
        } ?: run {
            adView.iconView?.visibility = View.GONE
        }

        // Set the headline
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.text = nativeAd.headline

        // Set the advertiser
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
        nativeAd.advertiser?.let {
            (adView.advertiserView as? TextView)?.text = it
            adView.advertiserView?.visibility = View.VISIBLE
        } ?: run {
            adView.advertiserView?.visibility = View.GONE
        }

        // Set the star rating
        adView.starRatingView = adView.findViewById(R.id.ad_stars)
        nativeAd.starRating?.let {
            adView.starRatingView?.visibility = View.VISIBLE
        } ?: run {
            adView.starRatingView?.visibility = View.GONE
        }

        // Set the body
        adView.bodyView = adView.findViewById(R.id.ad_body)
        nativeAd.body?.let {
            (adView.bodyView as? TextView)?.text = it
        } ?: run {
            adView.bodyView?.visibility = View.GONE
        }

        // Set the call to action
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        nativeAd.callToAction?.let {
            (adView.callToActionView as? Button)?.text = it
        } ?: run {
            adView.callToActionView?.visibility = View.INVISIBLE
        }

        // Register the native ad
        adView.setNativeAd(nativeAd)
    }

    fun preloadNativeAd(onComplete: ((Boolean) -> Unit)? = null) {
        loadNativeAd(
            onSuccess = {
                Log.d(TAG, "Native ad preloaded successfully")
                onComplete?.invoke(true)
            },
            onFailure = {
                Log.e(TAG, "Failed to preload native ad")
                onComplete?.invoke(false)
            }
        )
    }

    fun getPreloadedNativeAdView(adSize: NativeAdSize = NativeAdSize.SMALL): NativeAdView? {
        return nativeAd?.let { populateNativeAdView(it, adSize) }
    }

    fun isAdReady(): Boolean {
        return nativeAd != null
    }

    fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
    }
}
