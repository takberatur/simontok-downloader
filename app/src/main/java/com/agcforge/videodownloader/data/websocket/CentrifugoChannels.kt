package com.agcforge.videodownloader.data.websocket

object CentrifugoChannels {
    // Public channels
    const val PUBLIC_DOWNLOADS = "public:downloads"
    const val PLATFORM_UPDATES = "platform:updates"

    fun userChannel(userId: String) = "user:$userId"
	fun downloadChannel(downloadId: String) = "download:progress:$downloadId"
    fun globalChannel() = "global"


}
