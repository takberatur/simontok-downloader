package com.agcforge.videodownloader.helper

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.startapp.sdk.adsbase.model.AdPreferences
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

class RewardAdsHelper(private val activity: Activity) {

    private val TAG = "RewardAdsHelper"

    private var admobRewardedAd: RewardedAd? = null
    private var isAdmobLoading = false

    private var isUnityLoading = false
    private var isUnityLoaded = false

    private var isStartIoLoading = false
    private var isStartIoLoaded = false

    private var startAppAd: StartAppAd? = null

    init {
        startAppAd = StartAppAd(activity)
    }

    fun loadAd() {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled in config")
            return
        }

		if (AdsConfig.admobConfig.isRewardedEnabled()) {
			loadAdmobReward()
		}
		if (AdsConfig.unityConfig.isRewardedEnabled()) {
			loadUnityReward()
		}
		if (AdsConfig.startIoConfig.isStartIoEnabled()) {
			loadStartIoReward()
		}
    }

    fun showAd(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onRewarded: ((AdsConfig.AdsProvider, Int) -> Unit)? = null,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onAdFailed: ((AdsConfig.AdsProvider) -> Unit)? = null
    ) {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            onAdFailed?.invoke(AdsConfig.AdsProvider.ADMOB)
            return
        }

		val enabled = enabledProviders()
		val order = AdsConfig.Rotation.nextRewardOrder(enabled)
		showFromOrder(order, 0, onAdShown, onRewarded, onAdClosed, onAdFailed)
    }

	private fun enabledProviders(): List<AdsConfig.AdsProvider> {
		val enabled = AdsConfig.Rotation.REWARD_PRIORITY.filter {
			when (it) {
				AdsConfig.AdsProvider.ADMOB -> AdsConfig.admobConfig.isRewardedEnabled()
				AdsConfig.AdsProvider.UNITY -> AdsConfig.unityConfig.isRewardedEnabled()
				AdsConfig.AdsProvider.STARTIO -> AdsConfig.startIoConfig.isStartIoEnabled()
			}
		}
		return AdsCooldownManager.filterEligible(AdsCooldownManager.AdType.REWARD, enabled)
	}

	private fun showFromOrder(
		order: List<AdsConfig.AdsProvider>,
		index: Int,
		onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
		onRewarded: ((AdsConfig.AdsProvider, Int) -> Unit)?,
		onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onAdFailed: ((AdsConfig.AdsProvider) -> Unit)?
	) {
		if (index >= order.size) {
			Log.d(TAG, "No reward ads available")
			onAdFailed?.invoke(order.lastOrNull() ?: AdsConfig.AdsProvider.ADMOB)
			return
		}
		when (order[index]) {
			AdsConfig.AdsProvider.ADMOB -> {
				if (admobRewardedAd != null) {
					showAdmobReward(
						onAdShown,
						onRewarded,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onRewarded, onAdClosed, onAdFailed) }
					)
					return
				}
			}
			AdsConfig.AdsProvider.UNITY -> {
				if (isUnityLoaded) {
					showUnityReward(
						onAdShown,
						onRewarded,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onRewarded, onAdClosed, onAdFailed) }
					)
					return
				}
			}
			AdsConfig.AdsProvider.STARTIO -> {
				if (isStartIoLoaded) {
					showStartIoReward(
						onAdShown,
						onRewarded,
						onAdClosed,
						{ showFromOrder(order, index + 1, onAdShown, onRewarded, onAdClosed, onAdFailed) }
					)
					return
				}
			}
		}
		showFromOrder(order, index + 1, onAdShown, onRewarded, onAdClosed, onAdFailed)
	}

    private fun loadAdmobReward() {
		if (!AdsConfig.admobConfig.isRewardedEnabled() || isAdmobLoading) return

        isAdmobLoading = true

        val adRequest = AdRequest.Builder().build()

        AdsConfig.admobConfig.rewardedId?.let {
            RewardedAd.load(
                activity,
                it,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        Log.d(TAG, "Admob reward loaded")
                        admobRewardedAd = rewardedAd
                        isAdmobLoading = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e(TAG, "Admob reward failed: ${loadAdError.message}")
                        admobRewardedAd = null
                        isAdmobLoading = false
                    }
                }
            )
        }
    }

	private fun showAdmobReward(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
        onRewarded: ((AdsConfig.AdsProvider, Int) -> Unit)?,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
    ) {
        admobRewardedAd?.apply {
            fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Admob reward shown")
					AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.ADMOB)
                    onAdShown?.invoke(AdsConfig.AdsProvider.ADMOB)
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Admob reward dismissed")
                    admobRewardedAd = null
                    loadAdmobReward() // Reload
                    onAdClosed?.invoke(AdsConfig.AdsProvider.ADMOB)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Admob reward show failed: ${adError.message}")
					AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.ADMOB)
                    admobRewardedAd = null
                    loadAdmobReward()
					onShowFailedContinue()
                }
            }

            show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewarded?.invoke(AdsConfig.AdsProvider.ADMOB, rewardItem.amount)
            }
        } ?: run {
			AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.ADMOB)
			onShowFailedContinue()
        }
    }

    private fun loadUnityReward() {
		if (!AdsConfig.unityConfig.isRewardedEnabled() || isUnityLoading) return

        isUnityLoading = true

        AdsConfig.unityConfig.rewardPlacement?.let {
            UnityAds.load(
                it,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        Log.d(TAG, "Unity reward loaded")
                        isUnityLoaded = true
                        isUnityLoading = false
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        Log.e(TAG, "Unity reward failed: $message")
                        isUnityLoaded = false
                        isUnityLoading = false
                    }
                }
            )
        }
    }

    private fun showUnityReward(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
        onRewarded: ((AdsConfig.AdsProvider, Int) -> Unit)?,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
    ) {
		val placementId = AdsConfig.unityConfig.rewardPlacement
		if (placementId.isNullOrBlank()) {
			AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.UNITY)
			onShowFailedContinue()
			return
		}
		placementId.let { id ->
            UnityAds.show(
                activity,
				id,
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.d(TAG, "Unity reward started")
						AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.UNITY)
                        onAdShown?.invoke(AdsConfig.AdsProvider.UNITY)
                    }


                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.d(TAG, "Unity reward completed: $state")

                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            // User watched the full video
                            onRewarded?.invoke(AdsConfig.AdsProvider.UNITY, 1)
                        }

                        isUnityLoaded = false
                        loadUnityReward() // Reload
                        onAdClosed?.invoke(AdsConfig.AdsProvider.UNITY)
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.e(TAG, "Unity reward show failed: $message")
						AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.UNITY)
                        isUnityLoaded = false
                        loadUnityReward()
						onShowFailedContinue()
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.d(TAG, "Unity reward clicked")
                    }
                }
            )
        }
    }

    private fun loadStartIoReward() {
		if (!AdsConfig.startIoConfig.isStartIoEnabled() || isStartIoLoading) return

        isStartIoLoading = true

        // Start.io uses video ads for reward
        val adPreferences = AdPreferences()

        startAppAd?.loadAd(
            StartAppAd.AdMode.REWARDED_VIDEO,
            object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    Log.d(TAG, "Start.io reward loaded")
                    isStartIoLoaded = true
                    isStartIoLoading = false
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.e(TAG, "Start.io reward failed")
                    isStartIoLoaded = false
                    isStartIoLoading = false
                }
            }
        )
    }

    private fun showStartIoReward(
        onAdShown: ((AdsConfig.AdsProvider) -> Unit)?,
        onRewarded: ((AdsConfig.AdsProvider, Int) -> Unit)?,
        onAdClosed: ((AdsConfig.AdsProvider) -> Unit)?,
		onShowFailedContinue: () -> Unit
    ) {
		val ad = startAppAd
		if (ad == null) {
			AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.STARTIO)
			onShowFailedContinue()
			return
		}

		ad.setVideoListener(object : VideoListener {
            override fun onVideoCompleted() {
                Log.d(TAG, "Start.io video completed - user rewarded")
                onRewarded?.invoke(AdsConfig.AdsProvider.STARTIO, 1)
            }
        })

		ad.showAd(object : AdDisplayListener {
            override fun adHidden(ad: Ad) {
                Log.d(TAG, "Start.io reward hidden")
                isStartIoLoaded = false
                loadStartIoReward() // Reload
                onAdClosed?.invoke(AdsConfig.AdsProvider.STARTIO)
            }

            override fun adDisplayed(ad: Ad) {
                Log.d(TAG, "Start.io reward displayed")
				AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.STARTIO)
                onAdShown?.invoke(AdsConfig.AdsProvider.STARTIO)
            }

            override fun adClicked(ad: Ad) {
                Log.d(TAG, "Start.io reward clicked")
            }

            override fun adNotDisplayed(ad: Ad) {
                Log.e(TAG, "Start.io reward not displayed")
				AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.REWARD, AdsConfig.AdsProvider.STARTIO)
                isStartIoLoaded = false
                loadStartIoReward()
				onShowFailedContinue()
            }
        })
    }


    fun isAdReady(): Boolean {
        return admobRewardedAd != null || isUnityLoaded || isStartIoLoaded
    }


    fun destroy() {
        admobRewardedAd = null
    }
}
