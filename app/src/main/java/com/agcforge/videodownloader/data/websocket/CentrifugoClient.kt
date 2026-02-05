package com.agcforge.videodownloader.data.websocket

import android.util.Log
import com.google.gson.Gson
import io.github.centrifugal.centrifuge.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

class CentrifugoClient(
    private val config: CentrifugoConfig,
    private val gson: Gson = Gson()
) {
    private val TAG = "CentrifugoClient"

    private var client: Client? = null
    private val subscriptions = mutableMapOf<String, Subscription>()

    private val _connectionState = MutableStateFlow<CentrifugoEvent>(CentrifugoEvent.Disconnected)
    val connectionState: StateFlow<CentrifugoEvent> = _connectionState

    private val _downloadTaskEvents = MutableStateFlow<DownloadTaskEvent?>(null)
    val downloadTaskEvents: StateFlow<DownloadTaskEvent?> = _downloadTaskEvents

    init {
        setupClient()
    }

    private fun setupClient() {
		Log.d(TAG, "Creating Centrifugo client, url=${config.url}")
        val options = Options().apply {
            // Set connection token if available
            config.token?.let { token = it }

            // Timeout settings
            setTimeout(10000) // 10 seconds

        }

		ensureWebSocketSubprotocol(options)

        client = Client(config.url, options, object : EventListener() {
            override fun onConnecting(client: Client, event: ConnectingEvent) {
                Log.d(TAG, "Connecting to Centrifugo...")
                _connectionState.value = CentrifugoEvent.Connecting
            }

            override fun onConnected(client: Client, event: ConnectedEvent) {
                Log.d(TAG, "Connected to Centrifugo! Client ID: ${event.client}")
                _connectionState.value = CentrifugoEvent.Connected
            }

            override fun onDisconnected(client: Client, event: DisconnectedEvent) {
                Log.d(TAG, "Disconnected from Centrifugo. Reason: ${event.reason}")
                _connectionState.value = CentrifugoEvent.Disconnected
            }

            override fun onError(client: Client, event: ErrorEvent) {
                Log.e(TAG, "Centrifugo error: ${event.error.message}", event.error)
                _connectionState.value = CentrifugoEvent.Error(
                    event.error.message ?: "Unknown error",
                    event.error
                )
            }

            override fun onMessage(client: Client, event: MessageEvent) {
                Log.d(TAG, "Message received: ${String(event.data)}")
                handleMessage(event.data)
            }
        })
    }

	private fun ensureWebSocketSubprotocol(options: Options) {
		runCatching {
			val protocolHeader = "Sec-WebSocket-Protocol"
			val protocolValue = "centrifuge-protobuf"

			val headersGetter = options.javaClass.methods.firstOrNull { m ->
				m.name == "getHeaders" && m.parameterTypes.isEmpty()
			}
			val headersSetter = options.javaClass.methods.firstOrNull { m ->
				m.name == "setHeaders" && m.parameterTypes.size == 1
			}
			val current = (headersGetter?.invoke(options) as? Map<*, *>)
				?.entries
				?.mapNotNull { (k, v) ->
					val key = k?.toString() ?: return@mapNotNull null
					val value = v?.toString() ?: return@mapNotNull null
					key to value
				}
				?.toMap()
				?: emptyMap()

			val merged = current.toMutableMap().apply { put(protocolHeader, protocolValue) }
			if (headersSetter != null) {
				headersSetter.invoke(options, merged)
				Log.d(TAG, "Set WebSocket subprotocol header: $protocolValue")
			}
		}.onFailure {
			Log.d(TAG, "Unable to set WebSocket subprotocol header")
		}
	}

    fun connect() {
        try {
            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            _connectionState.value = CentrifugoEvent.Error("Connection failed", e)
        }
    }

    fun disconnect() {
        try {
            // Unsubscribe from all channels
            subscriptions.values.forEach { it.unsubscribe() }
            subscriptions.clear()

            client?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect", e)
        }
    }

    fun subscribe(channel: String): Flow<CentrifugoEvent> = callbackFlow {
        try {
            Log.d(TAG, "Subscribing to channel: $channel")

            val subscription = client?.newSubscription(channel, object : SubscriptionEventListener() {
                override fun onSubscribed(
                    sub: Subscription?,
                    event: SubscribedEvent?
                ) {
                    super.onSubscribed(sub, event)
                    Log.d(TAG, "Subscribed successfully to: $channel")
                    trySend(CentrifugoEvent.SubscriptionSuccess(channel))
                }

                override fun onUnsubscribed(
                    sub: Subscription?,
                    event: UnsubscribedEvent?
                ) {
                    super.onUnsubscribed(sub, event)
                    Log.d(TAG, "Subscribed to: $channel")
                }

                override fun onSubscribing(
                    sub: Subscription?,
                    event: SubscribingEvent?
                ) {
                    super.onSubscribing(sub, event)
                    Log.d(TAG, "Unsubscribed from: $channel")
                }

                override fun onError(
                    sub: Subscription?,
                    event: SubscriptionErrorEvent?
                ) {
                    super.onError(sub, event)
                    if (event != null) {
                        Log.e(TAG, "Subscription error for $channel: ${event.error.message}")
                    }
                    if (event != null) {
                        trySend(CentrifugoEvent.SubscriptionError(
                            channel,
                            event.error.message ?: "Unknown error"
                        ))
                    }
                }

                override fun onLeave(
                    sub: Subscription?,
                    event: LeaveEvent?
                ) {
                    super.onLeave(sub, event)
                }

                override fun onJoin(
                    sub: Subscription?,
                    event: JoinEvent?
                ) {
                    super.onJoin(sub, event)
                }

                override fun onPublication(
                    sub: Subscription?,
                    event: PublicationEvent?
                ) {
                    super.onPublication(sub, event)
                    if (event != null) {
                        Log.d(TAG, "Publication received on $channel: ${String(event.data)}")
                    }
                    if (event != null) {
                        handleChannelMessage(channel, event.data)
                        trySend(CentrifugoEvent.MessageReceived(channel, event.data))
                    }
                }
            })

            subscription?.subscribe()
            subscriptions[channel] = subscription!!

        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to $channel", e)
            trySend(CentrifugoEvent.Error("Subscription failed", e))
        }

        awaitClose {
            subscriptions[channel]?.unsubscribe()
            subscriptions.remove(channel)
        }
    }

    fun unsubscribe(channel: String) {
        subscriptions[channel]?.let { subscription ->
            subscription.unsubscribe()
            subscriptions.remove(channel)
            Log.d(TAG, "Unsubscribed from: $channel")
        }
    }

    private fun handleMessage(data: ByteArray) {
        try {
            val message = String(data)
            // Parse and handle global messages
            Log.d(TAG, "Global message: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message", e)
        }
    }

    private fun handleChannelMessage(channel: String, data: ByteArray) {
        try {
            val message = String(data)
            Log.d(TAG, "Channel $channel message: $message")

            // Parse message based on channel
            when {
                channel.startsWith("user:") -> handleUserChannelMessage(message)
                channel.startsWith("download:") -> handleDownloadChannelMessage(message)
                channel == CentrifugoChannels.PUBLIC_DOWNLOADS -> handlePublicDownloadMessage(message)
                else -> Log.d(TAG, "Unhandled channel: $channel")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle channel message", e)
        }
    }

    private fun handleUserChannelMessage(message: String) {
        try {
            val data = gson.fromJson(message, CentrifugoData::class.java)

            when (data.event) {
                "download.created" -> {
                    val task = gson.fromJson(
                        gson.toJson(data.payload),
                        com.agcforge.videodownloader.data.model.DownloadTask::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.Created(task)
                }

                "download.updated" -> {
                    val task = gson.fromJson(
                        gson.toJson(data.payload),
                        com.agcforge.videodownloader.data.model.DownloadTask::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.Updated(task)
                }

                "download.completed" -> {
                    val task = gson.fromJson(
                        gson.toJson(data.payload),
                        com.agcforge.videodownloader.data.model.DownloadTask::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.Completed(task)
                }

                "download.status_changed" -> {
                    val update = gson.fromJson(
                        gson.toJson(data.payload),
                        DownloadStatusUpdate::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(
                        update.taskId,
                        update.status,
                        update.progress,
                        update.errorMessage
                    )
                }

                "download.progress" -> {
                    val progress = gson.fromJson(
                        gson.toJson(data.payload),
                        DownloadProgress::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.ProgressUpdate(
                        progress.taskId,
                        progress.progress,
                        progress.downloadedBytes,
                        progress.totalBytes
                    )
                }

                "download.failed" -> {
                    val update = gson.fromJson(
                        gson.toJson(data.payload),
                        DownloadStatusUpdate::class.java
                    )
                    _downloadTaskEvents.value = DownloadTaskEvent.Failed(
                        update.taskId,
                        update.errorMessage ?: "Download failed"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user channel message", e)
        }
    }

    private fun handleDownloadChannelMessage(message: String) {
        try {
			val backend = gson.fromJson(message, BackendDownloadEvent::class.java)
			val taskId = backend.taskId ?: backend.payload?.id
			val status = backend.status ?: backend.payload?.status
			val progress = backend.progress ?: backend.payload?.progress
			val err = backend.error
				?: backend.message
				?: backend.payload?.let { null }

			if (!taskId.isNullOrBlank() && !status.isNullOrBlank()) {
				when (status) {
					"processing" -> {
						val p = progress ?: 0
						_downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(taskId, status, p, null)
						_downloadTaskEvents.value = DownloadTaskEvent.ProgressUpdate(taskId, p, 0L, 0L)
					}
					"queued", "pending" -> {
						_downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(taskId, status, progress, null)
					}
					"completed" -> {
						_downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(taskId, status, 100, null)
					}
					"failed" -> {
						_downloadTaskEvents.value = DownloadTaskEvent.Failed(taskId, err ?: "Download failed")
					}
					else -> {
						_downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(taskId, status, progress, err)
					}
				}
				return
			}

			val data = gson.fromJson(message, CentrifugoData::class.java)
			when (data.event) {
				"progress" -> {
					val progressData = gson.fromJson(
						gson.toJson(data.payload),
						DownloadProgress::class.java
					)
					_downloadTaskEvents.value = DownloadTaskEvent.ProgressUpdate(
						progressData.taskId,
						progressData.progress,
						progressData.downloadedBytes,
						progressData.totalBytes
					)
				}

				"status" -> {
					val update = gson.fromJson(
						gson.toJson(data.payload),
						DownloadStatusUpdate::class.java
					)
					_downloadTaskEvents.value = DownloadTaskEvent.StatusChanged(
						update.taskId,
						update.status,
						update.progress,
						update.errorMessage
					)
				}
			}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse download channel message", e)
        }
    }

    private fun handlePublicDownloadMessage(message: String) {
        try {
            val data = gson.fromJson(message, CentrifugoData::class.java)
            Log.d(TAG, "Public download event: ${data.event}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse public download message", e)
        }
    }

    fun isConnected(): Boolean {
        return _connectionState.value is CentrifugoEvent.Connected
    }

    fun getSubscribedChannels(): List<String> {
        return subscriptions.keys.toList()
    }
}
