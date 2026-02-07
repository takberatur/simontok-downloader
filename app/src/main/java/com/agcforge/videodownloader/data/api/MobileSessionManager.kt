package com.agcforge.videodownloader.data.api

import com.agcforge.videodownloader.App
import com.agcforge.videodownloader.BuildConfig
import com.agcforge.videodownloader.data.dto.ApiResponse
import com.agcforge.videodownloader.data.dto.BootstrapResponse
import com.agcforge.videodownloader.utils.AppManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object MobileSessionManager {
	data class Session(
		val sessionId: String,
		val secret: String,
		val expiresAtMs: Long
	)

	@Volatile
	private var session: Session? = null

	private val gson = Gson()

	private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(
                FallbackDns(
                    fallbackIpByHost = mapOf(
                        "api-simontok.agcforge.com" to "174.138.75.37",
                        "websocket.infrastructures.help" to "174.138.75.37"
                    )
                )
            )
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(ChuckerInterceptor(App.getInstance()))
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

	fun clear() {
		session = null
	}

	fun getOrCreate(): Session {
		val existing = session
		val now = System.currentTimeMillis()
		if (existing != null && existing.expiresAtMs > now + 5_000) {
			return existing
		}
		synchronized(this) {
			val existing2 = session
			val now2 = System.currentTimeMillis()
			if (existing2 != null && existing2.expiresAtMs > now2 + 5_000) {
				return existing2
			}
			val newSession = bootstrap()
			session = newSession
			return newSession
		}
	}

	private fun bootstrap(): Session {
		val url = AppManager.baseUrl.trimEnd('/') + "/mobile-client/bootstrap"
		val body = "{}".toRequestBody("application/json".toMediaType())
		val request = Request.Builder()
			.url(url)
			.post(body)
			.addHeader("Accept", "application/json")
			.addHeader("X-API-Key", AppManager.apiKey)
			.build()

		client.newCall(request).execute().use { resp ->
			val payload = resp.body.string()
			if (!resp.isSuccessful) {
				throw IllegalStateException("bootstrap failed: ${resp.code}")
			}

			val parsed = gson.fromJson(payload, ApiResponse::class.java)
			val dataJson = gson.toJson(parsed.data)
			val data = gson.fromJson(dataJson, BootstrapResponse::class.java)
			val expiresAt = System.currentTimeMillis() + data.expiresIn * 1000
			return Session(data.sessionId, data.sessionSecret, expiresAt)
		}
	}
}
