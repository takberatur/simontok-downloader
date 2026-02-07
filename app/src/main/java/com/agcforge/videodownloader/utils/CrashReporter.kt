package com.agcforge.videodownloader.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.os.LocaleListCompat
import com.agcforge.videodownloader.BuildConfig
import com.agcforge.videodownloader.data.api.ApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import androidx.core.content.edit

object CrashReporter {
	private const val PREF = "crash_reporter"
	private const val KEY_PAYLOAD = "pending_payload"
	private const val KEY_TS = "pending_ts"
	private const val MAX_STACK = 16000
	private const val MAX_MESSAGE = 2000

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val gson = Gson()

	fun install(context: Context) {
		sendPending(context)
		val prev = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { t, e ->
			runCatching {
				store(context, buildPayload(context, t, e, level = "fatal", tag = "uncaught"))
			}
			prev?.uncaughtException(t, e)
		}
	}

	fun reportNonFatal(context: Context, throwable: Throwable, tag: String = "non_fatal", message: String? = null) {
		val raw = buildPayload(context, Thread.currentThread(), throwable, level = "error", tag = tag, overrideMessage = message)
		scope.launch {
			sendRaw(raw)
		}
	}

	private fun sendPending(context: Context) {
		scope.launch {
			val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
			val raw = prefs.getString(KEY_PAYLOAD, null) ?: return@launch
			prefs.edit { remove(KEY_PAYLOAD).remove(KEY_TS) }
			sendRaw(raw)
		}
	}

	private fun store(context: Context, payload: String) {
		val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
		prefs.edit { putString(KEY_PAYLOAD, payload).putLong(KEY_TS, System.currentTimeMillis()) }
	}

	private suspend fun sendRaw(raw: String) {
		runCatching {
			val type = object : TypeToken<Map<String, String>>() {}.type
			val map: Map<String, String> = gson.fromJson(raw, type) ?: return
			ApiClient.apiService.reportError(map)
		}
	}

	private suspend fun send(map: Map<String, String>) {
		runCatching {
			ApiClient.apiService.reportError(map)
		}
	}

	private fun buildPayload(
		context: Context,
		thread: Thread,
		throwable: Throwable,
		level: String,
		tag: String,
		overrideMessage: String? = null
	): String {
		val sw = StringWriter()
		val pw = PrintWriter(sw)
		throwable.printStackTrace(pw)
		pw.flush()
		val stack = sw.toString().take(MAX_STACK)
		val msg = (overrideMessage ?: throwable.message ?: throwable.javaClass.name).take(MAX_MESSAGE)
		val locales = LocaleListCompat.getAdjustedDefault()
		val locale = if (!locales.isEmpty) locales[0]?.toLanguageTag() ?: "" else ""
		val dm = DisplayMetrics()
		context.resources.displayMetrics.also {
			dm.setTo(it)
		}
		val screen = "${dm.widthPixels}x${dm.heightPixels}@${dm.densityDpi}"

		val map = linkedMapOf(
			"level" to level,
			"tag" to tag,
			"message" to msg,
			"thread" to thread.name,
			"app_version" to BuildConfig.VERSION_NAME,
			"version_code" to BuildConfig.VERSION_CODE.toString(),
			"build_type" to BuildConfig.BUILD_TYPE,
			"android_version" to Build.VERSION.RELEASE,
			"device_brand" to Build.BRAND,
			"device_model" to Build.MODEL,
			"device" to Build.DEVICE,
			"abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
			"locale" to locale,
			"screen" to screen,
			"timestamp_ms" to System.currentTimeMillis().toString(),
			"stack" to stack.replace("\u0000", "")
		)
		return gson.toJson(map)
	}
}

// Example usage in non error :

/**
 * CrashReporter.reportNonFatal(context, throwable, tag = "feature_name", message = "additional message")
 */