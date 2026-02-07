package com.agcforge.videodownloader.ui.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agcforge.videodownloader.databinding.ActivityLoginBinding
import com.agcforge.videodownloader.service.WebSocketService
import com.agcforge.videodownloader.ui.activities.MainActivity
import com.agcforge.videodownloader.ui.viewmodel.AuthViewModel
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.Resource
import com.agcforge.videodownloader.utils.showToast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.agcforge.videodownloader.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var preferenceManager: PreferenceManager
	private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        setupListeners()
        observeViewModel()
		viewModel.resetLoginResult()
		showLoading(false)

		checkIfLoggedIn()
    }

    private fun checkIfLoggedIn() {
        lifecycleScope.launch {
			val token = preferenceManager.authToken.first()
			if (!token.isNullOrEmpty()) {
				navigateToMain()
			}
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnLogin.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (validateInput(email, password)) {
                    viewModel.login(email, password)
                }
            }

            tvSignUp.setOnClickListener {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            }

            tvForgotPassword.setOnClickListener {
                startActivity(Intent(this@LoginActivity, ForgotPasswordActivity::class.java))
            }

            btnGoogleLogin.setOnClickListener {
				lifecycleScope.launch {
					startGoogleSignIn()
				}
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        // Validate email
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.email_is_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.invalid_format_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // Validate password
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.password_is_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.password_must_be_at_least_6_characters)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.loginResult.collect { resource ->
                when (resource) {
                    is Resource.Idle -> {
                        showLoading(false)
                    }
                    is Resource.Loading -> {
                        showLoading(true)
                    }
                    is Resource.Success -> {
                        showLoading(false)
                        resource.data?.let { authResponse ->
                            showToast("Login successful!")

                            // Start WebSocket service
                            WebSocketService.start(
                                this@LoginActivity,
                                authResponse.user.id,
                                authResponse.token
                            )

                            navigateToMain()
						viewModel.resetLoginResult()
                        }
                    }
                    is Resource.Error -> {
                        showLoading(false)
                        showToast(resource.message ?: "Login failed")
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

    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !isLoading
            btnGoogleLogin.isEnabled = !isLoading
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
