package com.agcforge.videodownloader.helper

import android.content.Context
import android.util.Log
import com.agcforge.videodownloader.data.model.Application
import com.agcforge.videodownloader.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

object AdsConfig {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    var admobConfig = AdmobConfig.getDefault()
    var unityConfig = UnityConfig.getDefault()
    var startIoConfig = StartIoConfig.getDefault()

    var TEST_MODE: Boolean = true
    var ENABLE_ADS: Boolean = true
    var ONE_SIGNAL_ID: String? = null

    var INTERSTITIAL_INTERVAL_SECONDS: Int = 60

	const val COOLDOWN_FAILURE_THRESHOLD: Int = 2
	const val COOLDOWN_DURATION_MS: Long = 5 * 60 * 1000L

    private var preferenceManager: PreferenceManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isJobStarted = false

    fun init(context: Context) {
        if (isJobStarted) return
        isJobStarted = true

        Log.d("AdsConfig", "Initializing AdsConfig")
        preferenceManager = PreferenceManager(context.applicationContext)

        scope.launch {
            preferenceManager?.applicationConfig?.collect { app ->
                app?.let {
                    updateConfigs(it)
                    _isInitialized.value = true
                    Log.d("AdsConfig", "Config Updated: Admob Enabled = ${admobConfig.enable}")
                }
            }
        }
    }

    private fun updateConfigs(app: Application) {
        ENABLE_ADS = app.enableMonetization
        ONE_SIGNAL_ID = app.oneSignalId

        admobConfig = AdmobConfig(
            enable = app.enableMonetization && app.enableAdmob,
            adUnitId = app.admobAdUnitId,
            bannerId = app.admobBannerAdUnitId,
            interstitialId = app.admobInterstitialAdUnitId,
            nativeId = app.admobNativeAdUnitId,
            rewardedId = app.admobRewardedAdUnitId
        )
       unityConfig = UnityConfig(
            enable = app.enableMonetization && app.enableUnityAd,
            gameId = app.unityAdUnitId,
            interstitialPlacement = app.unityInterstitialAdUnitId,
            rewardPlacement = app.unityRewardedAdUnitId,
            bannerPlacement = app.unityBannerAdUnitId
        )
        startIoConfig = StartIoConfig(
            enable = app.enableMonetization && app.enableStartApp,
            appId = app.startAppAdUnitId
        )
    }

    object Rotation {
        val INTERSTITIAL_PRIORITY = listOf(
            AdsProvider.ADMOB,
            AdsProvider.UNITY,
            AdsProvider.STARTIO
        )

        val REWARD_PRIORITY = listOf(
            AdsProvider.ADMOB,
            AdsProvider.UNITY,
            AdsProvider.STARTIO
        )

        val BANNER_PRIORITY = listOf(
            AdsProvider.ADMOB,
            AdsProvider.UNITY,
            AdsProvider.STARTIO
        )

        val NATIVE_PRIORITY = listOf(
            AdsProvider.ADMOB
        )

		private val interstitialCursor = AtomicInteger(0)
		private val rewardCursor = AtomicInteger(0)
		private val bannerCursor = AtomicInteger(0)

		fun nextInterstitialOrder(enabledProviders: List<AdsProvider>): List<AdsProvider> {
			return rotate(enabledProviders, interstitialCursor)
		}

		fun nextRewardOrder(enabledProviders: List<AdsProvider>): List<AdsProvider> {
			return rotate(enabledProviders, rewardCursor)
		}

		fun nextBannerOrder(enabledProviders: List<AdsProvider>): List<AdsProvider> {
			return rotate(enabledProviders, bannerCursor)
		}

		private fun rotate(list: List<AdsProvider>, cursor: AtomicInteger): List<AdsProvider> {
			if (list.isEmpty()) return emptyList()
			val start = cursor.getAndIncrement().let { if (it < 0) 0 else it } % list.size
			if (start == 0) return list
			return list.drop(start) + list.take(start)
		}
    }


    enum class AdsProvider {
        ADMOB,
        UNITY,
        STARTIO
    }
}

data class AdmobConfig(
    val enable: Boolean,
    val adUnitId: String?,
    val bannerId: String?,
    val interstitialId: String?,
    val nativeId: String?,
    val rewardedId: String?
) {
    companion object {
        fun getDefault(): AdmobConfig = AdmobConfig(
            enable = false,
            adUnitId = null,
            bannerId = null,
            interstitialId = null,
            nativeId = null,
            rewardedId = null
        )
    }


    fun isAdmobEnabled(): Boolean = enable && adUnitId != null

    fun isBannerEnabled(): Boolean = isAdmobEnabled() && bannerId != null

    fun isInterstitialEnabled(): Boolean = isAdmobEnabled() && interstitialId != null

    fun isNativeEnabled(): Boolean = isAdmobEnabled() && nativeId != null

    fun isRewardedEnabled(): Boolean = isAdmobEnabled() && rewardedId != null
}

data class UnityConfig(
    val enable: Boolean,
    val gameId: String?,
    val bannerPlacement: String?,
    val interstitialPlacement: String?,
    val rewardPlacement: String?
){
    companion object {
        fun getDefault(): UnityConfig = UnityConfig(
            enable = false,
            gameId = null,
            bannerPlacement = null,
            interstitialPlacement = null,
            rewardPlacement = null
        )
    }

    fun isUnityEnabled(): Boolean = enable && gameId != null

    fun isBannerEnabled(): Boolean = isUnityEnabled() && bannerPlacement != null

    fun isInterstitialEnabled(): Boolean = isUnityEnabled() && interstitialPlacement != null

    fun isRewardedEnabled(): Boolean = isUnityEnabled() && rewardPlacement != null
}

data class StartIoConfig(
    val enable: Boolean,
    val appId: String?
) {
    companion object {
        fun getDefault(): StartIoConfig = StartIoConfig(
            enable = false,
            appId = null
        )
    }

    fun isStartIoEnabled(): Boolean = enable && appId != null
}
