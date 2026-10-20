package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    val error: String? = null,
    val isFallbackAvailable: Boolean = true
)

interface IPlayer {
    val playerState: StateFlow<PlayerState>
    fun play(url: String, title: String, startPositionMs: Long = 0L, requestHeaders: Map<String, String> = emptyMap())
    fun togglePlayPause()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun retry()
    fun playFallback(customTitle: String? = null)
    fun clearError()
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
    private var lastUrl: String = ""
    private var lastTitle: String = ""
    private var lastPositionMs: Long = 0L
    private var lastHeaders: Map<String, String> = emptyMap()
    private lateinit var httpDataSourceFactory: DefaultHttpDataSource.Factory

    companion object {
        const val DEFAULT_FALLBACK_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        const val STANDARD_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 NetStream/2.0"
    }

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(STANDARD_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "User-Agent" to STANDARD_USER_AGENT,
                    "Connection" to "keep-alive"
                )
            )

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playerState.value = _playerState.value.copy(
                                    isBuffering = true,
                                    error = null
                                )
                            }
                            Player.STATE_READY -> {
                                _playerState.value = _playerState.value.copy(
                                    isBuffering = false,
                                    durationMs = duration.coerceAtLeast(0L),
                                    isPlaying = isPlaying,
                                    error = null
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

                    override fun onPlayerError(error: PlaybackException) {
                        val cause = error.cause
                        val errorDesc = when {
                            cause is HttpDataSource.InvalidResponseCodeException -> {
                                when (cause.responseCode) {
                                    403 -> "Akses ditolak (HTTP 403 Forbidden). Server sumber video membatasi akses streaming langsung atau link membutuhkan izin khusus/token baru."
                                    404 -> "Video tidak ditemukan di server sumber (HTTP 404 Not Found)."
                                    500, 502, 503 -> "Server penyedia video sedang mengalami kendala (HTTP ${cause.responseCode})."
                                    else -> "Gagal memuat video dari server (Kode HTTP: ${cause.responseCode})."
                                }
                            }
                            cause is HttpDataSource.HttpDataSourceException -> {
                                "Koneksi ke server video terputus atau tidak stabil. Periksa koneksi internet Anda."
                            }
                            else -> {
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: "Terjadi kendala saat memproses pemutaran video."
                            }
                        }

                        _playerState.value = _playerState.value.copy(
                            isBuffering = false,
                            isPlaying = false,
                            error = errorDesc,
                            isFallbackAvailable = true
                        )
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

    override fun play(url: String, title: String, startPositionMs: Long, requestHeaders: Map<String, String>) {
        lastUrl = url
        lastTitle = title
        lastPositionMs = startPositionMs
        lastHeaders = requestHeaders

        exoPlayer?.let { player ->
            try {
                require(url.isNotBlank()) { "URL media kosong" }
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf("Accept" to "*/*", "User-Agent" to STANDARD_USER_AGENT) + requestHeaders
                )
                _playerState.value = _playerState.value.copy(
                    title = title,
                    error = null,
                    isBuffering = true
                )
                val mediaItem = ExoMediaItem.fromUri(url.trim())
                player.setMediaItem(mediaItem)
                if (startPositionMs > 0) {
                    player.seekTo(startPositionMs)
                }
                player.prepare()
                player.playWhenReady = true
            } catch (e: RuntimeException) {
                _playerState.value = _playerState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    error = "URL media tidak valid atau tidak didukung: ${e.localizedMessage ?: "periksa sumber video"}"
                )
            }
        }
    }

    override fun retry() {
        if (lastUrl.isNotEmpty()) {
            play(lastUrl, lastTitle, lastPositionMs, lastHeaders)
        }
    }

    override fun playFallback(customTitle: String?) {
        val title = customTitle ?: if (lastTitle.isNotEmpty()) "$lastTitle (Stream Cadangan)" else "Stream Cadangan Demo"
        play(DEFAULT_FALLBACK_URL, title, 0L)
    }

    override fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
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
