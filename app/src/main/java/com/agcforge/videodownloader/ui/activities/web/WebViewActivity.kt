package com.agcforge.videodownloader.ui.activities.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.databinding.ActivityWebviewBinding
import com.agcforge.videodownloader.ui.activities.BaseActivity
import com.agcforge.videodownloader.utils.WebViewConfig
import com.agcforge.videodownloader.utils.showToast
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.helper.ads.BillingManager
import com.agcforge.videodownloader.utils.UrlHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class WebViewActivity: BaseActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var currentUrl: String = ""
    private var isDesktopMode = false

    private var bannerAdsHelper: BannerAdsHelper? = null

    private var isPremium: Boolean = false

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_DESKTOP_MODE = "extra_desktop_mode"
        private const val FILE_CHOOSER_REQUEST_CODE = 1001

        fun start(
            context: Context,
            url: String,
            title: String? = null,
            desktopMode: Boolean = false
        ) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DESKTOP_MODE, desktopMode)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        setupBackPressedCallback()

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com"
        val pageTitle = intent.getStringExtra(EXTRA_TITLE)
        isDesktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, false)

        setupToolbar(pageTitle)
        setupAddressBar()
        setupWebView()
        setupButtons()

        setupAds()
        observePremiumAdsState()

        binding.webView.loadUrl(currentUrl)
    }

    private fun setupToolbar(pageTitle: String?) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = pageTitle ?: getString(R.string.app_name)
        }

        binding.toolbar.setNavigationOnClickListener {
           finish()
        }
    }

    private fun setupAddressBar() {
        binding.tvUrl.text = currentUrl

        // Click to edit URL
        binding.cvAddressBar.setOnClickListener {
            showUrlEditDialog()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Apply standard config
        WebViewConfig.setupStandardWebView(binding.webView)

        // Apply desktop mode if needed
        if (isDesktopMode) {
            WebViewConfig.enableDesktopMode(binding.webView)
        }

        binding.webView.apply {
            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 0
                    currentUrl = url ?: ""
                    binding.tvUrl.text = currentUrl
                    updateNavigationButtons()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    supportActionBar?.title = view?.title ?: getString(R.string.browser)
                    currentUrl = url ?: ""
                    binding.tvUrl.text = currentUrl
                    updateNavigationButtons()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    binding.progressBar.visibility = View.GONE

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (request?.isForMainFrame == true) {
                            loadErrorPage(error?.description?.toString() ?: getString(R.string.unknown_error))
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    when {
                        url.startsWith("tel:") -> {
                            startActivity(Intent(Intent.ACTION_DIAL, url.toUri()))
                            return true
                        }
                        url.startsWith("mailto:") -> {
                            startActivity(Intent(Intent.ACTION_SENDTO, url.toUri()))
                            return true
                        }
                        url.startsWith("whatsapp:") -> {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (e: Exception) {
                                showToast(getString(R.string.whatsapp_not_installed))
                            }
                            return true
                        }
                    }

                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress

                    if (newProgress == 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    supportActionBar?.title = title ?: getString(R.string.browser)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }

                    try {
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                    } catch (e: Exception) {
                        fileUploadCallback = null
                        showToast(getString(R.string.cannot_open_file_chooser))
                        return false
                    }

                    return true
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                handleDownload(url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnHome.setOnClickListener {
            binding.webView.loadUrl(currentUrl)
        }

        updateNavigationButtons()
    }

    private fun updateNavigationButtons() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()

        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1.0f else 0.5f
        binding.btnForward.alpha = if (binding.webView.canGoForward()) 1.0f else 0.5f
    }

    private fun setupAds() {
        isPremium = BillingManager.isPremiumNow()
        applyPremiumAdsState(isPremium)
    }

    private fun observePremiumAdsState() {
        lifecycleScope.launch {
            BillingManager.isPremium.collect { premiumStatus ->
                if (isPremium != premiumStatus) {
                    isPremium = premiumStatus
                    applyPremiumAdsState(premiumStatus)
                }
            }
        }
    }

    private fun applyPremiumAdsState(isPremium: Boolean) {
        if (isPremium) {
            binding.adsBanner.visibility = View.GONE
            destroyAdsHelpers()
        } else {
            binding.adsBanner.visibility = View.VISIBLE

            if (bannerAdsHelper == null) {
                bannerAdsHelper = BannerAdsHelper(this)
            }

            bannerAdsHelper?.loadAndAttachBanner(binding.adsBanner)
        }
    }
    private fun destroyAdsHelpers() {
        bannerAdsHelper?.destroy()
        bannerAdsHelper = null
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription(getString(R.string.downloading_file))
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            showToast(getString(R.string.downloading_file) + " $fileName")

        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.download_failed, ) + " ${e.message}")
        }
    }

    private fun showUrlEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.text_input_edit, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.textInputEditText)

        editText.apply {
            setText(currentUrl)
            setSelectAllOnFocus(true)
            setHint(getString(R.string.please_enter_valid_url))
        }

        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle(getString(R.string.please_enter_valid_url))
            .setView(dialogView)
            .setPositiveButton("Go") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    val url = UrlHelper.processInput(
                        input = input,
                        searchEngine = UrlHelper.SearchEngine.GOOGLE
                    )
                    binding.webView.loadUrl(url)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadErrorPage(errorMessage: String) {
        val html = """
            <html>
            <body style="text-align:center; padding:50px; font-family:Arial;">
                <h1>⚠️</h1>
                <h2>Page Load Error</h2>
                <p>$errorMessage</p>
                <button onclick="location.reload()">Retry</button>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)

        menu?.findItem(R.id.action_desktop_mode)?.apply {
            setIcon(if (isDesktopMode) R.drawable.ic_phone else R.drawable.ic_desktop)
            title = if (isDesktopMode) getString(R.string.mobile_mode) else getString(R.string.desktop_mode)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareCurrentPage()
                true
            }
            R.id.action_copy_url -> {
                copyUrlToClipboard()
                true
            }
            R.id.action_open_browser -> {
                openInExternalBrowser()
                true
            }
            R.id.action_desktop_mode -> {
                toggleDesktopMode()
                true
            }
            R.id.action_clear_cache -> {
                clearCache()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentPage() {
        val title = binding.webView.title ?: getString(R.string.webpage)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, currentUrl)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    private fun copyUrlToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", currentUrl)
        clipboard.setPrimaryClip(clip)
        showToast(getString(R.string.url_copied_to_clipboard))
    }

    private fun openInExternalBrowser() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, currentUrl.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            showToast(getString(R.string.no_browser_app_found))
        }
    }

    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode

        if (isDesktopMode) {
            WebViewConfig.enableDesktopMode(binding.webView)
        } else {
            WebViewConfig.enableMobileMode(binding.webView)
        }

        binding.webView.reload()
        invalidateOptionsMenu()

        showToast(if (isDesktopMode) getString(R.string.desktop_mode_enabled) else getString(R.string.mobile_mode_enabled))
    }

    private fun clearCache() {
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle(getString(R.string.clear_cache))
            .setMessage(getString(R.string.confirm_clear_cache))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                WebViewConfig.clearWebViewData(binding.webView)
                showToast(getString(R.string.cache_cleared_success))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val results = arrayOf(data.data ?: Uri.EMPTY)
                fileUploadCallback?.onReceiveValue(results)
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        bannerAdsHelper?.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        bannerAdsHelper?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.webView.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
    }
}