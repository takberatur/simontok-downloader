package com.agcforge.videodownloader.ui.activities.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.databinding.ActivityResetPasswordBinding
import com.agcforge.videodownloader.utils.showToast
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val repository by lazy { VideoDownloaderRepository() }

    private var resetToken: String? = null
    private var hasMinLength = false
    private var hasUppercase = false
    private var hasNumber = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get token from deep link or intent
        resetToken = intent.data?.getQueryParameter("token")
            ?: intent.getStringExtra("token")

        if (resetToken == null) {
            showToast("Invalid reset link")
            finish()
            return
        }

        setupToolbar()
        setupListeners()
        setupPasswordValidation()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnResetPassword.setOnClickListener {
                val newPassword = etNewPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()

                if (validatePasswords(newPassword, confirmPassword)) {
                    resetPassword(newPassword)
                }
            }
        }
    }

    private fun setupPasswordValidation() {
        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()

                // Check min length
                hasMinLength = password.length >= 8
                updateRequirement(binding.tvReqMinLength, hasMinLength)

                // Check uppercase
                hasUppercase = password.matches(".*[A-Z].*".toRegex())
                updateRequirement(binding.tvReqUppercase, hasUppercase)

                // Check number
                hasNumber = password.matches(".*[0-9].*".toRegex())
                updateRequirement(binding.tvReqNumber, hasNumber)
            }
        })
    }

    private fun updateRequirement(textView: android.widget.TextView, isMet: Boolean) {
        if (isMet) {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_check_circle,
                0, 0, 0
            )
        } else {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_check_circle_outline,
                0, 0, 0
            )
        }
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        var isValid = true

        if (newPassword.isEmpty()) {
            binding.tilNewPassword.error = "Password is required"
            isValid = false
        } else if (!hasMinLength || !hasUppercase || !hasNumber) {
            binding.tilNewPassword.error = "Password doesn't meet requirements"
            isValid = false
        } else {
            binding.tilNewPassword.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (newPassword != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun resetPassword(newPassword: String) {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val requestBody = mapOf(
                    "token" to resetToken!!,
					"new_password" to newPassword
                )

                val result = repository.resetPassword(requestBody)

                result.onSuccess {
                    showLoading(false)
                    showSuccessDialog()
                }.onFailure { error ->
                    showLoading(false)
                    showToast(error.message ?: "Failed to reset password")
                }

            } catch (e: Exception) {
                showLoading(false)
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun showSuccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Password Reset Successful")
            .setMessage("Your password has been reset successfully. You can now login with your new password.")
            .setPositiveButton("Login") { dialog, _ ->
                dialog.dismiss()
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnResetPassword.isEnabled = !isLoading
            etNewPassword.isEnabled = !isLoading
            etConfirmPassword.isEnabled = !isLoading
        }
    }
}
