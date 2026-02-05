package com.agcforge.videodownloader.helper

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.startapp.sdk.adsbase.StartAppSDK
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

object AdsInitializer {

    private const val TAG = "AdsInitializer"
    private var isInitialized = false


    fun initialize(application: Application) {
        if (isInitialized) return

        val admob = AdsConfig.admobConfig
        val unity = AdsConfig.unityConfig
        val startIo = AdsConfig.startIoConfig

		if (admob.isAdmobEnabled()) initializeAdmob(application)
		if (unity.isUnityEnabled()) initializeUnityAds(application, unity.gameId)
		if (startIo.isStartIoEnabled()) initializeStartIo(application, startIo.appId)

        isInitialized = true
    }

    private fun initializeAdmob(application: Application) {
        try {
            Log.d(TAG, "Initializing Admob...")

            MobileAds.initialize(application) { initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                for (adapterClass in statusMap.keys) {
                    val status = statusMap[adapterClass]
                    Log.d(TAG, "Admob Adapter: $adapterClass, Status: ${status?.description}")
                }
                Log.d(TAG, "Admob initialized successfully")
            }

            // Configure request settings
            val requestConfiguration = RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                .build()

            MobileAds.setRequestConfiguration(requestConfiguration)
			AppOpenAdManager.loadAd(application)

        } catch (e: Exception) {
            Log.e(TAG, "Admob initialization failed: ${e.message}")
        }
    }

    private fun initializeUnityAds(application: Application, gameId: String?) {
        if (gameId == null) return

        try {
            Log.d(TAG, "Initializing Unity Ads...")

            UnityAds.initialize(
                application,
                AdsConfig.unityConfig.gameId,
                AdsConfig.TEST_MODE,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        Log.d(TAG, "Unity Ads initialized successfully")
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String
                    ) {
                        Log.e(TAG, "Unity Ads initialization failed: $message")
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unity Ads initialization failed: ${e.message}")
        }
    }

    private fun initializeStartIo(application: Application, startIoAppId: String?) {
        if (startIoAppId == null) return

        try {
            Log.d(TAG, "Initializing Start.io...")

            AdsConfig.startIoConfig.appId?.let {
                StartAppSDK.init(
                    application,
                    it,
                    false
                )
            }

            // Set user consent for GDPR
             StartAppSDK.setUserConsent(
                 application,
                 "pas",
                 System.currentTimeMillis(),
                 true)

            Log.d(TAG, "Start.io initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Start.io initialization failed: ${e.message}")
        }
    }


    fun isInitialized(): Boolean {
        return isInitialized
    }
}
