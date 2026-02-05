package com.agcforge.videodownloader.data.websocket

import android.content.Context
import android.util.Log
import com.agcforge.videodownloader.utils.AppManager
import com.agcforge.videodownloader.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class CentrifugoManager private constructor(
    private val context: Context
) {
    private val TAG = "CentrifugoManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: CentrifugoClient? = null
    private val preferenceManager = PreferenceManager(context)

    private val _connectionState = MutableStateFlow<CentrifugoEvent>(CentrifugoEvent.Disconnected)
    val connectionState: StateFlow<CentrifugoEvent> = _connectionState.asStateFlow()

	private val _downloadEvents = MutableSharedFlow<DownloadTaskEvent>(extraBufferCapacity = 64)
	val downloadEvents: SharedFlow<DownloadTaskEvent> = _downloadEvents.asSharedFlow()

    private var currentUserId: String? = null
    private var authToken: String? = null

    companion object {
        private val CENTRIFUGO_URL = AppManager.centrifugoUrl

        @Volatile
        private var instance: CentrifugoManager? = null

        fun getInstance(context: Context): CentrifugoManager {
            return instance ?: synchronized(this) {
                instance ?: CentrifugoManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

	private fun normalizeWebSocketUrl(url: String): String {
		val trimmed = url.trim()
		val lower = trimmed.lowercase(Locale.ROOT)
		return when {
			lower.startsWith("wss://") -> trimmed
			lower.startsWith("ws://") -> trimmed
			lower.startsWith("https://") -> "wss://${trimmed.substring(8)}"
			lower.startsWith("http://") -> "ws://${trimmed.substring(7)}"
			else -> trimmed
		}
	}

    fun initialize(userId: String, token: String? = null) {
        if (client != null && currentUserId == userId) {
            Log.d(TAG, "Already initialized for user: $userId")
            return
        }

        disconnect()

        currentUserId = userId
        authToken = token

        val normalizedUrl = normalizeWebSocketUrl(CENTRIFUGO_URL)
		Log.d(TAG, "Centrifugo URL raw=$CENTRIFUGO_URL normalized=$normalizedUrl")

        val config = CentrifugoConfig(
            url = normalizedUrl,
            token = token,
            userId = userId
        )

        client = CentrifugoClient(config).apply {
            // Collect connection state
            scope.launch {
                connectionState.collect { state ->
                    _connectionState.value = state

                    // Auto-subscribe to user channel when connected
                    if (state is CentrifugoEvent.Connected) {
                        subscribeToUserChannel(userId)
                    }
                }
            }

            // Collect download events
            scope.launch {
                downloadTaskEvents.collect { event ->
					event?.let { _downloadEvents.tryEmit(it) }
                }
            }
        }

        Log.d(TAG, "Centrifugo client initialized for user: $userId")
    }

    fun connect() {
        if (currentUserId == null) {
            Log.w(TAG, "Cannot connect: not initialized. Call initialize() first")
            return
        }

        client?.connect()
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        currentUserId = null
        authToken = null
    }

    fun subscribeToUserChannel(userId: String) {
        val channel = CentrifugoChannels.userChannel(userId)
        subscribeToChannel(channel)
    }

    fun subscribeToDownloadChannel(downloadId: String) {
        val channel = CentrifugoChannels.downloadChannel(downloadId)
        subscribeToChannel(channel)
    }

    fun subscribeToPublicDownloads() {
        subscribeToChannel(CentrifugoChannels.PUBLIC_DOWNLOADS)
    }

    private fun subscribeToChannel(channel: String) {
        scope.launch {
            client?.subscribe(channel)?.collect { event ->
                Log.d(TAG, "Channel event: $event")
                when (event) {
                    is CentrifugoEvent.SubscriptionSuccess -> {
                        Log.d(TAG, "Successfully subscribed to: $channel")
                    }
                    is CentrifugoEvent.SubscriptionError -> {
                        Log.e(TAG, "Failed to subscribe to $channel: ${event.error}")
                    }
                    else -> {
                        // Handle other events
                    }
                }
            }
        }
    }

    fun unsubscribeFromChannel(channel: String) {
        client?.unsubscribe(channel)
    }

    fun unsubscribeFromDownloadChannel(downloadId: String) {
        val channel = CentrifugoChannels.downloadChannel(downloadId)
        unsubscribeFromChannel(channel)
    }

    fun isConnected(): Boolean {
        return client?.isConnected() ?: false
    }

    fun getSubscribedChannels(): List<String> {
        return client?.getSubscribedChannels() ?: emptyList()
    }

    fun reconnect() {
        disconnect()
        currentUserId?.let { userId ->
            initialize(userId, authToken)
            connect()
        }
    }
}
