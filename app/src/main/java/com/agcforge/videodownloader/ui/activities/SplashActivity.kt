package com.agcforge.videodownloader.ui.activities

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.data.api.ApiClient
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.databinding.ActivitySplashBinding
import com.agcforge.videodownloader.helper.AdsConfig
import com.agcforge.videodownloader.helper.AdsInitializer
import com.agcforge.videodownloader.utils.PreferenceManager
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var preferenceManager: PreferenceManager
    private val repository by lazy { VideoDownloaderRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        setupAnimations()

        AdsConfig.init(this)

        startSplashSequence()
    }

    private fun setupAnimations() {
        binding.lottieAnimation.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                // Animation started
            }

            override fun onAnimationEnd(animation: Animator) {
                // Lottie animation completed
                animateTextElements()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }
    private fun startSplashSequence() {

        lifecycleScope.launch {
            val animationDelay = launch { delay(2500) }
            val fetchData = launch {
                fetchAndStoreApplication()
                initializeAuthToken()
            }

            fetchData.join()

            AdsConfig.isInitialized.first { it }

            AdsInitializer.initialize(application)
            initOneSignal()

            animationDelay.join()

            navigateToMain()
        }
    }

    private suspend fun initOneSignal() {
        AdsConfig.ONE_SIGNAL_ID?.let {
            OneSignal.initWithContext(this, it)
            OneSignal.Notifications.requestPermission(true)
        }
    }

	private suspend fun fetchAndStoreApplication() {
		repository.getApplication()
			.onSuccess { app ->
				preferenceManager.saveApplication(app)
			}
			.onFailure {
				// ignore
			}
	}

	private suspend fun initializeAuthToken() {
		val token = preferenceManager.authToken.first()
		if (!token.isNullOrEmpty()) {
			ApiClient.setAuthToken(token)
		}
	}

    private fun navigateToMain() {
        binding.root.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
            .start()
    }

    private fun animateTextElements() {
        ObjectAnimator.ofFloat(binding.tvAppName, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        binding.tvAppName.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(300)
            .withEndAction {
                binding.tvAppName.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }
}
