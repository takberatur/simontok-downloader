package com.agcforge.videodownloader.helper.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import com.agcforge.videodownloader.utils.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.agcforge.videodownloader.data.api.ApiClient
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.data.dto.SubscriptionUpsertRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object BillingManager : PurchasesUpdatedListener {
	private const val TAG = "BillingManager"

	enum class Plan(val productId: String) {
		PREMIUM_1_MONTH("premium_1m"),
		PREMIUM_6_MONTH("premium_6m"),
		PREMIUM_1_YEAR("premium_1y")
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val initialized = AtomicBoolean(false)
	private var appContext: Context? = null
	@SuppressLint("StaticFieldLeak")
    private var preferenceManager: PreferenceManager? = null

	private var billingClient: BillingClient? = null
	private val productDetailsCache = mutableMapOf<String, ProductDetails>()
	private val repository by lazy { VideoDownloaderRepository() }

	private val _isReady = MutableStateFlow(false)
	val isReady = _isReady.asStateFlow()

	private val _isPremium = MutableStateFlow(false)
	val isPremium = _isPremium.asStateFlow()

	fun init(context: Context) {
		if (!initialized.compareAndSet(false, true)) return
		appContext = context.applicationContext
		preferenceManager = PreferenceManager(context.applicationContext)
		val pendingParams = PendingPurchasesParams.newBuilder()
			.enableOneTimeProducts()
			.build()
		billingClient = BillingClient.newBuilder(context.applicationContext)
			.enablePendingPurchases(pendingParams)
			.setListener(this)
			.build()

		startConnection()
	}

	fun isPremiumNow(): Boolean = _isPremium.value

	fun getCachedFormattedPrice(plan: Plan): String? {
		val details = productDetailsCache[plan.productId] ?: return null
		val pricingPhase = details.subscriptionOfferDetails
			?.firstOrNull()
			?.pricingPhases
			?.pricingPhaseList
			?.firstOrNull()
		return pricingPhase?.formattedPrice
	}

	fun launchPurchase(activity: Activity, plan: Plan, onResult: ((Boolean) -> Unit)? = null) {
		val client = billingClient
		if (client == null || !_isReady.value) {
			onResult?.invoke(false)
			return
		}
		val details = productDetailsCache[plan.productId]
		if (details == null) {
			queryProductDetails(listOf(plan.productId)) { ok ->
				val d = productDetailsCache[plan.productId]
				if (ok && d != null) {
					launchPurchase(activity, plan, onResult)
				} else {
					onResult?.invoke(false)
				}
			}
			return
		}

		val offerToken = details.subscriptionOfferDetails
			?.firstOrNull()
			?.offerToken

		if (offerToken.isNullOrBlank()) {
			onResult?.invoke(false)
			return
		}

		val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
			.setProductDetails(details)
			.setOfferToken(offerToken)
			.build()

		val params = BillingFlowParams.newBuilder()
			.setProductDetailsParamsList(listOf(productParams))
			.build()

		val result = client.launchBillingFlow(activity, params)
		onResult?.invoke(result.responseCode == BillingResponseCode.OK)
	}

	fun refreshEntitlements() {
		queryActivePurchases()
		syncSubscriptionFromServer()
	}

	fun syncSubscriptionFromServer() {
		val pm = preferenceManager ?: return
		scope.launch(Dispatchers.IO) {
			val token = pm.authToken.first()
			if (token.isNullOrBlank()) return@launch
			ApiClient.setAuthToken(token)
			repository.getCurrentSubscription()
				.onSuccess { sub ->
					if (sub != null) {
						val active = sub.status.equals("active", ignoreCase = true)
						pm.saveBoolean("is_premium", active)
						_isPremium.value = active
					}
				}
		}
	}

	private fun startConnection() {
		val client = billingClient ?: return
		client.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(billingResult: BillingResult) {
				if (billingResult.responseCode == BillingResponseCode.OK) {
					_isReady.value = true
					queryProductDetails(Plan.entries.map { it.productId })
					queryActivePurchases()
				} else {
					_isReady.value = false
					Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
				}
			}

			override fun onBillingServiceDisconnected() {
				_isReady.value = false
				scope.launch {
					startConnection()
				}
			}
		})
	}

	private fun queryProductDetails(productIds: List<String>, onDone: ((Boolean) -> Unit)? = null) {
		val client = billingClient
		if (client == null || !_isReady.value) {
			onDone?.invoke(false)
			return
		}

		val products = productIds.distinct().map {
			QueryProductDetailsParams.Product.newBuilder()
				.setProductId(it)
				.setProductType(ProductType.SUBS)
				.build()
		}

		val params = QueryProductDetailsParams.newBuilder()
			.setProductList(products)
			.build()

		client.queryProductDetailsAsync(
			params,
			ProductDetailsResponseListener { result, productDetailsResult: QueryProductDetailsResult ->
				val ok = result.responseCode == BillingResponseCode.OK
				if (!ok) {
					Log.w(TAG, "Query product details failed: ${result.debugMessage}")
					onDone?.invoke(false)
					return@ProductDetailsResponseListener
				}
				productDetailsCache.clear()
				for (d in productDetailsResult.productDetailsList) {
					productDetailsCache[d.productId] = d
				}
				onDone?.invoke(true)
			}
		)
	}

	private fun queryActivePurchases() {
		val client = billingClient
		if (client == null || !_isReady.value) return

		val params = QueryPurchasesParams.newBuilder()
			.setProductType(ProductType.SUBS)
			.build()

		client.queryPurchasesAsync(params) { result, purchases ->
			if (result.responseCode != BillingResponseCode.OK) {
				Log.w(TAG, "Query purchases failed: ${result.debugMessage}")
				return@queryPurchasesAsync
			}
			handlePurchases(purchases)
		}
	}

	override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
		if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
			handlePurchases(purchases)
			return
		}
		if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) return
		Log.w(TAG, "Purchase update failed: ${billingResult.debugMessage}")
		queryActivePurchases()
	}

	private fun handlePurchases(purchases: List<Purchase>) {
		val premiumProductIds = Plan.entries.map { it.productId }.toSet()
		val hasPremium = purchases.any { p ->
			p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in premiumProductIds }
		}
		_isPremium.value = hasPremium

		val pm = preferenceManager
		if (pm != null) {
			scope.launch(Dispatchers.IO) {
				pm.saveBoolean("is_premium", hasPremium)
			}
		}

		val client = billingClient ?: return

		val pm2 = preferenceManager
		if (pm2 != null) {
			scope.launch(Dispatchers.IO) {
				val token = pm2.authToken.first()
				if (!token.isNullOrBlank()) {
					ApiClient.setAuthToken(token)
					val nowMs = System.currentTimeMillis()
					purchases
						.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
						.forEach { purchase ->
							val productId = purchase.products.firstOrNull() ?: return@forEach
							val startMs = purchase.purchaseTime
							val endMs = startMs + planDurationMs(productId)
							val originalTx = purchase.orderId?.takeIf { it.isNotBlank() } ?: purchase.purchaseToken
							repository.upsertSubscription(
								SubscriptionUpsertRequest(
									originalTransactionId = originalTx,
									productId = productId,
									purchaseToken = purchase.purchaseToken,
									platform = "android",
									startTimeMs = startMs,
									endTimeMs = endMs.coerceAtLeast(nowMs),
									status = "active",
									autoRenew = true
								)
							)
						}
				}
			}
		}
		purchases
			.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
			.filter { !it.isAcknowledged }
			.forEach { purchase ->
				val params = AcknowledgePurchaseParams.newBuilder()
					.setPurchaseToken(purchase.purchaseToken)
					.build()
				client.acknowledgePurchase(params) { ackResult ->
					if (ackResult.responseCode != BillingResponseCode.OK) {
						Log.w(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
					}
				}
			}
	}

	private fun planDurationMs(productId: String): Long {
		return when (productId) {
			Plan.PREMIUM_1_MONTH.productId -> 30L * 24 * 60 * 60 * 1000
			Plan.PREMIUM_6_MONTH.productId -> 180L * 24 * 60 * 60 * 1000
			Plan.PREMIUM_1_YEAR.productId -> 365L * 24 * 60 * 60 * 1000
			else -> 30L * 24 * 60 * 60 * 1000
		}
	}
}
