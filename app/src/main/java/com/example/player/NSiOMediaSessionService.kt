package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Exposes NSiO's shared ExoPlayer to Android system media controls, lock screen,
 * Bluetooth controls and the media notification while playback is in background.
 */
class NSiOMediaSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // A service launched with startForegroundService() must be promoted immediately.
        // Do this before touching PlayerRuntime/MediaSession: creating the shared player
        // or session can take long enough to exceed Android's foreground-service deadline.
        ensurePlaybackNotificationChannel()
        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NSiO")
            .setContentText("Menyiapkan pemutaran media")
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                STARTUP_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(STARTUP_NOTIFICATION_ID, notification)
        }

        val player = PlayerRuntime.get(applicationContext).exoPlayer ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        mediaSession = MediaSession.Builder(this, player)
            .setId("nsio-playback")
            .build()
    }

    private fun ensurePlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(PLAYBACK_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        PLAYBACK_CHANNEL_ID,
                        "Pemutaran media",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Kontrol pemutaran media NSiO"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val PLAYBACK_CHANNEL_ID = "nsio_media_playback"
        const val STARTUP_NOTIFICATION_ID = 1001
    }
}
