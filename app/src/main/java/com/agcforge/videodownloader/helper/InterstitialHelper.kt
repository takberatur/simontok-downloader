package com.agcforge.videodownloader.helper

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import kotlinx.coroutines.flow.first

class InterstitialHelper (private val activity: Activity) {
    private val TAG = "InterstitialHelper"


    private var admobInterstitial: InterstitialAd? = null
    private var isAdmobLoading = false
    private var isUnityLoading = false
    private var isUnityLoaded = false
    private var startAppAd: StartAppAd? = null
    private var isStartIoLoading = false
    private var isStartIoLoaded = false

    private var lastShownTime = 0L

    init {
        startAppAd = StartAppAd(activity)
    }

    fun loadAd() {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled in config")
            return
        }

		if (AdsConfig.admobConfig.isInterstitialEnabled()) {
			loadAdmobInterstitial()
		}
		if (AdsConfig.unityConfig.isInterstitialEnabled()) {
			loadUnityInterstitial()
		}
		if (AdsConfig.startIoConfig.isStartIoEnabled()) {
			loadStartIoInterstitial()
		}
    }

    fun showAd(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onAdFailed: ((AdsConfig.AdsProvider) -> Unit)? = null
    ) {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            onAdFailed?.invoke(AdsConfig.AdsProvider.ADMOB)
            return
        }

        // Check interval
        if (!canShowAd()) {
            Log.d(TAG, "Interval not reached yet")
            onAdFailed?.invoke(AdsConfig.AdsProvider.ADMOB)
            return
        }

		val enabled = enabledProviders()
		val order = AdsConfig.Rotation.nextInterstitialOrder(enabled)
		showFromOrder(order, 0, onAdShown, onAdClosed, onAdFailed)
    }

	private fun enabledProviders(): List<AdsConfig.AdsProvider> {
		val enabled = AdsConfig.Rotation.INTERSTITIAL_PRIORITY.filter {
			when (it) {
				AdsConfig.AdsProvider.ADMOB -> AdsConfig.admobConfig.isInterstitialEnabled()
				AdsConfig.AdsProvider.UNITY -> AdsConfig.unityConfig.isInterstitialEnabled()
				AdsConfig.AdsProvider.STARTIO -> AdsConfig.startIoConfig.isStartIoEnabled()
			}
		}
		return AdsCooldownManager.filterEligible(AdsCooldownManager.AdType.INTERSTITIAL, enabled)
	}

	private fun showFromOrder(
		order: List<AdsConfig.AdsProvider>,
		index: Int,
		onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
		onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onAdFailed: ((AdsConfig.AdsProvider) -> Unit)?
	) {
		if (index >= order.size) {
			Log.d(TAG, "No ads available to show")
			onAdFailed?.invoke(order.lastOrNull() ?: AdsConfig.AdsProvider.ADMOB)
			return
		}
		when (val provider = order[index]) {
			AdsConfig.AdsProvider.ADMOB -> {
				if (admobInterstitial != null) {
					showAdmobInterstitial(
						onAdShown,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onAdClosed, onAdFailed) }
					)
					return
				}
			}
			AdsConfig.AdsProvider.UNITY -> {
				if (isUnityLoaded) {
					showUnityInterstitial(
						onAdShown,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onAdClosed, onAdFailed) }
					)
					return
				}
			}
			AdsConfig.AdsProvider.STARTIO -> {
				if (isStartIoLoaded) {
					showStartIoInterstitial(
						onAdShown,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onAdClosed, onAdFailed) }
					)
					return
				}
			}
		}
		showFromOrder(order, index + 1, onAdShown, onAdClosed, onAdFailed)
	}

    private fun canShowAd(): Boolean {
        val currentTime = System.currentTimeMillis()
        val intervalMillis = AdsConfig.INTERSTITIAL_INTERVAL_SECONDS * 1000L

        if (lastShownTime == 0L) return true

        return (currentTime - lastShownTime) >= intervalMillis
    }

    private fun loadAdmobInterstitial() {
		if (!AdsConfig.admobConfig.isInterstitialEnabled() || isAdmobLoading) return

        isAdmobLoading = true

        val adRequest = AdRequest.Builder().build()

		AdsConfig.admobConfig.interstitialId?.let {
            InterstitialAd.load(
                activity,
                it,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        Log.d(TAG, "Admob interstitial loaded")
                        admobInterstitial = interstitialAd
                        isAdmobLoading = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e(TAG, "Admob interstitial failed: ${loadAdError.message}")
                        admobInterstitial = null
                        isAdmobLoading = false
                    }
                }
            )
        }
    }

	private fun showAdmobInterstitial(
		onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
		onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
	) {
        admobInterstitial?.apply {
            fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Admob interstitial shown")
					AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.ADMOB)
                    lastShownTime = System.currentTimeMillis()
                    onAdShown?.invoke(AdsConfig.AdsProvider.ADMOB)
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Admob interstitial dismissed")
                    admobInterstitial = null
                    loadAdmobInterstitial() // Reload
                    onAdClosed?.invoke(AdsConfig.AdsProvider.ADMOB)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Admob show failed: ${adError.message}")
					AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.ADMOB)
                    admobInterstitial = null
                    loadAdmobInterstitial()
					onShowFailedContinue()
                }
            }

            show(activity)
		} ?: run {
			AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.ADMOB)
			onShowFailedContinue()
		}
    }

    private fun loadUnityInterstitial() {
		if (!AdsConfig.unityConfig.isInterstitialEnabled() || isUnityLoading) return
        AdsConfig.unityConfig.interstitialPlacement?.let {
            isUnityLoading = true

            UnityAds.load(
                AdsConfig.unityConfig.interstitialPlacement,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        Log.d(TAG, "Unity interstitial loaded")
                        isUnityLoaded = true
                        isUnityLoading = false
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        Log.e(TAG, "Unity interstitial failed: $message")
                        isUnityLoaded = false
                        isUnityLoading = false
                    }
                }
            )
        }
    }

    private fun showUnityInterstitial(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
    ) {
        UnityAds.show(
            activity,
            AdsConfig.unityConfig.interstitialPlacement,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Unity interstitial started")
					AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.UNITY)
                    lastShownTime = System.currentTimeMillis()
                    onAdShown?.invoke(AdsConfig.AdsProvider.UNITY)
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    Log.d(TAG, "Unity interstitial completed")
                    isUnityLoaded = false
                    loadUnityInterstitial() // Reload
                    onAdClosed?.invoke(AdsConfig.AdsProvider.UNITY)
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    Log.e(TAG, "Unity show failed: $message")
					AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.UNITY)
                    isUnityLoaded = false
                    loadUnityInterstitial()
					onShowFailedContinue()
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Unity interstitial clicked")
                }
            }
        )
    }
    private fun loadStartIoInterstitial() {
		if (!AdsConfig.startIoConfig.isStartIoEnabled() || isStartIoLoading) return

        isStartIoLoading = true

        startAppAd?.loadAd(object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                Log.d(TAG, "Start.io interstitial loaded")
                isStartIoLoaded = true
                isStartIoLoading = false
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.e(TAG, "Start.io interstitial failed")
                isStartIoLoaded = false
                isStartIoLoading = false
            }
        })
    }

    private fun showStartIoInterstitial(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
    ) {
		val ad = startAppAd
		if (ad == null) {
			AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.STARTIO)
			onShowFailedContinue()
			return
		}
		ad.showAd(object : AdDisplayListener {
            override fun adHidden(ad: Ad) {
                Log.d(TAG, "Start.io interstitial hidden")
                isStartIoLoaded = false
                loadStartIoInterstitial() // Reload
                onAdClosed?.invoke(AdsConfig.AdsProvider.STARTIO)
            }

            override fun adDisplayed(ad: Ad) {
                Log.d(TAG, "Start.io interstitial displayed")
				AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.STARTIO)
                lastShownTime = System.currentTimeMillis()
                onAdShown?.invoke(AdsConfig.AdsProvider.STARTIO)
            }

            override fun adClicked(ad: Ad) {
                Log.d(TAG, "Start.io interstitial clicked")
            }

            override fun adNotDisplayed(ad: Ad) {
                Log.e(TAG, "Start.io not displayed")
				AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.INTERSTITIAL, AdsConfig.AdsProvider.STARTIO)
                isStartIoLoaded = false
                loadStartIoInterstitial()
				onShowFailedContinue()
            }
        })
    }


    fun isAdReady(): Boolean {
        return admobInterstitial != null || isUnityLoaded || isStartIoLoaded
    }


    fun destroy() {
        admobInterstitial = null
        startAppAd = null
    }

}
