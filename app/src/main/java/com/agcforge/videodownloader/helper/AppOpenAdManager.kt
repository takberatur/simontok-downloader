package com.agcforge.videodownloader.helper

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenAdManager {
    private const val TAG = "AppOpenAdManager"
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0

    private var appBackgroundTimestamp: Long = 0
    private const val MIN_BACKGROUND_TIME_MS = 30_000

    fun onAppEnteredBackground() {
        appBackgroundTimestamp = System.currentTimeMillis()
        Log.d(TAG, "App entered background at: $appBackgroundTimestamp")
    }

    private fun wasInBackgroundLongEnough(): Boolean {
        val timeInBackground = System.currentTimeMillis() - appBackgroundTimestamp
        return timeInBackground >= MIN_BACKGROUND_TIME_MS
    }

    fun loadAd(context: Context) {
		if (!AdsConfig.ENABLE_ADS || !AdsConfig.admobConfig.isAdmobEnabled()) return
		if (isLoadingAd || isAdAvailable()) return

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()

		val adUnitId = AdsConfig.admobConfig.adUnitId ?: return

        AppOpenAd.load(
            context,
            adUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    Log.d(TAG, "App Open Ad Loaded")
                }

                override fun onAdFailedToLoad(loadError: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "App Open Ad Failed to Load: ${loadError.message}")
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - loadTime) < 3600000 * 4
    }

    fun showAdIfAvailable(activity: Activity) {
        if (activity.javaClass.simpleName == "SplashActivity") return

        if (isAdAvailable() && wasInBackgroundLongEnough()) {
            Log.d(TAG, "Showing App Open Ad...")

            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    loadAd(activity) // Preload lagi
                }
            }
            appOpenAd?.show(activity)

            appBackgroundTimestamp = System.currentTimeMillis()
        } else {
            Log.d(TAG, "Ad not shown: Interval not reached or Ad not available")
            loadAd(activity)
        }
    }
}
