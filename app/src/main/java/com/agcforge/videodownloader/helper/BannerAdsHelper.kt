package com.agcforge.videodownloader.helper

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.agcforge.videodownloader.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

class BannerAdsHelper(private val activity: Activity) {

    private val TAG = "BannerAdsHelper"

    private var admobBannerView: AdView? = null
    private var unityBannerView: BannerView? = null
    private var startIoBanner: Banner? = null

    private var admobContainer: View? = null
    private var unityContainer: FrameLayout? = null
    private var startIoContainer: FrameLayout? = null

    // Loading states
    private var isAdmobLoading = false
    private var isUnityLoading = false
    private var isStartIoLoading = false

    // Current provider
    private var currentProvider: AdsConfig.AdsProvider? = null

    fun inflateBannerView(parent: ViewGroup): View {
        return LayoutInflater.from(activity)
            .inflate(R.layout.item_banner_ad, parent, false)
    }

    fun loadAndAttachBanner(
        container: ViewGroup,
        onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onBannerFailed: ((AdsConfig.AdsProvider) -> Unit)? = null
    ) {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            container.visibility = View.GONE
            return
        }

		container.removeAllViews()
		val bannerView = inflateBannerView(container)
		container.addView(bannerView)

        // Get references
        admobContainer = bannerView.findViewById(R.id.admobBannerView)
        unityContainer = bannerView.findViewById(R.id.unityBannerContainer)
        startIoContainer = bannerView.findViewById(R.id.startIoBannerContainer)

		admobBannerView = admobContainer as? AdView
		AdsConfig.admobConfig.bannerId?.let { id ->
			admobBannerView?.adUnitId = id
		}

