package com.agcforge.videodownloader.ui.activities

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.helper.BannerAdsHelper
import com.agcforge.videodownloader.helper.CurrentActivityTracker
import com.agcforge.videodownloader.helper.InterstitialHelper
import com.agcforge.videodownloader.helper.NativeAdsHelper
import com.agcforge.videodownloader.helper.RewardAdsHelper
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.applyTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager
	private var appliedLanguageCode: String? = null

    private lateinit var interstitialHelper: InterstitialHelper
    private lateinit var rewardAdsHelper: RewardAdsHelper
    private lateinit var bannerAdsHelper: BannerAdsHelper
    private lateinit var nativeAdsHelper: NativeAdsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceManager = PreferenceManager(this)
        observeTheme()
        observeLanguage()
        super.onCreate(savedInstanceState)

        initializeAdsHelpers()
        loadAds()
    }

    private fun initializeAdsHelpers() {
        interstitialHelper = InterstitialHelper(this)
        rewardAdsHelper = RewardAdsHelper(this)
        bannerAdsHelper = BannerAdsHelper(this)
        nativeAdsHelper = NativeAdsHelper(this)
    }

    private fun loadAds() {
        interstitialHelper.loadAd()
        rewardAdsHelper.loadAd()
    }

    private fun observeTheme() {
        lifecycleScope.launch {
            preferenceManager.theme.first().let { theme ->
                applyTheme(theme)
            }
        }
    }
    private fun observeLanguage() {
        lifecycleScope.launch {
			preferenceManager.language
				.distinctUntilChanged()
				.collect { languageCode ->
					if (appliedLanguageCode == null) {
						appliedLanguageCode = languageCode
						return@collect
					}
					if (appliedLanguageCode != languageCode) {
						appliedLanguageCode = languageCode
						recreate()
					}
				}
        }
    }

    override fun attachBaseContext(newBase: Context) {
        preferenceManager = PreferenceManager(newBase)
        val languageCode = runBlocking { preferenceManager.language.first() }
		appliedLanguageCode = languageCode
        val context = updateBaseContextLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }

    private fun updateBaseContextLocale(context: Context, languageCode: String?): Context {
        val locale = if (!languageCode.isNullOrEmpty()) {
            Locale(languageCode)
        } else {
            // If no language is saved, use the system default
            Locale.getDefault()
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun restartActivity() {
		recreate()
    }
    fun showInterstitial(onDismiss: () -> Unit) {
        interstitialHelper.showAd(
            onAdShown = { provider ->
                Log.d("BaseActivity", "Ad Shown from $provider")
            },
            onAdClosed = { provider ->
                onDismiss.invoke()
            },
            onAdFailed = { provider ->
                onDismiss.invoke()
            }
        )
    }

    fun showRewardAd(onRewardEarned: (Boolean) -> Unit) {
		var rewarded = false
		rewardAdsHelper.showAd(
			onAdShown = { provider ->
				Log.d("BaseActivity", "Ad Shown from $provider")
			},
			onRewarded = { provider, _ ->
				rewarded = true
				Log.d("BaseActivity", "Reward earned from $provider")
			},
			onAdClosed = { provider ->
				onRewardEarned.invoke(rewarded)
				Log.d("BaseActivity", "Ad Closed from $provider")
			},
			onAdFailed = { provider ->
				onRewardEarned.invoke(false)
				Log.d("BaseActivity", "Ad Failed from $provider")
			}
		)
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialHelper.destroy()
        rewardAdsHelper.destroy()
        bannerAdsHelper.destroy()
        nativeAdsHelper.destroy()
    }

    override fun onResume() {
        super.onResume()
		CurrentActivityTracker.set(this)
    }

	override fun onPause() {
		if (CurrentActivityTracker.get() === this) {
			CurrentActivityTracker.set(null)
		}
		super.onPause()
	}
}
