package com.agcforge.videodownloader.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.helper.ads.BillingManager
import com.agcforge.videodownloader.helper.ads.CurrentActivityTracker
import com.agcforge.videodownloader.helper.ads.InterstitialHelper
import com.agcforge.videodownloader.helper.ads.NativeAdsHelper
import com.agcforge.videodownloader.helper.ads.RewardAdsHelper
import com.agcforge.videodownloader.ui.component.PremiumFeatureDialog
import com.agcforge.videodownloader.utils.CrashReporter
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.applyTheme
import com.agcforge.videodownloader.utils.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    @SuppressLint("SuspiciousIndentation")
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
                showPremiumFeatureDialog()
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

    @SuppressLint("SuspiciousIndentation")
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

    private fun showPremiumFeatureDialog() {
        lifecycleScope.launch {
            val isDialogOpened = preferenceManager.isOpenPremiumFeatureDialog.first()
            val isPremium = BillingManager.isPremium.first()

            if (!isPremium && !isDialogOpened) {
                PremiumFeatureDialog.create(this@BaseActivity)
                    .setAnimation(R.raw.premium_icon_animation, autoPlay = true, loop = true)
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.subscription_cta_buy)) {
                        startActivity(Intent(this@BaseActivity, SubscriptionActivity::class.java))
                    }
                    .setNegativeButton(getString(R.string.close))
                    .show()
                preferenceManager.setIsOpenPremiumFeatureDialog(true)
            }
        }
    }

    fun sendErrorReport(
        throwable: Throwable,
        tag: String = "non_fatal",
        message: String? = null) {

        MaterialAlertDialogBuilder(this,R.style.AlertDialogTheme)
            .setTitle(getString(R.string.send_report_to_us))
            .setPositiveButton(getString(R.string.send_report_to_us)) { _, _ ->
                CrashReporter.reportNonFatal(this, throwable, tag, message)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }
}
