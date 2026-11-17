package com.example.player

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
        val player = PlayerRuntime.get(applicationContext).exoPlayer ?: run {
            stopSelf()
            return
        }
        mediaSession = MediaSession.Builder(this, player)
            .setId("nsio-playback")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
