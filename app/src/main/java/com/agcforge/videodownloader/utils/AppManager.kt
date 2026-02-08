package com.agcforge.videodownloader.utils

import androidx.annotation.Keep
import com.agcforge.videodownloader.BuildConfig

@Keep
object AppManager {
	init {
		System.loadLibrary("apphandler")
	}
    @Keep
	external fun nativeBaseUrl(): String
    @Keep
	external fun nativeCentrifugoUrl(): String
    @Keep
	external fun nativeApiKey(): String

	val baseUrl: String by lazy { nativeBaseUrl() }
	val centrifugoUrl: String by lazy { nativeCentrifugoUrl() }
	val apiKey: String by lazy {
		nativeApiKey().ifBlank { BuildConfig.MOBILE_API_KEY }
	}
}
