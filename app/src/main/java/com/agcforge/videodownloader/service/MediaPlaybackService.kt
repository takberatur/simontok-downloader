package com.agcforge.videodownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.ui.activities.player.VideoPlayerActivity

@UnstableApi
class MediaPlaybackService : MediaSessionService() {

    lateinit var player: ExoPlayer
        private set

    private lateinit var mediaSession: MediaSession

    private lateinit var notificationManager: PlayerNotificationManager

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setId("media_playback_session")
            .build()

        createNotificationChannel()

        notificationManager =
            PlayerNotificationManager.Builder(
                this,
                1001,
                "media_playback"
            )
                .setMediaDescriptionAdapter(descriptionAdapter)
                .setSmallIconResourceId(R.drawable.ic_notifications)
                .build()

        notificationManager.setPlayer(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_playback",
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private val descriptionAdapter =
        object : PlayerNotificationManager.MediaDescriptionAdapter {

            override fun getCurrentContentTitle(player: Player): CharSequence {
                return player.mediaMetadata.title ?: "Playing media"
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return player.mediaMetadata.artist ?: "AGCForge Player"
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                return BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_audiotrack
                )
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = Intent(this@MediaPlaybackService, VideoPlayerActivity::class.java)
                return PendingIntent.getActivity(
                    this@MediaPlaybackService,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
}