package com.agcforge.videodownloader.ui.activities.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.databinding.ActivityForgotPasswordBinding
import com.agcforge.videodownloader.utils.showToast
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val repository by lazy { VideoDownloaderRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnSendResetLink.setOnClickListener {
                val email = etEmail.text.toString().trim()

                if (validateEmail(email)) {
                    sendResetLink(email)
                }
            }

            tvBackToLogin.setOnClickListener {
                finish()
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                binding.tilEmail.error = getString(R.string.email_is_required)
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = getString(R.string.invalid_format_email)
                false
            }
            else -> {
                binding.tilEmail.error = null
                true
            }
        }
    }

    private fun sendResetLink(email: String) {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val requestBody = mapOf("email" to email)
                val result = repository.forgotPassword(requestBody)

                result.onSuccess {
                    showLoading(false)
                    showToast(getString(R.string.reset_link_sent_to_email, email))

                    // Show success dialog or navigate
                    showSuccessDialog(email)
                }.onFailure { error ->
                    showLoading(false)
                    showToast(error.message ?: getString(R.string.failed_to_send_reset_link))
                }

            } catch (e: Exception) {
                showLoading(false)
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun showSuccessDialog(email: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.check_your_email))
            .setMessage(getString(R.string.reset_link_sent_to_email, email))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnSendResetLink.isEnabled = !isLoading
            etEmail.isEnabled = !isLoading
        }
    }
}