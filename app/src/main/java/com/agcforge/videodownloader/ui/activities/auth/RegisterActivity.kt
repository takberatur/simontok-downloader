package com.agcforge.videodownloader.ui.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.data.api.ApiClient
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.databinding.ActivityRegisterBinding
import com.agcforge.videodownloader.ui.activities.MainActivity
import com.agcforge.videodownloader.ui.viewmodel.AuthViewModel
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.showToast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.agcforge.videodownloader.utils.Resource
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val repository by lazy { VideoDownloaderRepository() }
    private lateinit var preferenceManager: PreferenceManager
    private val viewModel: AuthViewModel by viewModels()
	private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

		preferenceManager = PreferenceManager(this)

        setupToolbar()
        setupListeners()
		viewModel.resetLoginResult()
		showLoading(false)
		observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnRegister.setOnClickListener {
                val fullName = etFullName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()

                if (validateInput(fullName, email, password, confirmPassword)) {
                    if (cbTerms.isChecked) {
                        register(fullName, email, password)
                    } else {
                        showToast("Please accept terms and conditions")
                    }
                }
            }
            btnGoogleLogin.setOnClickListener {
				lifecycleScope.launch {
					startGoogleSignIn()
				}
            }
            tvSignIn.setOnClickListener {
                finish()
            }
        }
    }

    private fun validateInput(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Validate full name
        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Full name is required"
            isValid = false
        } else if (fullName.length < 3) {
            binding.tilFullName.error = "Name must be at least 3 characters"
            isValid = false
        } else {
            binding.tilFullName.error = null
        }

        // Validate email
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Invalid email format"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // Validate password
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.matches(".*[A-Z].*".toRegex())) {
            binding.tilPassword.error = "Password must contain at least one uppercase letter"
            isValid = false
        } else if (!password.matches(".*[0-9].*".toRegex())) {
            binding.tilPassword.error = "Password must contain at least one number"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun register(fullName: String, email: String, password: String) {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val requestBody = mapOf(
                    "full_name" to fullName,
                    "email" to email,
                    "password" to password
                )

                val result = repository.register(requestBody)

                result.onSuccess { authResponse ->
                    showLoading(false)
                    showToast("Registration successful!")

					ApiClient.setAuthToken(authResponse.token)
					preferenceManager.saveAuthToken(authResponse.token)
					preferenceManager.saveUserInfo(
						authResponse.user.id,
						authResponse.user.email,
						authResponse.user.fullName
					)

					startActivity(Intent(this@RegisterActivity, MainActivity::class.java).apply {
						flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
					})
					finish()
                }.onFailure { error ->
                    showLoading(false)
                    showToast(error.message ?: "Registration failed")
                }

            } catch (e: Exception) {
                showLoading(false)
                showToast("Registration failed: ${e.message}")
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnRegister.isEnabled = !isLoading
            btnGoogleLogin.isEnabled = !isLoading
            etFullName.isEnabled = !isLoading
            etEmail.isEnabled = !isLoading
            etPassword.isEnabled = !isLoading
            etConfirmPassword.isEnabled = !isLoading
        }
    }

	private fun observeViewModel() {
		lifecycleScope.launch {
			viewModel.loginResult.collect { resource ->
				when (resource) {
					is Resource.Idle -> showLoading(false)
					is Resource.Loading -> showLoading(true)
					is Resource.Success -> {
						showLoading(false)
						startActivity(Intent(this@RegisterActivity, MainActivity::class.java).apply {
							flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
						})
						finish()
						viewModel.resetLoginResult()
					}
					is Resource.Error -> {
						showLoading(false)
						showToast(resource.message ?: "Google login failed")
						viewModel.resetLoginResult()
					}
                }
			}
		}
	}

	private suspend fun startGoogleSignIn() {
		try {
			val googleIdOption = GetGoogleIdOption.Builder()
				.setServerClientId(getString(com.agcforge.videodownloader.R.string.google_web_client_id))
				.setFilterByAuthorizedAccounts(false)
				.setAutoSelectEnabled(true)
				.build()

			val request = GetCredentialRequest.Builder()
				.addCredentialOption(googleIdOption)
				.build()

			val result = credentialManager.getCredential(this, request)
			val cred = result.credential
			if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
				val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
				viewModel.loginGoogle(googleCred.idToken)
				return
			}
			showToast("Google login failed")
		} catch (e: GoogleIdTokenParsingException) {
			showToast(e.message ?: "Google login failed")
		} catch (e: GetCredentialException) {
			showToast(e.message ?: "Google login cancelled")
		} catch (e: Exception) {
			showToast(e.message ?: "Google login failed")
		}
	}
}
