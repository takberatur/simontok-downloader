package com.agcforge.videodownloader.ui.activities

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.databinding.ActivitySubscriptionBinding
import com.agcforge.videodownloader.helper.BillingManager
import com.agcforge.videodownloader.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubscriptionActivity : BaseActivity() {
	private lateinit var binding: ActivitySubscriptionBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySubscriptionBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.toolbar.setNavigationOnClickListener { finish() }

		binding.btnBuy1Month.setOnClickListener { buy(BillingManager.Plan.PREMIUM_1_MONTH) }
		binding.btnBuy6Month.setOnClickListener { buy(BillingManager.Plan.PREMIUM_6_MONTH) }
		binding.btnBuy1Year.setOnClickListener { buy(BillingManager.Plan.PREMIUM_1_YEAR) }
		binding.btnRestore.setOnClickListener {
			setLoading(true)
			BillingManager.refreshEntitlements()
			lifecycleScope.launch {
				delay(1200)
				setLoading(false)
			}
		}

		observeBillingState()
	}

	private fun observeBillingState() {
		lifecycleScope.launch {
			BillingManager.isReady.collect { ready ->
				binding.progressBar.visibility = if (ready) View.GONE else View.VISIBLE
				binding.btnBuy1Month.isEnabled = ready
				binding.btnBuy6Month.isEnabled = ready
				binding.btnBuy1Year.isEnabled = ready
				binding.btnRestore.isEnabled = ready
				updateButtonText()
			}
		}

		lifecycleScope.launch {
			BillingManager.isPremium.collect { isPremium ->
				binding.tvStatus.text = if (isPremium) {
					getString(R.string.subscription_status_premium)
				} else {
					getString(R.string.subscription_status_free)
				}
                binding.tvStatus.setTextColor(if (isPremium)
                    ContextCompat.getColor(this@SubscriptionActivity, R.color.text_secondary)
                else
                    ContextCompat.getColor(this@SubscriptionActivity, R.color.chartreuse))


				binding.btnBuy1Month.isEnabled = !isPremium && BillingManager.isReady.value
				binding.btnBuy6Month.isEnabled = !isPremium && BillingManager.isReady.value
				binding.btnBuy1Year.isEnabled = !isPremium && BillingManager.isReady.value
				updateButtonText()
			}
		}
	}

	private fun updateButtonText() {
		val p1 = BillingManager.getCachedFormattedPrice(BillingManager.Plan.PREMIUM_1_MONTH)
		val p6 = BillingManager.getCachedFormattedPrice(BillingManager.Plan.PREMIUM_6_MONTH)
		val p12 = BillingManager.getCachedFormattedPrice(BillingManager.Plan.PREMIUM_1_YEAR)
		binding.btnBuy1Month.text = if (p1.isNullOrBlank()) getString(R.string.subscription_cta_buy) else "${getString(R.string.subscription_cta_buy)} ($p1)"
		binding.btnBuy6Month.text = if (p6.isNullOrBlank()) getString(R.string.subscription_cta_buy) else "${getString(R.string.subscription_cta_buy)} ($p6)"
		binding.btnBuy1Year.text = if (p12.isNullOrBlank()) getString(R.string.subscription_cta_buy) else "${getString(R.string.subscription_cta_buy)} ($p12)"
	}

	private fun buy(plan: BillingManager.Plan) {
		BillingManager.launchPurchase(this, plan) { ok ->
			if (!ok) {
				BillingManager.refreshEntitlements()
			}
		}
	}

	private fun setLoading(loading: Boolean) {
		binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
	}
}
