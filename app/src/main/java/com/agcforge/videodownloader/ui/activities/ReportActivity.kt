package com.agcforge.videodownloader.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.dto.FormReportError
import com.agcforge.videodownloader.databinding.ActivityReportBinding
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.helper.ads.BillingManager
import com.agcforge.videodownloader.helper.ads.NativeAdsHelper
import com.agcforge.videodownloader.ui.component.AppAlertDialog
import com.agcforge.videodownloader.utils.CrashReporter
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.showToast
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ReportActivity: BaseActivity() {

    private lateinit var _binding: ActivityReportBinding
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager

    private var nativeAdsHelper: NativeAdsHelper? = null
    private var bannerAdsHelper: BannerAdsHelper? = null

    private var isPremium: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        _binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        setupToolbar()
        handleIntentExtras()
        setupUI()
        setupAds()
        observePremiumAdsState()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.send_report)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    private fun handleIntentExtras() {
        val subject = intent.getStringExtra("EXTRA_SUBJECT")
        val platform = intent.getStringExtra("EXTRA_PLATFORM")
        val url = intent.getStringExtra("EXTRA_URL")
        val message = intent.getStringExtra("EXTRA_MESSAGE")

        platform?.let { binding.etPlatform.setText(it) }
        url?.let { binding.etUrl.setText(it) }
        message?.let { binding.etMessage.setText(it) }

        if (!subject.isNullOrEmpty() && !message.isNullOrEmpty()) {
            binding.etFullName.requestFocus()
        }
    }
    private fun setupUI() {

        binding.tilUrl.setEndIconOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    binding.etUrl.setText(text)
                    binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                    showToast("${getString(R.string.paste)}: $text", Toast.LENGTH_SHORT)
                }
            }
        }
        binding.btnReport.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val subject = binding.etSubject.text.toString().trim()
            val platform = binding.etPlatform.text.toString().trim()
            val url = binding.etUrl.text.toString().trim()
            val message = binding.etMessage.text.toString().trim()



            try {
                when {
                    name.isEmpty() -> {
                        throw FormReportError(
                            name, email, url,platform, subject, getString(R.string.name_required),
                        )
                    }
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        throw FormReportError(
                            name, email, url,platform, subject, getString(R.string.invalid_format_email),
                        )
                    }
                    subject.length < 5 -> {
                        throw FormReportError(
                            name, email, url,platform, subject, getString(R.string.subject_minimum_length),
                        )
                    }
                    message.length < 10 -> {
                        throw FormReportError(
                            name, email, url,platform, subject,getString(R.string.message_minimum_length),
                        )
                    }
                    else -> {
                        val throwData = FormReportError(
                            name, email, url,platform, subject, message,
                        )
                        sendReport(throwData)
                    }
                }
            } catch (e: FormReportError) {
                showErrorOnField(e)
            }

        }
    }

    private fun showErrorOnField(e: FormReportError) {
        when (e.message) {
            "Nama tidak boleh kosong!" -> binding.etFullName.error = e.message
            "Format email salah!" -> binding.etEmail.error = e.message
            "Subject minimal 5 karakter!" -> binding.etSubject.error = e.message
            "Pesan laporan harus diisi!" -> binding.etMessage.error = e.message
        }

        Toast.makeText(this, "${getString(R.string.error)}: ${e.message}", Toast.LENGTH_SHORT).show()
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
            binding.adsNative.visibility = View.GONE
            destroyAdsHelpers()
        } else {
            binding.adsBanner.visibility = View.VISIBLE
            binding.adsNative.visibility = View.VISIBLE

            if (bannerAdsHelper == null) {
                bannerAdsHelper = BannerAdsHelper(this)
            }
            if (nativeAdsHelper == null) {
                nativeAdsHelper = NativeAdsHelper(this)
            }

            bannerAdsHelper?.loadAndAttachBanner(binding.adsBanner)
            nativeAdsHelper?.loadAndAttachNativeAd(
                binding.adsNative,
                NativeAdsHelper.NativeAdSize.MEDIUM
            )
        }
    }
    private fun destroyAdsHelpers() {
        bannerAdsHelper?.destroy()
        nativeAdsHelper?.destroy()
        bannerAdsHelper = null
        nativeAdsHelper = null
    }

    override fun onPause() {
        super.onPause()
        bannerAdsHelper?.pause()
    }

    override fun onResume() {
        super.onResume()
        bannerAdsHelper?.resume()
    }

    private fun sendReport(throwData: FormReportError): Unit {

        try {
            binding.progressBar.isVisible = true
            val gson = Gson()

            val jsonString: String = gson.toJson(throwData)
            val myThrowable = Throwable(throwData.message)

            val telegramMessage = """
                🚨 *New Report Error* 🚨
                ```json
                $jsonString
                ```
            """.trimIndent()

            binding.progressBar.setProgress(50)

            CrashReporter.reportNonFatal(
                this,
                myThrowable,
                "non_fatal",
                telegramMessage
            )

            binding.progressBar.setProgress(100)
            binding.progressBar.isVisible = false

            showDialogAlert(
                AppAlertDialog.AlertDialogType.SUCCESS,
                getString(R.string.ok),
            )
        }catch (e: Exception) {
            binding.progressBar.setProgress(0)
            binding.progressBar.isVisible = false
            showDialogAlert(
                AppAlertDialog.AlertDialogType.ERROR,
                getString(R.string.error),
                e.message
            )
        }
    }

    private fun showDialogAlert(
        type: AppAlertDialog.AlertDialogType = AppAlertDialog.AlertDialogType.INFO,
        title: String? = null,
        message: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val dialog = AppAlertDialog
            .Builder(this)
            .setNegativeButtonText(getString(R.string.cancel))
            .setPositiveButtonText(getString(R.string.ok))
            .setType(type)

        if (title != null) {
            dialog.setTitle(title)
        }
        if (message != null) {
            dialog.setMessage(message)
        }
        if (onAction != null) {
            dialog.setOnPositiveClick(onAction)
        }
        dialog.show()
    }




}