		loadBanner(onBannerLoaded, onBannerFailed)
    }

    fun loadBanner(
        onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)? = null,
        onBannerFailed: ((AdsConfig.AdsProvider) -> Unit)? = null
    ) {
        if (!AdsConfig.ENABLE_ADS) {
            Log.d(TAG, "Ads disabled")
            return
        }

		val enabled = AdsConfig.Rotation.BANNER_PRIORITY.filter {
			when (it) {
				AdsConfig.AdsProvider.ADMOB -> AdsConfig.admobConfig.isBannerEnabled()
				AdsConfig.AdsProvider.UNITY -> AdsConfig.unityConfig.isBannerEnabled()
				AdsConfig.AdsProvider.STARTIO -> AdsConfig.startIoConfig.isStartIoEnabled()
			}
		}
		val eligible = AdsCooldownManager.filterEligible(AdsCooldownManager.AdType.BANNER, enabled)
		val order = AdsConfig.Rotation.nextBannerOrder(eligible)
		loadFromOrder(order, 0, onBannerLoaded, onBannerFailed)
    }

	private fun loadFromOrder(
		order: List<AdsConfig.AdsProvider>,
		index: Int,
		onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)?,
		onBannerFailed: ((AdsConfig.AdsProvider) -> Unit)?
	) {
		if (index >= order.size) {
			Log.e(TAG, "All banner providers failed")
			hideBanner()
			onBannerFailed?.invoke(order.lastOrNull() ?: AdsConfig.AdsProvider.ADMOB)
			return
		}
		when (order[index]) {
			AdsConfig.AdsProvider.ADMOB -> {
				if (admobBannerView != null && !isAdmobLoading && AdsConfig.admobConfig.isBannerEnabled()) {
					loadAdmobBanner(onBannerLoaded) { loadFromOrder(order, index + 1, onBannerLoaded, onBannerFailed) }
					return
				}
			}
			AdsConfig.AdsProvider.UNITY -> {
				if (unityContainer != null && !isUnityLoading && AdsConfig.unityConfig.isBannerEnabled()) {
					loadUnityBanner(onBannerLoaded) { loadFromOrder(order, index + 1, onBannerLoaded, onBannerFailed) }
					return
				}
			}
			AdsConfig.AdsProvider.STARTIO -> {
				if (startIoContainer != null && !isStartIoLoading && AdsConfig.startIoConfig.isStartIoEnabled()) {
					loadStartIoBanner(onBannerLoaded) { loadFromOrder(order, index + 1, onBannerLoaded, onBannerFailed) }
					return
				}
			}
		}
		loadFromOrder(order, index + 1, onBannerLoaded, onBannerFailed)
	}


	private fun loadAdmobBanner(
		onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)?,
		onLoadFailedContinue: () -> Unit
	) {
        admobBannerView?.apply {
            isAdmobLoading = true

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Admob banner loaded")
					AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.ADMOB)
                    isAdmobLoading = false
                    currentProvider = AdsConfig.AdsProvider.ADMOB
                    showBanner(AdsConfig.AdsProvider.ADMOB)
                    onBannerLoaded?.invoke(AdsConfig.AdsProvider.ADMOB)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Admob banner failed: ${error.message}")
					AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.ADMOB)
                    isAdmobLoading = false
					onLoadFailedContinue()
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Admob banner clicked")
                }
            }

            loadAd(AdRequest.Builder().build())
        }
    }

	private fun loadUnityBanner(
		onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)?,
		onLoadFailedContinue: () -> Unit
	) {
        unityContainer?.apply {
            isUnityLoading = true
            removeAllViews()

            unityBannerView = BannerView(
                activity,
                AdsConfig.unityConfig.bannerPlacement,
                UnityBannerSize(320, 50)
            ).apply {
                listener = object : BannerView.IListener {
                    override fun onBannerLoaded(bannerView: BannerView?) {
                        Log.d(TAG, "Unity banner loaded")
						AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.UNITY)
                        isUnityLoading = false
                        currentProvider = AdsConfig.AdsProvider.UNITY
                        showBanner(AdsConfig.AdsProvider.UNITY)
                        onBannerLoaded?.invoke(AdsConfig.AdsProvider.UNITY)
                    }

                    override fun onBannerShown(bannerAdView: BannerView?) {}

                    override fun onBannerFailedToLoad(
                        bannerView: BannerView?,
                        errorInfo: BannerErrorInfo?
                    ) {
                        Log.e(TAG, "Unity banner failed: ${errorInfo?.errorMessage}")
						AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.UNITY)
                        isUnityLoading = false
					onLoadFailedContinue()
                    }

                    override fun onBannerClick(bannerView: BannerView?) {
                        Log.d(TAG, "Unity banner clicked")
                    }

                    override fun onBannerLeftApplication(bannerView: BannerView?) {
                        Log.d(TAG, "Unity banner left application")
                    }
                }
                load()
            }

            addView(unityBannerView)
        }
    }

	private fun loadStartIoBanner(
		onBannerLoaded: ((AdsConfig.AdsProvider) -> Unit)?,
		onLoadFailedContinue: () -> Unit
	) {
        startIoContainer?.apply {
            isStartIoLoading = true
            removeAllViews()

            startIoBanner = Banner(activity, object : BannerListener {
                override fun onReceiveAd(view: View) {
                    Log.d(TAG, "Start.io banner loaded")
					AdsCooldownManager.recordSuccess(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.STARTIO)
                    isStartIoLoading = false
                    currentProvider = AdsConfig.AdsProvider.STARTIO
                    showBanner(AdsConfig.AdsProvider.STARTIO)
                    onBannerLoaded?.invoke(AdsConfig.AdsProvider.STARTIO)
                }

                override fun onFailedToReceiveAd(view: View?) {
                    Log.e(TAG, "Start.io banner failed to load.")
					AdsCooldownManager.recordFailure(AdsCooldownManager.AdType.BANNER, AdsConfig.AdsProvider.STARTIO)
                    isStartIoLoading = false
					onLoadFailedContinue()
                }

                override fun onImpression(view: View) {}
                override fun onClick(view: View) {}
            })

            addView(startIoBanner)
        }
    }

    private fun showBanner(provider: AdsConfig.AdsProvider) {
        admobContainer?.visibility = View.GONE
        unityContainer?.visibility = View.GONE
        startIoContainer?.visibility = View.GONE

        when (provider) {
            AdsConfig.AdsProvider.ADMOB -> admobContainer?.visibility = View.VISIBLE
            AdsConfig.AdsProvider.UNITY -> unityContainer?.visibility = View.VISIBLE
            AdsConfig.AdsProvider.STARTIO -> startIoContainer?.visibility = View.VISIBLE
        }
    }


    fun hideBanner() {
        admobContainer?.visibility = View.GONE
        unityContainer?.visibility = View.GONE
        startIoContainer?.visibility = View.GONE
    }

    fun showCurrentBanner() {
        currentProvider?.let { showBanner(it) }
    }

    fun destroy() {
        admobBannerView?.destroy()
        unityBannerView?.destroy()
        startIoBanner = null

        admobBannerView = null
        unityBannerView = null
    }

    fun pause() {
        admobBannerView?.pause()
    }

    fun resume() {
        admobBannerView?.resume()
    }
}
