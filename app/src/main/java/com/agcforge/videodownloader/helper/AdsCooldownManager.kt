package com.agcforge.videodownloader.helper

import java.util.concurrent.ConcurrentHashMap

object AdsCooldownManager {
	enum class AdType {
		INTERSTITIAL,
		REWARD,
		BANNER
	}

	private data class Key(val adType: AdType, val provider: AdsConfig.AdsProvider)
	private data class State(var consecutiveFailures: Int, var cooldownUntilMs: Long)

	private val states = ConcurrentHashMap<Key, State>()

	fun isInCooldown(adType: AdType, provider: AdsConfig.AdsProvider, nowMs: Long = System.currentTimeMillis()): Boolean {
		return states[Key(adType, provider)]?.cooldownUntilMs?.let { it > nowMs } ?: false
	}

	fun recordSuccess(adType: AdType, provider: AdsConfig.AdsProvider) {
		states.remove(Key(adType, provider))
	}

	fun recordFailure(adType: AdType, provider: AdsConfig.AdsProvider, nowMs: Long = System.currentTimeMillis()) {
		val key = Key(adType, provider)
		val state = states.getOrPut(key) { State(consecutiveFailures = 0, cooldownUntilMs = 0L) }
		state.consecutiveFailures += 1

		val threshold = AdsConfig.COOLDOWN_FAILURE_THRESHOLD
		val cooldownMs = AdsConfig.COOLDOWN_DURATION_MS
		if (threshold > 0 && state.consecutiveFailures >= threshold) {
			state.cooldownUntilMs = nowMs + cooldownMs
			state.consecutiveFailures = 0
		}
	}

	fun filterEligible(adType: AdType, providers: List<AdsConfig.AdsProvider>): List<AdsConfig.AdsProvider> {
		val nowMs = System.currentTimeMillis()
		return providers.filterNot { isInCooldown(adType, it, nowMs) }
	}
}

