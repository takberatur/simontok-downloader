package com.agcforge.videodownloader.data.api

import com.agcforge.videodownloader.data.dto.AuthResponse
import com.agcforge.videodownloader.data.dto.DownloadRequest
import com.agcforge.videodownloader.data.dto.LoginRequest
import com.agcforge.videodownloader.data.dto.TokenResponse
import com.agcforge.videodownloader.data.dto.ApiResponse
import com.agcforge.videodownloader.data.model.Application
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.model.Platform
import com.agcforge.videodownloader.data.model.User
import kotlinx.coroutines.flow.first

class VideoDownloaderRepository {

    private val api = ApiClient.apiService

	private fun apiError(resp: ApiResponse<*>?, fallback: String): String {
		val msg = resp?.message?.trim()
		if (!msg.isNullOrEmpty()) {
			return msg
		}
		val err = resp?.error
		if (err != null) {
			val s = err.toString().trim()
			if (s.isNotEmpty() && s != "null") {
				return s
			}
		}
		return fallback
	}

    suspend fun getPlatforms(): Result<List<Platform>> {
        return try {
            val response = api.getPlatforms()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception(apiError(response.body(), "Failed to fetch platforms")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getApplication(): Result<Application> {
        return try {
            val response = api.getApplication()
            if (response.isSuccessful && response.body()?.success == true) {
                val app = response.body()?.data
                if (app != null) {
                    println("AppConfig: ${app}")
                    Result.success(app)
                } else {
                    Result.failure(Exception("No data returned"))
                }
            } else {
                Result.failure(Exception(apiError(response.body(), "Failed to fetch application")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDownloadVideo(url: String, type: String, platformId: String? = null): Result<DownloadTask> {
        return try {
            val request = DownloadRequest(url = url, type = type, platformId = platformId)
            val response = api.createDownloadVideo(request)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { Result.success(it) } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Download failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDownloadMp3(url: String, type: String, platformId: String? = null): Result<DownloadTask> {
        return try {
            val request = DownloadRequest(url = url, type = type, platformId = platformId)
            val response = api.createDownloadMp3(request)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { Result.success(it) } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Download failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDownloadTask(id: String): Result<DownloadTask> {
        return try {
            val response = api.getDownloadTask(id)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { Result.success(it) } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Failed to fetch download")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getDownloads(page: Int = 1, limit: Int = 20): Result<List<DownloadTask>> {
        return try {
            val response = api.getDownloads(page, limit)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception(apiError(response.body(), "Failed to fetch downloads")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val request = LoginRequest(email, password)
            val response = api.login(request)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let {
                    ApiClient.setAuthToken(it.token)
                    Result.success(it)
                } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Login failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginGoogle(credential: String): Result<AuthResponse> {
        return try {
            val response = api.loginGoogle(mapOf("credential" to credential))
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let {
                    ApiClient.setAuthToken(it.token)
                    Result.success(it)
                } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Google login failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(request: Map<String, String>): Result<AuthResponse> {
        return try {
            val response = api.register(request)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let {
					ApiClient.setAuthToken(it.token)
                    Result.success(it)
                } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Registration failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(request: Map<String, String>): Result<Unit> {
        return try {
            val response = api.forgotPassword(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
				Result.failure(Exception(apiError(response.body(), "Failed to send reset link")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(request: Map<String, String>): Result<Unit> {
        return try {
            val response = api.resetPassword(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
				Result.failure(Exception(apiError(response.body(), "Failed to reset password")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("No data returned"))
            } else {
                Result.failure(Exception(apiError(response.body(), "Failed to get user")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCentrifugoToken(): Result<TokenResponse> {
        return try {
            val response = api.getCentrifugoToken()
            if (response.isSuccessful) {
                val token = response.body()
                if (token != null) {
                    Result.success(token)
                } else {
                    Result.failure(Exception("No data returned"))
                }
            } else {
                Result.failure(Exception("Failed to get centrifugo token"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val response = api.logout()
			if (response.isSuccessful && response.body()?.success == true) {
                ApiClient.setAuthToken(null)
                Result.success(Unit)
            } else {
				Result.failure(Exception(apiError(response.body(), "Logout failed")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
