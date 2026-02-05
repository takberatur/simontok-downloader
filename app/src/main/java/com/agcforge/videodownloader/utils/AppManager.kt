package com.agcforge.videodownloader.utils

object AppManager {
	init {
		System.loadLibrary("apphandler")
	}

	external fun nativeBaseUrl(): String
	external fun nativeCentrifugoUrl(): String
	external fun nativeApiKey(): String

	val baseUrl: String by lazy { nativeBaseUrl() }
	val centrifugoUrl: String by lazy { nativeCentrifugoUrl() }
	val apiKey: String by lazy { nativeApiKey() }
}
