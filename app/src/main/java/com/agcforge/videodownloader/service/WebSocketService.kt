package com.agcforge.videodownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.api.ApiClient
import com.agcforge.videodownloader.data.api.VideoDownloaderRepository
import com.agcforge.videodownloader.data.websocket.CentrifugoEvent
import com.agcforge.videodownloader.data.websocket.CentrifugoManager
import com.agcforge.videodownloader.data.websocket.DownloadTaskEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WebSocketService : Service() {

    private val TAG = "WebSocketService"
    private val binder = WebSocketBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var centrifugoManager: CentrifugoManager
    private lateinit var notificationManager: NotificationManager
    private val repository by lazy { VideoDownloaderRepository() }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "websocket_service_channel"

        fun start(context: Context, userId: String, token: String? = null) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra("user_id", userId)
                putExtra("token", token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebSocketService::class.java))
        }
    }

    inner class WebSocketBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        centrifugoManager = CentrifugoManager.Companion.getInstance(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("WebSocket service running"))

        observeWebSocketEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getStringExtra("user_id")
        val token = intent?.getStringExtra("token")

        if (userId != null) {
			if (!token.isNullOrEmpty()) {
				ApiClient.setAuthToken(token)
			}
			scope.launch {
				val wsToken = if (!token.isNullOrEmpty()) {
					repository.getCentrifugoToken().getOrNull()?.token
				} else {
					null
				}
				centrifugoManager.initialize(userId, wsToken)
				centrifugoManager.connect()
			}
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun observeWebSocketEvents() {
        // Observe connection state
        scope.launch {
            centrifugoManager.connectionState.collect { event ->
                when (event) {
                    is CentrifugoEvent.Connected -> {
                        Log.d(TAG, "WebSocket connected")
                        updateNotification("WebSocket connected")
                    }
                    is CentrifugoEvent.Disconnected -> {
                        Log.d(TAG, "WebSocket disconnected")
                        updateNotification("WebSocket disconnected")
                    }
                    is CentrifugoEvent.Connecting -> {
                        Log.d(TAG, "WebSocket connecting...")
                        updateNotification("Connecting...")
                    }
                    is CentrifugoEvent.Error -> {
                        Log.e(TAG, "WebSocket error: ${event.message}")
                        updateNotification("Connection error")
                    }
                    else -> {}
                }
            }
        }

        // Observe download events
        scope.launch {
            centrifugoManager.downloadEvents.collect { event ->
                event?.let { handleDownloadEvent(it) }
            }
        }
    }

    private fun handleDownloadEvent(event: DownloadTaskEvent) {
        when (event) {
            is DownloadTaskEvent.Created -> {
                Log.d(TAG, "Download created: ${event.task.title}")
                showDownloadNotification("Download created", event.task.title ?: "New download")
            }
            is DownloadTaskEvent.ProgressUpdate -> {
                Log.d(TAG, "Download progress: ${event.progress}%")
            }
            is DownloadTaskEvent.Completed -> {
                Log.d(TAG, "Download completed: ${event.task.title}")
                showDownloadNotification("Download completed", event.task.title ?: "Download finished")
            }
            is DownloadTaskEvent.Failed -> {
                Log.e(TAG, "Download failed: ${event.error}")
                showDownloadNotification("Download failed", event.error)
            }
            else -> {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebSocket connection service"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(this.getString(R.string.app_name))
        .setContentText(message)
        .setSmallIcon(R.drawable.ic_download)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun updateNotification(message: String) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun showDownloadNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_download)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        centrifugoManager.disconnect()
    }

    fun getCentrifugoManager(): CentrifugoManager = centrifugoManager
}
