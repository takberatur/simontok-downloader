package com.agcforge.videodownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agcforge.videodownloader.data.api.ApiClient
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.model.Platform
import com.agcforge.videodownloader.data.websocket.CentrifugoEvent
import com.agcforge.videodownloader.data.websocket.CentrifugoManager
import com.agcforge.videodownloader.data.websocket.DownloadTaskEvent
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.Resource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy { VideoDownloaderRepository() }
    private val centrifugoManager = CentrifugoManager.getInstance(application)
    private val preferenceManager = PreferenceManager(application)

    private val _platforms = MutableStateFlow<Resource<List<Platform>>>(Resource.Loading())
    val platforms: StateFlow<Resource<List<Platform>>> = _platforms.asStateFlow()

    private val _downloadResult = MutableStateFlow<Resource<DownloadTask>>(Resource.Idle())
    val downloadResult: StateFlow<Resource<DownloadTask>> = _downloadResult.asStateFlow()

    // WebSocket connection state
    private val _wsConnectionState = MutableStateFlow<CentrifugoEvent>(CentrifugoEvent.Disconnected)
    val wsConnectionState: StateFlow<CentrifugoEvent> = _wsConnectionState.asStateFlow()

	val realtimeDownloadEvent: SharedFlow<DownloadTaskEvent> = centrifugoManager.downloadEvents

    init {
        observeWebSocketEvents()
        initializeWebSocket()
    }

    private fun initializeWebSocket() {
        viewModelScope.launch {
			preferenceManager.userId
				.combine(preferenceManager.authToken) { userId, token ->
					Pair(userId, token)
				}
				.collect { (userId, token) ->
					if (!userId.isNullOrEmpty() && !token.isNullOrEmpty()) {
						ApiClient.setAuthToken(token)
						val centrifugoToken = repository.getCentrifugoToken().getOrNull()?.token
						centrifugoManager.initialize(userId, centrifugoToken)
						centrifugoManager.connect()
					}
				}
        }
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            centrifugoManager.connectionState.collect { state ->
                _wsConnectionState.value = state
            }
        }

		// Events are exposed directly from CentrifugoManager.downloadEvents
    }

    fun loadPlatforms() {
        viewModelScope.launch {
            _platforms.value = Resource.Loading()

            repository.getPlatforms()
                .onSuccess { platformList ->
                    _platforms.value = Resource.Success(platformList)
                }
                .onFailure { error ->
                    _platforms.value = Resource.Error(error.message ?: "Failed to load platforms")
                }
        }
    }

    fun createDownload(url: String, type: String) {
        viewModelScope.launch {
            _downloadResult.value = Resource.Loading()

			val isMp3 = type.lowercase().endsWith("-to-mp3")
			val result = if (isMp3) {
				repository.createDownloadMp3(url, type)
			} else {
				repository.createDownloadVideo(url, type)
			}

			result
                .onSuccess { task ->
                    _downloadResult.value = Resource.Success(task)
                    preferenceManager.addToHistory(task)
                    // Subscribe to download channel for real-time updates
                    centrifugoManager.subscribeToDownloadChannel(task.id)
                }
                .onFailure { error ->
                    _downloadResult.value = Resource.Error(error.message ?: "Download failed")
                }
        }
    }

	fun clearDownloadResult() {
		_downloadResult.value = Resource.Idle()
	}

    fun connectWebSocket() {
        centrifugoManager.connect()
    }

    fun disconnectWebSocket() {
        centrifugoManager.disconnect()
    }

    fun subscribeToPublicDownloads() {
        centrifugoManager.subscribeToPublicDownloads()
    }

	suspend fun getDownloadTask(id: String): Result<DownloadTask> {
		return repository.getDownloadTask(id)
	}
}


