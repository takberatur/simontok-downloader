package com.agcforge.videodownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.websocket.CentrifugoManager
import com.agcforge.videodownloader.data.websocket.DownloadTaskEvent
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.Resource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoDownloaderRepository()
    private val centrifugoManager = CentrifugoManager.getInstance(application)
    private val preferenceManager = PreferenceManager(application)

    private val _downloads = MutableStateFlow<Resource<List<DownloadTask>>>(Resource.Loading())
    val downloads: StateFlow<Resource<List<DownloadTask>>> = _downloads.asStateFlow()

    private val _downloadDetail = MutableStateFlow<Resource<DownloadTask>>(Resource.Loading())
    val downloadDetail: StateFlow<Resource<DownloadTask>> = _downloadDetail.asStateFlow()

    // Map untuk tracking progress per download ID
    private val downloadProgress = mutableMapOf<String, Int>()

    // Real-time download updates
    private val _realtimeDownloadUpdate = MutableStateFlow<DownloadTaskEvent?>(null)
    val realtimeDownloadUpdate: StateFlow<DownloadTaskEvent?> = _realtimeDownloadUpdate.asStateFlow()

    init {
        observeRealtimeUpdates()
    }

    private fun observeRealtimeUpdates() {
        viewModelScope.launch {
            centrifugoManager.downloadEvents.collect { event ->
                event?.let { handleRealtimeEvent(it) }
            }
        }
    }

    private fun handleRealtimeEvent(event: DownloadTaskEvent) {
        _realtimeDownloadUpdate.value = event

        when (event) {
            is DownloadTaskEvent.Created,
            is DownloadTaskEvent.Updated,
            is DownloadTaskEvent.Completed -> {
                // Refresh download list when task is created, updated, or completed
                refreshDownloads()
            }
            is DownloadTaskEvent.ProgressUpdate -> {
                // Update progress in map
                downloadProgress[event.taskId] = event.progress
            }
            is DownloadTaskEvent.StatusChanged -> {
                // Refresh to get latest status
                refreshDownloads()
            }
            else -> {}
        }
    }

    fun loadDownloads(page: Int = 1, limit: Int = 20) {
        viewModelScope.launch {
            _downloads.value = Resource.Loading()

			val token = preferenceManager.authToken.first()
			if (token.isNullOrEmpty()) {
				_downloads.value = Resource.Error("Login diperlukan untuk melihat riwayat")
				return@launch
			}

            repository.getDownloads(page, limit)
                .onSuccess { downloadList ->
                    _downloads.value = Resource.Success(downloadList)

                    // Subscribe to download channels untuk setiap download
                    downloadList.forEach { task ->
                        if (task.status == "processing" || task.status == "pending") {
                            centrifugoManager.subscribeToDownloadChannel(task.id)
                        }
                    }
                }
                .onFailure { error ->
                    _downloads.value = Resource.Error(error.message ?: "Failed to load downloads")
                }
        }
    }

    fun getDownload(id: String) {
        viewModelScope.launch {
            _downloadDetail.value = Resource.Loading()

			repository.getDownloadTask(id)
                .onSuccess { task ->
                    _downloadDetail.value = Resource.Success(task)

                    // Subscribe untuk real-time updates
                    if (task.status == "processing" || task.status == "pending") {
                        centrifugoManager.subscribeToDownloadChannel(task.id)
                    }
                }
                .onFailure { error ->
                    _downloadDetail.value = Resource.Error(error.message ?: "Failed to load download")
                }
        }
    }

    fun refreshDownloads() {
        loadDownloads()
    }

    fun getDownloadProgress(taskId: String): Int {
        return downloadProgress[taskId] ?: 0
    }

    fun unsubscribeFromDownload(downloadId: String) {
        centrifugoManager.unsubscribeFromDownloadChannel(downloadId)
        downloadProgress.remove(downloadId)
    }

    override fun onCleared() {
        super.onCleared()
        // Unsubscribe dari semua download channels
        downloadProgress.keys.forEach { downloadId ->
            centrifugoManager.unsubscribeFromDownloadChannel(downloadId)
        }
        downloadProgress.clear()
    }
}
