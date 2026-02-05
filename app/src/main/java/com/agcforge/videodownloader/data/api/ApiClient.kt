package com.agcforge.videodownloader.data.api

import com.agcforge.videodownloader.App
import com.agcforge.videodownloader.BuildConfig
import com.agcforge.videodownloader.utils.AppManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okio.Buffer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.UUID
import java.util.concurrent.TimeUnit

object ApiClient {
    private val baseUrl: String by lazy { AppManager.baseUrl }
    private val apiKey: String by lazy { AppManager.apiKey }

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val request = req.newBuilder()
        authToken?.let {
            request.addHeader("Authorization", "Bearer $it")
        }
        request.addHeader("Accept", "application/json")
		val path = req.url.encodedPath
		if (path.endsWith("/api/v1/mobile-client/bootstrap")) {
			request.addHeader("X-API-Key", apiKey)
		}
        chain.proceed(request.build())
    }

	private val signatureInterceptor = Interceptor { chain ->
		val original = chain.request()
		val path = original.url.encodedPath
		if (path.endsWith("/api/v1/mobile-client/bootstrap")) {
			return@Interceptor chain.proceed(original)
		}

		fun signWithSession(session: MobileSessionManager.Session): okhttp3.Request {
			val ts = (System.currentTimeMillis() / 1000L).toString()
			val nonce = UUID.randomUUID().toString().replace("-", "")
			val url = buildString {
				append(original.url.encodedPath)
				val q = original.url.encodedQuery
				if (!q.isNullOrEmpty()) {
					append('?')
					append(q)
				}
			}
			val bodyBytes = requestBodyToBytes(original.body)
			val bodySha = sha256Hex(bodyBytes)
			val canonical = "${original.method}\n${url}\n${ts}\n${nonce}\n${bodySha}"
			val sig = hmacSha256Hex(session.secret, canonical)

			return original.newBuilder()
				.header("X-Session-Id", session.sessionId)
				.header("X-Timestamp", ts)
				.header("X-Nonce", nonce)
				.header("X-Signature", sig)
				.build()
		}

		val session = MobileSessionManager.getOrCreate()
		var response = chain.proceed(signWithSession(session))
		if (response.code == 401 || response.code == 403) {
			response.close()
			MobileSessionManager.clear()
			val session2 = MobileSessionManager.getOrCreate()
			response = chain.proceed(signWithSession(session2))
		}
		response
	}

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient by lazy {
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
            .addInterceptor(authInterceptor)
            .addInterceptor(signatureInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

	private fun requestBodyToBytes(body: RequestBody?): ByteArray {
		if (body == null) return ByteArray(0)
		val buffer = Buffer()
		body.writeTo(buffer)
		return buffer.readByteArray()
	}

	private fun sha256Hex(bytes: ByteArray): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val hashed = digest.digest(bytes)
		return hashed.joinToString("") { "%02x".format(it) }
	}

	private fun hmacSha256Hex(secret: String, message: String): String {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
		val out = mac.doFinal(message.toByteArray())
		return out.joinToString("") { "%02x".format(it) }
	}
}
