package com.agcforge.videodownloader.data.api

import com.agcforge.videodownloader.data.dto.ApiResponse
import com.agcforge.videodownloader.data.dto.AuthResponse
import com.agcforge.videodownloader.data.dto.CsrfResponse
import com.agcforge.videodownloader.data.dto.DownloadRequest
import com.agcforge.videodownloader.data.dto.LoginRequest
import com.agcforge.videodownloader.data.dto.TokenResponse
import com.agcforge.videodownloader.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("token/csrf")
    suspend fun getCsrfToken(): Response<ApiResponse<CsrfResponse>>

    @GET("mobile-client/application")
    suspend fun getApplication(): Response<ApiResponse<Application>>

    @GET("mobile-client/platforms")
    suspend fun getPlatforms(): Response<ApiResponse<List<Platform>>>

    @GET("mobile-client/platforms/{id}")
    suspend fun getPlatform(@Path("id") id: String): Response<ApiResponse<Platform>>

    @POST("mobile-client/download/process/video")
    suspend fun createDownloadVideo(@Body request: DownloadRequest): Response<ApiResponse<DownloadTask>>

    @POST("mobile-client/download/process/mp3")
    suspend fun createDownloadMp3(@Body request: DownloadRequest): Response<ApiResponse<DownloadTask>>

	@GET("mobile-client/downloads/{id}")
    suspend fun getDownloadTask(@Path("id") id: String): Response<ApiResponse<DownloadTask>>


    @GET("mobile-client/protected-mobile/downloads")
    suspend fun getDownloads(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<DownloadTask>>>

    @POST("mobile-client/auth/email")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("mobile-client/auth/google")
    suspend fun loginGoogle(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>

    @POST("mobile-client/auth/forgot-password")
    suspend fun forgotPassword(@Body request: Map<String, String>): Response<ApiResponse<Any>>

    @POST("mobile-client/auth/reset-password")
    suspend fun resetPassword(@Body request: Map<String, String>): Response<ApiResponse<Any>>

    @POST("mobile-client/auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>

    @GET("mobile-client/protected-mobile/users/current")
    suspend fun getCurrentUser(): Response<ApiResponse<User>>

    @POST("mobile-client/protected-mobile/auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>

    @GET("mobile-client/centrifugo/token")
    suspend fun getCentrifugoToken(): Response<TokenResponse>
}

