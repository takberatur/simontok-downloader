package com.agcforge.videodownloader.helper

import android.app.Activity
import java.lang.ref.WeakReference

object CurrentActivityTracker {
	@Volatile
	private var ref: WeakReference<Activity>? = null

	fun set(activity: Activity?) {
		ref = if (activity == null) null else WeakReference(activity)
	}

	fun get(): Activity? {
		return ref?.get()
	}
}

