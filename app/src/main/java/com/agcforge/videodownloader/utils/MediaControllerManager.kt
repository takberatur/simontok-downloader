package com.agcforge.videodownloader.utils

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object MediaControllerManager {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    fun connect(context: Context, serviceClass: Class<*>) {
        val sessionToken = SessionToken(context, ComponentName(context, serviceClass))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        mediaControllerFuture?.addListener(
            {
                try {
                    mediaController = mediaControllerFuture?.get()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            android.os.AsyncTask.THREAD_POOL_EXECUTOR
        )
    }

    fun disconnect() {
        mediaController?.release()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        mediaControllerFuture = null
    }

    fun getController(): MediaController? = mediaController

    fun isConnected(): Boolean = mediaController?.isConnected == true
}