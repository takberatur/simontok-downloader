package com.agcforge.videodownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.data.dto.AuthResponse
import com.agcforge.videodownloader.data.model.User
import com.agcforge.videodownloader.data.websocket.CentrifugoManager
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy { VideoDownloaderRepository() }
    private val centrifugoManager = CentrifugoManager.getInstance(application)
    private val preferenceManager = PreferenceManager(application)

    private val _loginResult = MutableStateFlow<Resource<AuthResponse>>(Resource.Idle())
    val loginResult: StateFlow<Resource<AuthResponse>> = _loginResult.asStateFlow()

    private val _currentUser = MutableStateFlow<Resource<User>>(Resource.Idle())
    val currentUser: StateFlow<Resource<User>> = _currentUser.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = Resource.Loading()

			val result = withTimeoutOrNull(30_000) {
				repository.login(email, password)
			}
			if (result == null) {
				_loginResult.value = Resource.Error("Login timeout")
				return@launch
			}

			result
				.onSuccess { authResponse ->
					_loginResult.value = Resource.Success(authResponse)

					preferenceManager.saveAuthToken(authResponse.token)
					preferenceManager.saveUserInfo(
						authResponse.user.id,
						authResponse.user.email,
						authResponse.user.fullName
					)

					repository.getCentrifugoToken()
						.onSuccess { tokenResponse ->
							centrifugoManager.initialize(authResponse.user.id, tokenResponse.token)
							centrifugoManager.connect()
						}
						.onFailure {
							centrifugoManager.initialize(authResponse.user.id, null)
							centrifugoManager.connect()
						}
				}
				.onFailure { error ->
					_loginResult.value = Resource.Error(error.message ?: "Login failed")
				}
        }
    }

    fun loginGoogle(credential: String) {
        viewModelScope.launch {
            _loginResult.value = Resource.Loading()

			val result = withTimeoutOrNull(30_000) {
				repository.loginGoogle(credential)
			}
			if (result == null) {
				_loginResult.value = Resource.Error("Google login timeout")
				return@launch
			}

			result
				.onSuccess { authResponse ->
					_loginResult.value = Resource.Success(authResponse)

					preferenceManager.saveAuthToken(authResponse.token)
					preferenceManager.saveUserInfo(
						authResponse.user.id,
						authResponse.user.email,
						authResponse.user.fullName
					)

					repository.getCentrifugoToken()
						.onSuccess { tokenResponse ->
							centrifugoManager.initialize(authResponse.user.id, tokenResponse.token)
							centrifugoManager.connect()
						}
						.onFailure {
							centrifugoManager.initialize(authResponse.user.id, null)
							centrifugoManager.connect()
						}
				}
				.onFailure { error ->
					_loginResult.value = Resource.Error(error.message ?: "Google login failed")
				}
        }
    }

	fun resetLoginResult() {
		_loginResult.value = Resource.Idle()
	}

    fun getCurrentUser() {
        viewModelScope.launch {
            _currentUser.value = Resource.Loading()

            repository.getCurrentUser()
                .onSuccess { user ->
                    _currentUser.value = Resource.Success(user)
                }
                .onFailure { error ->
                    _currentUser.value = Resource.Error(error.message ?: "Failed to get user")
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
                .onSuccess {
                    // Disconnect WebSocket
                    centrifugoManager.disconnect()

                    // Clear preferences
                    preferenceManager.clearUserData()
                }
                .onFailure { error ->
                    // Still clear local data even if API call fails
                    centrifugoManager.disconnect()
                    preferenceManager.clearUserData()
                }
        }
    }
}
