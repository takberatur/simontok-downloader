package com.agcforge.videodownloader.utils

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object UrlHelper {

    /**
     * Enable all standard features
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun setupStandardWebView(webView: WebView) {
        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true

            // Storage
            domStorageEnabled = true
            databaseEnabled = true

            // Display
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Media
            mediaPlaybackRequiresUserGesture = false

            // Access
            allowFileAccess = true
            allowContentAccess = true

            // Cache
            cacheMode = WebSettings.LOAD_DEFAULT

            // Mixed content (for HTTPS sites loading HTTP content)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Enable cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    /**
     * Enable desktop mode
     */
    fun enableDesktopMode(webView: WebView) {
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Enable mobile mode (default)
     */
    fun enableMobileMode(webView: WebView) {
        webView.settings.userAgentString = null // Use default mobile UA
    }

    /**
     * Clear all WebView data
     */
    fun clearWebViewData(webView: WebView) {
        webView.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }

        CookieManager.getInstance().removeAllCookies(null)
    }

    /**
     * Get custom user agent for specific sites
     */
    fun getCustomUserAgent(siteName: String): String? {
        return when (siteName.lowercase()) {
            "facebook" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            "instagram" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15"
            "twitter", "x" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            else -> null
        }
    }
}