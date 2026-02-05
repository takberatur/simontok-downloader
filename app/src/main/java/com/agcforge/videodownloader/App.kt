package com.agcforge.videodownloader

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.agcforge.videodownloader.helper.AppOpenAdManager
import com.agcforge.videodownloader.helper.CurrentActivityTracker
import com.agcforge.videodownloader.utils.DownloadManagerCleaner



class App : Application(), DefaultLifecycleObserver {

    companion object {
        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: synchronized(this) {
                instance ?: throw IllegalStateException("App not initialized")
            }
        }

        fun getContext(): Context {
            return instance?.applicationContext ?: throw IllegalStateException("Context not initialized")
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
    }

	override fun onCreate() {
		super<Application>.onCreate()
		instance = this

		ProcessLifecycleOwner.get().lifecycle.addObserver(this)
		registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityResumed(activity: Activity) {
				CurrentActivityTracker.set(activity)
			}
			override fun onActivityPaused(activity: Activity) {
				if (CurrentActivityTracker.get() === activity) {
					CurrentActivityTracker.set(null)
				}
			}
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
			override fun onActivityDestroyed(activity: Activity) {
				if (CurrentActivityTracker.get() === activity) {
					CurrentActivityTracker.set(null)
				}
			}
		})

		DownloadManagerCleaner.clearFailedDownloads(this)
		AppOpenAdManager.loadAd(this)
	}

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        AppOpenAdManager.onAppEnteredBackground()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
		CurrentActivityTracker.get()?.let {
            AppOpenAdManager.showAdIfAvailable(it)
        }
    }
}
