package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val title: String = "",
    val error: String? = null
)

interface IPlayer {
    val playerState: StateFlow<PlayerState>
    fun play(url: String, title: String, startPositionMs: Long = 0L)
    fun togglePlayPause()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun release()
}

@OptIn(UnstableApi::class)
class CS3IPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : IPlayer {

    var exoPlayer: ExoPlayer? = null
        private set

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _playerState.value = _playerState.value.copy(isBuffering = true)
                        }
                        Player.STATE_READY -> {
                            _playerState.value = _playerState.value.copy(
                                isBuffering = false,
                                durationMs = duration.coerceAtLeast(0L),
                                isPlaying = isPlaying
                            )
                        }
                        Player.STATE_ENDED -> {
                            _playerState.value = _playerState.value.copy(
                                isPlaying = false,
                                currentPositionMs = duration
                            )
                        }
                        Player.STATE_IDLE -> {
                            _playerState.value = _playerState.value.copy(isBuffering = false)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                }
            })
        }
        startProgressTracking()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    override fun play(url: String, title: String, startPositionMs: Long) {
        exoPlayer?.let { player ->
            _playerState.value = _playerState.value.copy(
                title = title,
                error = null,
                isBuffering = true
            )
            val mediaItem = ExoMediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
            }
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    override fun seekForward(offsetMs: Long) {
        exoPlayer?.let { player ->
            val target = (player.currentPosition + offsetMs).coerceAtMost(player.duration)
            player.seekTo(target)
        }
    }

    override fun seekBackward(offsetMs: Long) {
        exoPlayer?.let { player ->
            val target = (player.currentPosition - offsetMs).coerceAtLeast(0L)
            player.seekTo(target)
        }
    }

    override fun release() {
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
