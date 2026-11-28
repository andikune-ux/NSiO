package com.example.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.lagradost.cloudstream3.app
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.DrmSessionManager
import java.util.UUID
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException

data class PlayerTrackOption(
    val id: String,
    val label: String,
    val trackType: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val selected: Boolean,
    val language: String? = null
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isSeekable: Boolean = false,
    val title: String = "",
    val error: String? = null,
    val isFallbackAvailable: Boolean = true,
    val playbackSpeed: Float = 1f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1f,
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val videoTracks: List<PlayerTrackOption> = emptyList(),
    val subtitlesDisabled: Boolean = true,
    val pluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList(),
    val selectedPluginStreamUrl: String? = null,
    val audioDelayMs: Int = 0,
    val subtitleDelayMs: Int = 0,
    val repeatA: Long? = null,
    val repeatB: Long? = null,
    val audioCompressorEnabled: Boolean = false,
    val equalizerGainsDb: List<Float> = List(10) { 0f },
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val isLive: Boolean = false,
    val playbackDiagnostics: List<String> = emptyList(),
    val externalAudioName: String? = null
)

interface IPlayer {
    val playerState: StateFlow<PlayerState>
    fun play(url: String, title: String, startPositionMs: Long = 0L, requestHeaders: Map<String, String> = emptyMap(), mediaCacheKey: String? = null)
    fun preloadDriveMedia(url: String, mediaId: String)
    fun cancelDrivePreload()
    /** Enables repeat-one only while TikTod owns the player. */
    fun setTikTodLoopEnabled(enabled: Boolean)
    fun togglePlayPause()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekToLive()
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun setPlaybackSpeed(speed: Float)
    fun setAudioDelay(delayMs: Int)
    fun setSubtitleDelay(delayMs: Int)
    fun setRepeatPointA()
    fun setRepeatPointB()
    fun clearABRepeat()
    fun setAudioCompressorEnabled(enabled: Boolean)
    fun setEqualizerBandGain(index: Int, gainDb: Float)
    fun resetEqualizer()
    fun setPreferredAudioLanguage(language: String?)
    fun setExternalAudioTrack(uri: Uri, displayName: String)
    fun clearExternalAudioTrack()
    fun setPreferredSubtitleLanguage(language: String?)
    fun toggleRepeatMode()
    fun toggleShuffle()
    fun setVolume(volume: Float)
    fun toggleMute()
    fun selectPluginStream(stream: com.example.data.plugin.CloudStreamStream)
    fun retry()
    fun playFallback(customTitle: String? = null)
    fun clearError()
    fun release()
}

/**
 * Resolve common CloudStream subtitle MIME aliases without relying on Android Uri,
 * so the mapping stays deterministic and can be covered by local unit tests.
 */
internal fun resolveSubtitleMimeType(url: String, declaredMimeType: String?): String? {
    val extension = url.substringBefore('#').substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "")
        .lowercase()
    val declaredMime = declaredMimeType?.substringBefore(';')?.trim()?.lowercase()
    return when (declaredMime) {
        "text/vtt" -> "text/vtt"
        "application/x-subrip", "text/srt", "application/srt" -> "application/x-subrip"
        "application/ttml+xml" -> "application/ttml+xml"
        "text/x-ssa", "text/x-ass", "application/x-ssa", "application/x-ass" -> "text/x-ssa"
        else -> when (extension) {
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "ttml", "xml" -> "application/ttml+xml"
            "ass", "ssa" -> "text/x-ssa"
            else -> null
        }
    }
}

private fun resolveExternalAudioMimeType(displayName: String): String? = when (displayName.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "m4a", "mp4" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg", "oga" -> "audio/ogg"
    "opus" -> "audio/opus"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "weba", "webm" -> "audio/webm"
    else -> null
}

@OptIn(UnstableApi::class)
class CS3IPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val manageMediaSessionService: Boolean = true
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
    private var lastMimeType: String? = null
    private var lastExternalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList()
    private val userExternalSubtitles = mutableListOf<com.example.data.plugin.CloudStreamSubtitle>()
    private var activeDrmSessionManager: DrmSessionManager? = null
    private var lastVideoInterceptor: okhttp3.Interceptor? = null
    private var lastPluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList()
    private var externalAudioUri: Uri? = null
    private var externalAudioName: String? = null
    private val attemptedPluginStreamKeys = mutableSetOf<String>()
    private var bufferingStartedAtMs: Long = 0L
    private var bufferingFallbackRequestedForUrl: String? = null
    @Volatile private var cloudStreamRefreshInProgress = false

    /** True only while the active item belongs to CloudStream; other sources must never invoke its resolver. */
    var isCloudStreamSourceActive: (() -> Boolean)? = null

    /** Called when resolved plugin URLs fail with an expired-link HTTP response. */
    var onCloudStreamRefreshRequested: (suspend (Long) -> Boolean)? = null
    private var lastNonZeroVolume: Float = 1f
    private var audioDelayMs: Int = 0
    private var subtitleDelayMs: Int = 0
    private var repeatPointA: Long? = null
    private var repeatPointB: Long? = null
    private var audioCompressorEnabled: Boolean = false
    private var equalizerGainsDb: FloatArray = FloatArray(10)
    private lateinit var equalizerProcessor: TenBandEqualizerProcessor
    private var logicalRepeatMode: Int = Player.REPEAT_MODE_OFF
    @Volatile private var tikTodLoopEnabled: Boolean = false
    private var logicalShuffleEnabled: Boolean = false
    private lateinit var httpDataSourceFactory: HttpDataSource.Factory
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private var activeMediaCacheKey: String? = null
    private var lastMediaCacheKey: String? = null
    private var drivePreloadJob: Job? = null

    companion object {
        private val CLEARKEY_UUID: UUID = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")
        const val DEFAULT_FALLBACK_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        const val DRIVE_PRELOAD_BYTES = 2L * 1024L * 1024L
        const val STANDARD_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 NetStream/2.0"
    }

    init {
        // Keep audio synchronization preference across app launches and player recreation.
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        audioDelayMs = preferences.getInt("audio_delay_ms", 0).coerceIn(-2000, 2000)
        subtitleDelayMs = preferences.getInt("subtitle_delay_ms", 0).coerceIn(-5000, 5000)
        audioCompressorEnabled = preferences.getBoolean("audio_compressor_enabled", false)
        equalizerGainsDb = preferences.getString("equalizer_gains_db", null)
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }
            ?.takeIf { it.size == 10 }
            ?.map { it.coerceIn(-12f, 12f) }
            ?.toFloatArray()
            ?: FloatArray(10)
        _playerState.value = _playerState.value.copy(
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
            audioCompressorEnabled = audioCompressorEnabled,
            equalizerGainsDb = equalizerGainsDb.toList(),
            preferredAudioLanguage = preferences.getString("preferred_audio_language", null),
            preferredSubtitleLanguage = preferences.getString("preferred_subtitle_language", null)
        )
        logicalRepeatMode = preferences.getInt("playback_repeat_mode", Player.REPEAT_MODE_OFF)
            .takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL }
            ?: Player.REPEAT_MODE_OFF
        logicalShuffleEnabled = preferences.getBoolean("playback_shuffle_enabled", false)
        initExoPlayer()
    }

    private fun createHttpDataSourceFactory(
        interceptor: okhttp3.Interceptor?,
        subtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        additionalHeaders: Map<String, String> = emptyMap()
    ): HttpDataSource.Factory {
        val baseHeaders = mapOf(
            "Accept" to "*/*",
            "User-Agent" to STANDARD_USER_AGENT,
            "Connection" to "keep-alive"
        )
        val subtitleHeaderMap = subtitles
            .filter { it.url.isNotBlank() && it.headers.isNotEmpty() }
            .associate { normalizePlaybackUrl(it.url) to it.headers }

        // Match CloudStream's own player networking: reuse its shared OkHttp client
        // so provider cookies, cookie-jar state, TLS configuration and other session
        // settings survive from plugin resolution into the actual media requests.
        // Creating a fresh OkHttpClient here silently drops that shared session.
        val client = app.baseClient.newBuilder().apply {
            interceptor?.let(::addInterceptor)
            // Google Drive may answer a public-file download with an HTML interstitial
            // containing a one-time confirmation token. Resolve it before Media3 sees it.
            addInterceptor(GoogleDriveDownloadInterceptor())
            if (subtitleHeaderMap.isNotEmpty()) {
                addInterceptor { chain ->
                    val request = chain.request()
                    val headers = subtitleHeaderMap.entries.firstOrNull { (url, _) ->
                        runCatching { normalizePlaybackUrl(request.url.toString()) == url }.getOrDefault(false)
                    }?.value
                    val updated = request.newBuilder().apply {
                        headers?.forEach { (name, value) -> header(name, value) }
                    }.build()
                    chain.proceed(updated)
                }
            }
        }.build()

        // CloudStream uses OkHttpDataSource for online playback even when a plugin
        // does not supply a custom video interceptor. Keep the same behavior here.
        val requestProperties = baseHeaders.toMutableMap().apply {
            additionalHeaders.forEach { (name, value) ->
                keys.filter { it.equals(name, ignoreCase = true) }.forEach(::remove)
                put(name, value)
            }
        }
        return OkHttpDataSource.Factory(client)
            .setUserAgent(STANDARD_USER_AGENT)
            .setDefaultRequestProperties(requestProperties)
    }

    private fun ensureMediaSessionServiceStarted() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NSiOMediaSessionService::class.java)
            )
        } catch (error: Exception) {
            // Playback in the foreground remains available if Android rejects a
            // background-service start; the failure is visible in logcat.
            Log.w("NSiOPlayer", "Unable to start media playback service", error)
        }
    }

    private fun initExoPlayer() {
        httpDataSourceFactory = createHttpDataSourceFactory(null)
        val dataSourceFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val upstreamFactory: DataSource.Factory =
                    if (activeMediaCacheKey?.startsWith("telegram:") == true) {
                        TelegramTdlibDataSource.Factory()
                    } else {
                        DefaultDataSource.Factory(context, httpDataSourceFactory)
                    }
                // Drive and Telegram share one Media3 SimpleCache and its 1 GiB LRU
                // limit. Telegram bytes arrive from TDLib in small ranges on demand.
                return if (activeMediaCacheKey != null) {
                    DriveMediaCache.track(
                        CacheDataSource.Factory()
                            .setCache(DriveMediaCache.get(context))
                            .setUpstreamDataSourceFactory(upstreamFactory)
                            .setCacheKeyFactory { dataSpec ->
                                val activeKey = activeMediaCacheKey
                                val path = dataSpec.uri.encodedPath.orEmpty().ifBlank { "/" }
                                when {
                                    activeKey == null -> dataSpec.key ?: dataSpec.uri.toString()
                                    activeKey.startsWith("media:") || activeKey.startsWith("url:") -> activeKey + ":" + path
                                    dataSpec.key != null -> dataSpec.key!!
                                    else -> activeKey
                                }
                            }
                            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                            .createDataSource(),
                        context
                    )
                } else upstreamFactory.createDataSource()
            }
        }
        mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider {
                activeDrmSessionManager ?: DrmSessionManager.DRM_UNSUPPORTED
            }

        equalizerProcessor = TenBandEqualizerProcessor(equalizerGainsDb)
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(AudioDelayProcessor(audioDelayMs), equalizerProcessor, AudioCompressorProcessor(audioCompressorEnabled)))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
                val preferredParameters = trackSelectionParameters.buildUpon()
                preferences.getString("preferred_audio_language", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(preferredParameters::setPreferredAudioLanguage)
                preferences.getString("preferred_subtitle_language", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(preferredParameters::setPreferredTextLanguage)
                trackSelectionParameters = preferredParameters.build()
                val restoredVolume = preferences.getFloat("preferred_volume", 1f).coerceIn(0f, 1f)
                lastNonZeroVolume = preferences.getFloat("last_non_zero_volume", 1f).coerceIn(0.05f, 1f)
                volume = restoredVolume
                repeatMode = if (tikTodLoopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                preferences.getFloat("preferred_playback_speed", 1f)
                    .takeIf { it.isFinite() && it in 0.25f..8f }
                    ?.let(::setPlaybackSpeed)
                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        refreshTrackOptions()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = SystemClock.elapsedRealtime()
                                _playerState.value = _playerState.value.copy(
                                    isBuffering = true,
                                    error = null
                                )
                            }
                            Player.STATE_READY -> {
                                bufferingStartedAtMs = 0L
                                bufferingFallbackRequestedForUrl = null
                                _playerState.value = _playerState.value.copy(
                                    isBuffering = false,
                                    durationMs = duration.coerceAtLeast(0L),
                                    isSeekable = isCurrentMediaItemSeekable,
                                    isLive = isCurrentMediaItemDynamic,
                                    isPlaying = isPlaying,
                                    error = null
                                )
                            }
                            Player.STATE_ENDED -> {
                                _playerState.value = _playerState.value.copy(
                                    isPlaying = false,
                                    currentPositionMs = duration,
                                    playbackEnded = true
                                )
                            }
                            Player.STATE_IDLE -> {
                                bufferingStartedAtMs = 0L
                                _playerState.value = _playerState.value.copy(isBuffering = false, isSeekable = false)
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        // Start the MediaSessionService only when playback is actually active.
                        // Calling startForegroundService while resolving/preparing a stream can
                        // exceed Android's foreground-service promotion deadline.
                        if (isPlaying && manageMediaSessionService) ensureMediaSessionServiceStarted()
                        val position = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: lastPositionMs
                        if (!isPlaying) lastPositionMs = position
                        _playerState.value = _playerState.value.copy(
                            isPlaying = isPlaying,
                            currentPositionMs = if (isPlaying) _playerState.value.currentPositionMs else position
                        )
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Media3 wraps extractor, manifest, decoder and HTTP failures in
                        // PlaybackException. Try another CloudStream link for any source failure.
                        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
                        val httpError = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                        val cause = httpError ?: error.cause
                        val responseCode = httpError?.responseCode

                        // Retry only source/network/parser I/O failures. Decoder, DRM and
                        // renderer failures should be surfaced directly instead of cycling
                        // through unrelated provider URLs.
                        val isRecoverableSourceFailure = httpError != null ||
                            causes.any { it is IOException }
                        if (isRecoverableSourceFailure && tryNextPluginStream()) return

                        // Expired/session-bound links can be refreshed by asking the provider
                        // to resolve the source again, but never start concurrent refreshes.
                        val isCloudStreamSource = runCatching {
                            isCloudStreamSourceActive?.invoke() == true
                        }.getOrDefault(false)
                        if (isCloudStreamSource && responseCode in setOf(401, 403, 404)) {
                            val refreshHandler = onCloudStreamRefreshRequested
                            if (refreshHandler != null && !cloudStreamRefreshInProgress) {
                                cloudStreamRefreshInProgress = true
                                val position = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: lastPositionMs
                                coroutineScope.launch {
                                    val refreshed = runCatching { refreshHandler(position) }.getOrDefault(false)
                                    cloudStreamRefreshInProgress = false
                                    if (!refreshed) {
                                        _playerState.value = _playerState.value.copy(
                                            isBuffering = false,
                                            isPlaying = false,
                                            error = "Link video tidak berlaku (HTTP $responseCode) dan provider gagal memberikan link baru. Coba sumber lain atau putar ulang.",
                                            isFallbackAvailable = true
                                        )
                                    }
                                }
                                return
                            }
                        }

                        val causeName = cause?.javaClass?.simpleName.orEmpty()
                        val errorDesc = when {
                            cause is HttpDataSource.InvalidResponseCodeException -> {
                                when (cause.responseCode) {
                                    401, 403 -> "Akses ke sumber video ditolak (HTTP ${cause.responseCode}). Link mungkin memerlukan sesi atau token baru."
                                    404 -> "Video tidak ditemukan di server sumber (HTTP 404 Not Found)."
                                    500, 502, 503 -> "Server penyedia video sedang mengalami kendala (HTTP ${cause.responseCode})."
                                    else -> "Gagal memuat video dari server (Kode HTTP: ${cause.responseCode})."
                                }
                            }
                            cause is HttpDataSource.HttpDataSourceException ->
                                describeHttpPlaybackFailure(causes)
                            else -> {
                                val detail = error.message?.takeIf { it.isNotBlank() }
                                    ?: cause?.message?.takeIf { it.isNotBlank() }
                                if (detail != null) "Format atau sumber video gagal diproses ($causeName): $detail"
                                else "Format atau sumber video tidak dapat diproses ($causeName, ${error.errorCodeName})."
                            }
                        }

                        bufferingStartedAtMs = 0L
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

    private fun createClearKeySessionManager(
        drm: com.example.data.plugin.CloudStreamDrm
    ): DrmSessionManager {
        val kid = drm.keyId ?: throw IllegalArgumentException("ClearKey KID kosong.")
        val key = drm.key ?: throw IllegalArgumentException("ClearKey KEY kosong.")
        fun toBase64Url(value: String): String {
            val decoded = runCatching { Base64.decode(value, Base64.DEFAULT) }
                .getOrElse { throw IllegalArgumentException("Format KID/KEY ClearKey bukan Base64 yang valid.", it) }
            return Base64.encodeToString(decoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
        val response = JSONObject()
            .put(
                "keys",
                JSONArray().put(
                    JSONObject()
                        .put("kty", drm.keyType?.takeIf { it.isNotBlank() } ?: "oct")
                        .put("kid", toBase64Url(kid))
                        .put("k", toBase64Url(key))
                )
            )
            .put("type", "temporary")
            .toString()
            .toByteArray(Charsets.UTF_8)
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(LocalMediaDrmCallback(response))
    }

    private fun refreshTrackOptions() {
        val player = exoPlayer ?: return
        val audio = mutableListOf<PlayerTrackOption>()
        val subtitles = mutableListOf<PlayerTrackOption>()
        val video = mutableListOf<PlayerTrackOption>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            val type = group.type
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT && type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                val label = when (type) {
                    C.TRACK_TYPE_AUDIO -> listOfNotNull(language, format.label, format.channelCount.takeIf { it > 0 }?.let { "${it}ch" }).distinct().joinToString(" · ").ifBlank { "Audio ${audio.size + 1}" }
                    C.TRACK_TYPE_TEXT -> listOfNotNull(language, format.label, format.sampleMimeType?.substringAfter('/')?.uppercase()).distinct().joinToString(" · ").ifBlank { "Subtitle ${subtitles.size + 1}" }
                    else -> {
                        val resolution = if (format.height > 0) "${format.height}p" else "Video ${video.size + 1}"
                        val bitrate = format.bitrate.takeIf { it > 0 }?.let { " · ${it / 1000} kbps" }.orEmpty()
                        resolution + bitrate
                    }
                }
                val option = PlayerTrackOption(
                    id = "${groupIndex}:${trackIndex}",
                    label = label,
                    trackType = type,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    selected = group.isTrackSelected(trackIndex),
                    language = language
                )
                when (type) {
                    C.TRACK_TYPE_AUDIO -> audio += option
                    C.TRACK_TYPE_TEXT -> subtitles += option
                    C.TRACK_TYPE_VIDEO -> video += option
                }
            }
        }
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val diagnostics = buildList {
            add("Status: " + when (player.playbackState) {
                Player.STATE_BUFFERING -> "Buffering"
                Player.STATE_READY -> if (player.isPlaying) "Memutar" else "Siap / dijeda"
                Player.STATE_ENDED -> "Selesai"
                else -> "Menyiapkan"
            })
            add("Jenis: " + if (player.isCurrentMediaItemDynamic) "Live" else "VOD")
            videoFormat?.let { format ->
                val resolution = if (format.width > 0 && format.height > 0) "${format.width} × ${format.height}" else "Tidak diketahui"
                val codec = format.codecs ?: format.sampleMimeType ?: "Codec tidak diketahui"
                add("Video: $resolution · $codec")
                if (format.bitrate > 0) add("Bitrate video: ${format.bitrate / 1000} kbps")
                if (format.frameRate.isFinite() && format.frameRate > 0f) add("Frame rate: ${"%.2f".format(java.util.Locale.US, format.frameRate)} fps")
            } ?: video.firstOrNull { it.selected }?.let { add("Video: ${it.label}") }
            audioFormat?.let { format ->
                val codec = format.codecs ?: format.sampleMimeType ?: "Codec tidak diketahui"
                val channels = format.channelCount.takeIf { it > 0 }?.let { "${it} channel" }
                val sampleRate = format.sampleRate.takeIf { it > 0 }?.let { "${it} Hz" }
                add("Audio: " + listOfNotNull(codec, channels, sampleRate).joinToString(" · "))
                if (format.bitrate > 0) add("Bitrate audio: ${format.bitrate / 1000} kbps")
            } ?: audio.firstOrNull { it.selected }?.let { add("Audio: ${it.label}") }
            add("Kecepatan: ${player.playbackParameters.speed}×")
        }
        _playerState.value = _playerState.value.copy(
            audioTracks = audio,
            subtitleTracks = subtitles,
            videoTracks = video,
            subtitlesDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
            preferredAudioLanguage = preferences.getString("preferred_audio_language", null),
            preferredSubtitleLanguage = preferences.getString("preferred_subtitle_language", null),
            playbackDiagnostics = diagnostics
        )
    }

    fun selectTrack(option: PlayerTrackOption) {
        val player = exoPlayer ?: return
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (option.trackIndex !in 0 until group.length) return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex))
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(option.trackType)
            .setTrackTypeDisabled(option.trackType, false)
            .addOverride(override)
        when (option.trackType) {
            C.TRACK_TYPE_AUDIO -> option.language?.takeIf { it.isNotBlank() }?.let { language ->
                preferences.edit().putString("preferred_audio_language", language).apply()
                parameters.setPreferredAudioLanguage(language)
            }
            C.TRACK_TYPE_TEXT -> option.language?.takeIf { it.isNotBlank() }?.let { language ->
                preferences.edit().putString("preferred_subtitle_language", language).apply()
                parameters.setPreferredTextLanguage(language)
            }
        }
        player.trackSelectionParameters = parameters.build()
        refreshTrackOptions()
    }

    override fun selectPluginStream(stream: com.example.data.plugin.CloudStreamStream) {
        if (stream.url.isBlank()) return
        val currentPosition = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: lastPositionMs
        val headers = buildStreamHeaders(stream)
        val mimeType = resolveMimeType(stream.streamType, stream.url)
        playInternal(
            url = stream.url,
            title = lastTitle,
            startPositionMs = currentPosition,
            requestHeaders = headers,
            mimeType = mimeType,
            externalSubtitles = stream.subtitles,
            videoInterceptor = stream.videoInterceptor,
            pluginStreams = lastPluginStreams
        )
    }

    /**
     * Adds a user-selected subtitle without requiring broad storage permission.
     * The SAF URI permission is retained by the caller and the active stream is
     * re-prepared at its current timestamp.
     */
    fun addExternalSubtitle(uri: Uri, displayName: String, mimeType: String?) {
        val player = exoPlayer ?: return
        if (lastUrl.isBlank()) return
        val safeName = displayName.substringAfterLast('/').substringAfterLast(':')
            .ifBlank { "Subtitle eksternal" }
        val normalizedName = safeName.substringBeforeLast('.', safeName)
        val language = Regex("(?i)(?:^|[._ -])(id|ind|indonesian|en|eng|english|ja|jpn|japanese|ko|kor|korean|zh|chi|chinese|ar|ara|arabic)(?:$|[._ -])")
            .find(normalizedName)?.groupValues?.get(1)?.lowercase()
            ?.let { code -> when (code) {
                "ind", "indonesian" -> "id"
                "eng", "english" -> "en"
                "jpn", "japanese" -> "ja"
                "kor", "korean" -> "ko"
                "chi", "chinese" -> "zh"
                "ara", "arabic" -> "ar"
                else -> code
            } } ?: "Unknown"
        val subtitle = com.example.data.plugin.CloudStreamSubtitle(
            language = language,
            url = uri.toString(),
            mimeType = mimeType,
            isExternal = true,
            displayName = normalizedName
        )
        userExternalSubtitles.removeAll { it.url == subtitle.url }
        userExternalSubtitles.add(subtitle)
        val updatedSubtitles = (lastExternalSubtitles + subtitle).distinctBy { it.url }
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying
        lastExternalSubtitles = updatedSubtitles
        playInternal(
            url = lastUrl,
            title = lastTitle,
            startPositionMs = currentPosition,
            requestHeaders = lastHeaders,
            mimeType = lastMimeType,
            externalSubtitles = updatedSubtitles,
            videoInterceptor = lastVideoInterceptor,
            pluginStreams = lastPluginStreams
        )
        if (!wasPlaying) exoPlayer?.pause()
    }

    fun disableSubtitles() {
        val player = exoPlayer ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        refreshTrackOptions()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.playbackState == Player.STATE_BUFFERING) {
                        if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = SystemClock.elapsedRealtime()
                        val bufferingForMs = SystemClock.elapsedRealtime() - bufferingStartedAtMs
                        if (bufferingForMs >= 45_000L && bufferingFallbackRequestedForUrl != lastUrl) {
                            bufferingFallbackRequestedForUrl = lastUrl
                            if (!tryNextPluginStream()) {
                                bufferingStartedAtMs = 0L
                                _playerState.value = _playerState.value.copy(
                                    isBuffering = false,
                                    isPlaying = false,
                                    error = "Sumber video tidak merespons setelah 45 detik. Coba pilih sumber lain atau putar ulang.",
                                    isFallbackAvailable = true
                                )
                                player.stop()
                            }
                        }
                    } else if (player.playbackState == Player.STATE_READY) {
                        bufferingStartedAtMs = 0L
                        bufferingFallbackRequestedForUrl = null
                    }
                    // A-B repeat: seek back to A as soon as playback reaches B.
                    val pointA = repeatPointA
                    val pointB = repeatPointB
                    if (player.isPlaying && pointA != null && pointB != null &&
                        player.currentPosition >= pointB
                    ) {
                        player.seekTo(pointA)
                        lastPositionMs = pointA
                        _playerState.value = _playerState.value.copy(currentPositionMs = pointA)
                    }
                    // Duration is available at READY even while paused.
                    if (player.playbackState == Player.STATE_READY) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                            isSeekable = player.isCurrentMediaItemSeekable,
                            isLive = player.isCurrentMediaItemDynamic
                        )
                    }
                    if (player.isPlaying) lastPositionMs = player.currentPosition.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun buildStreamHeaders(stream: com.example.data.plugin.CloudStreamStream): Map<String, String> =
        stream.headers.toMutableMap().apply {
            if (stream.referer.isNotBlank() && keys.none { it.equals("Referer", ignoreCase = true) }) {
                put("Referer", stream.referer)
            }
        }

    private fun buildStreamAttemptKey(url: String, headers: Map<String, String>): String {
        val normalizedHeaders = headers.entries.sortedBy { it.key.lowercase() }
            .joinToString("&") { it.key.lowercase() + "=" + it.value }
        return normalizePlaybackUrl(url) + "|" + normalizedHeaders
    }

    private fun tryNextPluginStream(): Boolean {
        val alternative = lastPluginStreams.asSequence()
            .filter { it.url.isNotBlank() }
            .filter { stream -> buildStreamAttemptKey(stream.url, buildStreamHeaders(stream)) !in attemptedPluginStreamKeys }
            .sortedByDescending { it.quality }
            .firstOrNull() ?: return false
        selectPluginStream(alternative)
        return true
    }

    private fun resolveMimeType(streamType: String, url: String): String? {
        val type = streamType.substringAfterLast('.').uppercase()
        val extension = runCatching {
            Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").uppercase()
        }.getOrDefault("")
        return when {
            type.contains("M3U8") || type.contains("HLS") || extension == "M3U8" -> "application/x-mpegURL"
            type.contains("DASH") || type == "MPD" || extension == "MPD" -> "application/dash+xml"
            type.contains("WEBM") || extension == "WEBM" -> "video/webm"
            type.contains("MKV") || type.contains("MATROSKA") || extension == "MKV" -> "video/x-matroska"
            type.contains("MP4") || type == "VIDEO" || extension == "MP4" -> "video/mp4"
            else -> null
        }
    }

    override fun play(url: String, title: String, startPositionMs: Long, requestHeaders: Map<String, String>, mediaCacheKey: String?) {
        attemptedPluginStreamKeys.clear()
        cloudStreamRefreshInProgress = false
        playInternal(url, title, startPositionMs, requestHeaders, null, pluginStreams = emptyList(), mediaCacheKey = mediaCacheKey)
    }

    override fun cancelDrivePreload() {
        drivePreloadJob?.cancel()
        drivePreloadJob = null
    }

    override fun setTikTodLoopEnabled(enabled: Boolean) {
        tikTodLoopEnabled = enabled
        // TikTod has exactly one active ExoPlayer item at a time. Repeat that
        // item in-place; never use the app queue or the user's global Repeat setting.
        exoPlayer?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (enabled) {
            _playerState.value = _playerState.value.copy(playbackEnded = false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override fun preloadDriveMedia(url: String, mediaId: String) {
        if (url.isBlank() || !com.example.data.model.MediaSourceClassifier.isGoogleDriveUrl(url)) return
        val canonicalCacheKey = MediaCacheIdentity.forUrl(url) ?: return
        drivePreloadJob?.cancel()
        drivePreloadJob = coroutineScope.launch(Dispatchers.IO) {
            // CacheWriter requires the concrete CacheDataSource. Hold the same
            // active-reader lease explicitly for the full preload lifecycle.
            DriveMediaCache.acquireRead()
            val source = CacheDataSource.Factory()
                .setCache(DriveMediaCache.get(context))
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, createHttpDataSourceFactory(null)))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            try {
                val request = androidx.media3.datasource.DataSpec.Builder()
                    .setUri(Uri.parse(normalizePlaybackUrl(url.trim())))
                    .setKey(canonicalCacheKey)
                    .setPosition(0L)
                    .setLength(DRIVE_PRELOAD_BYTES)
                    .build()
                runInterruptible { CacheWriter(source, request, ByteArray(128 * 1024), null).cache() }
            } catch (error: Exception) {
                Log.d("NSiOPlayer", "Drive next-item preload stopped: ${error.message}")
            } finally {
                runCatching { source.close() }
                DriveMediaCache.releaseRead()
            }
        }
    }



    fun playWithMimeType(
        url: String,
        title: String,
        startPositionMs: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap(),
        mimeType: String? = null,
        externalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        videoInterceptor: okhttp3.Interceptor? = null,
        pluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList(),
        mediaCacheKey: String? = null
    ) {
        attemptedPluginStreamKeys.clear()
        playInternal(url, title, startPositionMs, requestHeaders, mimeType, externalSubtitles, videoInterceptor, pluginStreams, mediaCacheKey)
    }

    private fun buildSubtitleConfigurations(
        subtitles: List<com.example.data.plugin.CloudStreamSubtitle>,
        delayMs: Int
    ): List<ExoMediaItem.SubtitleConfiguration> = subtitles
        .filter { it.url.isNotBlank() }
        .map { subtitle ->
            val subtitleMime = resolveSubtitleMimeType(subtitle.url, subtitle.mimeType)
            ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setLanguage(subtitle.language.takeIf { it.isNotBlank() && it != "Unknown" })
                .setLabel(subtitle.displayName?.takeIf { it.isNotBlank() } ?: subtitle.language.ifBlank { "Subtitle" })
                .setSelectionFlags(if (subtitle.isExternal) C.SELECTION_FLAG_DEFAULT else 0)
                .setTimeOffsetUs(delayMs.toLong() * 1_000L)
                .apply { subtitleMime?.let(::setMimeType) }
                .build()
        }

    private fun resolvePluginAudioMimeType(url: String): String? = when (
        Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
    ) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "weba", "webm" -> "audio/webm"
        else -> null
    }

    /**
     * Mirrors CloudStream's getAudioSources(): each plugin-provided audio file
     * gets its own HTTP factory so its headers are not replaced by video headers.
     */
    private fun createPluginAudioSources(
        videoUrl: String,
        videoHeaders: Map<String, String>,
        interceptor: okhttp3.Interceptor?,
        pluginStreams: List<com.example.data.plugin.CloudStreamStream>
    ): List<MediaSource> {
        val activeStream = pluginStreams.firstOrNull {
            normalizePlaybackUrl(it.url) == normalizePlaybackUrl(videoUrl) &&
                buildStreamHeaders(it).all { (name, value) ->
                    videoHeaders.entries.any { entry -> entry.key.equals(name, true) && entry.value == value }
                }
        } ?: pluginStreams.firstOrNull {
            normalizePlaybackUrl(it.url) == normalizePlaybackUrl(videoUrl)
        } ?: return emptyList()

        return activeStream.audioTracks.mapIndexedNotNull { index, audio ->
            if (audio.url.isBlank()) return@mapIndexedNotNull null
            runCatching {
                val audioHeaders = videoHeaders.toMutableMap().apply {
                    audio.headers.forEach { (name, value) ->
                        keys.filter { it.equals(name, ignoreCase = true) }.forEach(::remove)
                        put(name, value)
                    }
                }
                val audioHttpFactory = createHttpDataSourceFactory(
                    interceptor = interceptor,
                    additionalHeaders = audioHeaders
                )
                val audioDataSourceFactory = DefaultDataSource.Factory(context, audioHttpFactory)
                val audioSourceFactory = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(audioDataSourceFactory)
                val audioItemBuilder = ExoMediaItem.Builder()
                    .setUri(audio.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Audio tambahan ${index + 1}")
                            .build()
                    )
                resolvePluginAudioMimeType(audio.url)?.let(audioItemBuilder::setMimeType)
                audioSourceFactory.createMediaSource(audioItemBuilder.build())
            }.onFailure { error ->
                Log.w("NSiOPlayer", "Unable to create CloudStream audio track ${index + 1}", error)
            }.getOrNull()
        }
    }

    private fun playInternal(
        url: String,
        title: String,
        startPositionMs: Long,
        requestHeaders: Map<String, String>,
        mimeType: String?,
        externalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        videoInterceptor: okhttp3.Interceptor? = null,
        pluginStreams: List<com.example.data.plugin.CloudStreamStream> = lastPluginStreams,
        mediaCacheKey: String? = lastMediaCacheKey
    ) {
        if (lastTitle.isNotBlank() && title != lastTitle) {
            userExternalSubtitles.clear()
            externalAudioUri = null
            externalAudioName = null
        }
        lastUrl = url
        lastTitle = title
        val playbackCacheIdentity = MediaCacheIdentity.forPlayback(url, mediaCacheKey)
        // Keep the same identity for CacheDataSource and ExoMediaItem custom keys.
        // This allows refreshed CloudStream/CDN URLs to reuse prior cached byte ranges.
        lastMediaCacheKey = playbackCacheIdentity
        activeMediaCacheKey = playbackCacheIdentity
        repeatPointA = null
        repeatPointB = null
        _playerState.value = _playerState.value.copy(repeatA = null, repeatB = null)
        lastPositionMs = startPositionMs
        lastHeaders = requestHeaders
        lastMimeType = mimeType
        val mergedExternalSubtitles = (externalSubtitles + userExternalSubtitles).distinctBy { it.url }
        lastExternalSubtitles = mergedExternalSubtitles
        lastVideoInterceptor = videoInterceptor
        lastPluginStreams = pluginStreams
        bufferingStartedAtMs = 0L
        bufferingFallbackRequestedForUrl = null
        if (url.isNotBlank()) {
            attemptedPluginStreamKeys.add(buildStreamAttemptKey(url, requestHeaders))
        }
        httpDataSourceFactory = createHttpDataSourceFactory(videoInterceptor, mergedExternalSubtitles)
        val drmInfo = pluginStreams.firstOrNull { it.url == url }?.drm
        activeDrmSessionManager = null

        exoPlayer?.let { player ->
            try {
                require(url.isNotBlank()) { "URL media kosong" }
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf("Accept" to "*/*", "User-Agent" to STANDARD_USER_AGENT) + requestHeaders
                )
                _playerState.value = PlayerState(
                    title = title,
                    playbackEnded = false,
                    currentPositionMs = startPositionMs.coerceAtLeast(0L),
                    isBuffering = true,
                    isFallbackAvailable = true,
                    playbackSpeed = player.playbackParameters.speed,
                    repeatMode = logicalRepeatMode,
                    shuffleEnabled = logicalShuffleEnabled,
                    volume = player.volume,
                    pluginStreams = pluginStreams,
                    selectedPluginStreamUrl = url,
                    audioDelayMs = audioDelayMs,
                    subtitleDelayMs = subtitleDelayMs,
                    audioCompressorEnabled = audioCompressorEnabled,
                    equalizerGainsDb = equalizerGainsDb.toList(),
                    externalAudioName = externalAudioName
                )
                val mediaItemBuilder = ExoMediaItem.Builder()
                    .setUri(normalizePlaybackUrl(url.trim()))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                lastMediaCacheKey?.let(mediaItemBuilder::setCustomCacheKey)
                mimeType?.takeIf { it.isNotBlank() }?.let(mediaItemBuilder::setMimeType)
                if (drmInfo != null) {
                    val drmUuid = runCatching { UUID.fromString(drmInfo.uuid) }.getOrNull()
                        ?: throw IllegalArgumentException("UUID DRM dari plugin tidak valid.")
                    if (drmUuid != CLEARKEY_UUID) {
                        throw IllegalArgumentException("Sumber ini memakai DRM berlisensi ($drmUuid), tetapi plugin SDK yang terpasang tidak memberikan URL lisensi. NSiO hanya dapat memutar ClearKey lokal untuk saat ini.")
                    }
                    if (drmInfo.keyId.isNullOrBlank() || drmInfo.key.isNullOrBlank()) {
                        throw IllegalArgumentException("Metadata ClearKey dari plugin tidak lengkap (KID/KEY kosong).")
                    }
                    activeDrmSessionManager = createClearKeySessionManager(drmInfo)
                    mediaItemBuilder.setDrmConfiguration(
                        ExoMediaItem.DrmConfiguration.Builder(drmUuid).build()
                    )
                }
                val subtitleConfigurations = buildSubtitleConfigurations(mergedExternalSubtitles, subtitleDelayMs)
                if (subtitleConfigurations.isNotEmpty()) {
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
                }
                val videoMediaItem = mediaItemBuilder.build()
                val mergedSources = mutableListOf<MediaSource>()
                val pluginAudioSources = createPluginAudioSources(
                    videoUrl = url,
                    videoHeaders = requestHeaders,
                    interceptor = videoInterceptor,
                    pluginStreams = pluginStreams
                )
                if (pluginAudioSources.isNotEmpty() || externalAudioUri != null) {
                    mergedSources += mediaSourceFactory.createMediaSource(videoMediaItem)
                    mergedSources += pluginAudioSources
                    externalAudioUri?.let { selectedExternalAudio ->
                        val audioMediaItemBuilder = ExoMediaItem.Builder()
                            .setUri(selectedExternalAudio)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(externalAudioName ?: "Audio eksternal").build())
                        val audioMimeType = context.contentResolver.getType(selectedExternalAudio)
                            ?: resolveExternalAudioMimeType(externalAudioName.orEmpty())
                        audioMimeType?.let(audioMediaItemBuilder::setMimeType)
                        mergedSources += mediaSourceFactory.createMediaSource(audioMediaItemBuilder.build())
                    }
                    player.setMediaSource(MergingMediaSource(*mergedSources.toTypedArray()))
                } else {
                    player.setMediaItem(videoMediaItem)
                }
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

    override fun setExternalAudioTrack(uri: Uri, displayName: String) {
        require(uri.scheme == "content" || uri.scheme == "file") { "File audio harus berasal dari penyimpanan perangkat." }
        require(displayName.isNotBlank()) { "Nama file audio kosong." }
        externalAudioUri = uri
        externalAudioName = displayName
        if (lastUrl.isNotBlank()) {
            val position = exoPlayer?.currentPosition?.takeIf { it > 0L } ?: lastPositionMs
            playInternal(lastUrl, lastTitle, position.coerceAtLeast(0L), lastHeaders, lastMimeType, lastExternalSubtitles, lastVideoInterceptor, lastPluginStreams)
        } else {
            _playerState.value = _playerState.value.copy(externalAudioName = displayName)
        }
    }

    override fun clearExternalAudioTrack() {
        externalAudioUri = null
        externalAudioName = null
        if (lastUrl.isNotBlank()) {
            val position = exoPlayer?.currentPosition?.takeIf { it > 0L } ?: lastPositionMs
            playInternal(lastUrl, lastTitle, position.coerceAtLeast(0L), lastHeaders, lastMimeType, lastExternalSubtitles, lastVideoInterceptor, lastPluginStreams)
        } else {
            _playerState.value = _playerState.value.copy(externalAudioName = null)
        }
    }

    override fun retry() {
        if (lastUrl.isNotEmpty()) {
            val current = exoPlayer?.currentPosition?.takeIf { it > 0L } ?: lastPositionMs
            playWithMimeType(lastUrl, lastTitle, current.coerceAtLeast(0L), lastHeaders, lastMimeType, lastExternalSubtitles, lastVideoInterceptor, lastPluginStreams, lastMediaCacheKey)
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
                // A play action after reaching the end should replay from the beginning.
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                    lastPositionMs = 0L
                    _playerState.value = _playerState.value.copy(currentPositionMs = 0L, playbackEnded = false)
                }
                player.play()
            }
        }
    }

    override fun pause() {
        exoPlayer?.let { player ->
            lastPositionMs = player.currentPosition.coerceAtLeast(0L)
            player.pause()
            _playerState.value = _playerState.value.copy(currentPositionMs = lastPositionMs)
        }
    }

    override fun stop() {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        lastUrl = ""
        lastTitle = ""
        lastPositionMs = 0L
        lastHeaders = emptyMap()
        lastMimeType = null
        attemptedPluginStreamKeys.clear()
        lastPluginStreams = emptyList()
        lastExternalSubtitles = emptyList()
        userExternalSubtitles.clear()
        externalAudioUri = null
        externalAudioName = null
        activeDrmSessionManager = null
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        _playerState.value = PlayerState(
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
            audioCompressorEnabled = audioCompressorEnabled,
            preferredAudioLanguage = preferences.getString("preferred_audio_language", null),
            preferredSubtitleLanguage = preferences.getString("preferred_subtitle_language", null)
        )
    }

    override fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable) return
        val duration = player.duration.coerceAtLeast(0L)
        val target = positionMs.coerceAtLeast(0L).coerceAtMost(duration)
        player.seekTo(target)
        lastPositionMs = target
        _playerState.value = _playerState.value.copy(currentPositionMs = target)
    }

    override fun seekToLive() {
        exoPlayer?.takeIf { it.isCurrentMediaItemDynamic }?.seekToDefaultPosition()
    }

    override fun seekForward(offsetMs: Long) {
        exoPlayer?.takeIf { it.isCurrentMediaItemSeekable }?.let { player ->
            val current = player.currentPosition.coerceAtLeast(0L)
            val target = (current + offsetMs.coerceAtLeast(0L)).let { requested ->
                if (player.duration > 0L) requested.coerceAtMost(player.duration) else requested
            }
            player.seekTo(target)
            lastPositionMs = target
            _playerState.value = _playerState.value.copy(currentPositionMs = target)
        }
    }

    override fun seekBackward(offsetMs: Long) {
        exoPlayer?.takeIf { it.isCurrentMediaItemSeekable }?.let { player ->
            val target = (player.currentPosition.coerceAtLeast(0L) - offsetMs.coerceAtLeast(0L)).coerceAtLeast(0L)
            player.seekTo(target)
            lastPositionMs = target
            _playerState.value = _playerState.value.copy(currentPositionMs = target)
        }
    }

    override fun setRepeatPointA() {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable) return
        repeatPointA = player.currentPosition.coerceAtLeast(0L)
        repeatPointB = null
        _playerState.value = _playerState.value.copy(repeatA = repeatPointA, repeatB = null)
    }

    override fun setRepeatPointB() {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable) return
        val pointA = repeatPointA ?: return
        val pointB = player.currentPosition.coerceAtLeast(0L)
        if (pointB - pointA < 500L) return
        repeatPointB = pointB
        _playerState.value = _playerState.value.copy(repeatB = pointB)
    }

    override fun clearABRepeat() {
        repeatPointA = null
        repeatPointB = null
        _playerState.value = _playerState.value.copy(repeatA = null, repeatB = null)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val player = exoPlayer ?: return
        if (!speed.isFinite() || speed <= 0f) return
        val safeSpeed = speed.coerceIn(0.25f, 8f)
        player.setPlaybackSpeed(safeSpeed)
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit()
            .putFloat("preferred_playback_speed", safeSpeed)
            .apply()
        _playerState.value = _playerState.value.copy(playbackSpeed = safeSpeed)
    }

    override fun setPreferredAudioLanguage(language: String?) {
        val normalized = language?.trim()?.takeIf { it.isNotEmpty() }
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        preferences.edit().apply {
            if (normalized == null) remove("preferred_audio_language") else putString("preferred_audio_language", normalized)
        }.apply()
        exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setPreferredAudioLanguage(normalized)
                .build()
            refreshTrackOptions()
        }
        _playerState.value = _playerState.value.copy(preferredAudioLanguage = normalized)
    }

    override fun setPreferredSubtitleLanguage(language: String?) {
        val normalized = language?.trim()?.takeIf { it.isNotEmpty() }
        val preferences = context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
        preferences.edit().apply {
            if (normalized == null) remove("preferred_subtitle_language") else putString("preferred_subtitle_language", normalized)
        }.apply()
        exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(normalized)
                .build()
            refreshTrackOptions()
        }
        _playerState.value = _playerState.value.copy(preferredSubtitleLanguage = normalized, subtitlesDisabled = false)
    }

    override fun setAudioDelay(delayMs: Int) {
        val safeDelay = delayMs.coerceIn(-2000, 2000)
        if (safeDelay == audioDelayMs) return
        audioDelayMs = safeDelay
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit()
            .putInt("audio_delay_ms", safeDelay)
            .apply()
        val player = exoPlayer
        val currentPosition = player?.currentPosition?.coerceAtLeast(0L) ?: lastPositionMs
        val wasPlaying = player?.isPlaying ?: false
        val speed = player?.playbackParameters?.speed ?: _playerState.value.playbackSpeed
        val volume = player?.volume ?: _playerState.value.volume
        if (lastUrl.isBlank()) {
            _playerState.value = _playerState.value.copy(audioDelayMs = audioDelayMs)
            return
        }

        progressJob?.cancel()
        player?.release()
        exoPlayer = null
        initExoPlayer()
        playInternal(
            url = lastUrl,
            title = lastTitle,
            startPositionMs = currentPosition,
            requestHeaders = lastHeaders,
            mimeType = lastMimeType,
            externalSubtitles = lastExternalSubtitles,
            videoInterceptor = lastVideoInterceptor,
            pluginStreams = lastPluginStreams
        )
        exoPlayer?.setPlaybackSpeed(speed)
        exoPlayer?.volume = volume
        if (!wasPlaying) exoPlayer?.pause()
        _playerState.value = _playerState.value.copy(
            playbackSpeed = speed,
            volume = volume,
            audioDelayMs = audioDelayMs
        )
    }
    override fun setSubtitleDelay(delayMs: Int) {
        val safeDelay = delayMs.coerceIn(-5000, 5000)
        if (safeDelay == subtitleDelayMs) return
        subtitleDelayMs = safeDelay
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putInt("subtitle_delay_ms", safeDelay).apply()

        val player = exoPlayer
        val itemIndex = player?.currentMediaItemIndex ?: C.INDEX_UNSET
        val currentItem = player?.currentMediaItem
        if (player != null && currentItem != null && itemIndex != C.INDEX_UNSET && lastExternalSubtitles.isNotEmpty()) {
            val updatedItem = currentItem.buildUpon()
                .setSubtitleConfigurations(buildSubtitleConfigurations(lastExternalSubtitles, safeDelay))
                .build()
            val previousSelection = player.trackSelectionParameters
            val textWasDisabled = C.TRACK_TYPE_TEXT in previousSelection.disabledTrackTypes
            // Media3 1.12 updates sideloaded subtitle timestamps in-place. Re-select
            // the text renderer so a cue already visible is recalculated immediately.
            player.replaceMediaItem(itemIndex, updatedItem)
            if (!textWasDisabled) {
                player.trackSelectionParameters = previousSelection.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                player.trackSelectionParameters = previousSelection.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
        }
        _playerState.value = _playerState.value.copy(subtitleDelayMs = subtitleDelayMs)
    }

    override fun setAudioCompressorEnabled(enabled: Boolean) {
        if (audioCompressorEnabled == enabled) return
        audioCompressorEnabled = enabled
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("audio_compressor_enabled", enabled).apply()

        val player = exoPlayer
        val currentPosition = player?.currentPosition?.coerceAtLeast(0L) ?: lastPositionMs
        val wasPlaying = player?.isPlaying ?: false
        val speed = player?.playbackParameters?.speed ?: _playerState.value.playbackSpeed
        val volume = player?.volume ?: _playerState.value.volume
        if (lastUrl.isBlank()) {
            _playerState.value = _playerState.value.copy(audioCompressorEnabled = enabled)
            return
        }

        progressJob?.cancel()
        player?.release()
        exoPlayer = null
        initExoPlayer()
        playInternal(
            url = lastUrl,
            title = lastTitle,
            startPositionMs = currentPosition,
            requestHeaders = lastHeaders,
            mimeType = lastMimeType,
            externalSubtitles = lastExternalSubtitles,
            videoInterceptor = lastVideoInterceptor,
            pluginStreams = lastPluginStreams
        )
        exoPlayer?.setPlaybackSpeed(speed)
        exoPlayer?.volume = volume
        if (!wasPlaying) exoPlayer?.pause()
        _playerState.value = _playerState.value.copy(
            playbackSpeed = speed,
            volume = volume,
            audioDelayMs = audioDelayMs,
            audioCompressorEnabled = enabled
        )
    }

    override fun setEqualizerBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until 10 || !gainDb.isFinite()) return
        val safeGain = gainDb.coerceIn(-12f, 12f)
        equalizerGainsDb[index] = safeGain
        equalizerProcessor.setBandGain(index, safeGain)
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putString("equalizer_gains_db", equalizerGainsDb.joinToString(",")).apply()
        _playerState.value = _playerState.value.copy(equalizerGainsDb = equalizerGainsDb.toList())
    }

    override fun resetEqualizer() {
        equalizerGainsDb.fill(0f)
        equalizerProcessor.resetBands()
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putString("equalizer_gains_db", equalizerGainsDb.joinToString(",")).apply()
        _playerState.value = _playerState.value.copy(equalizerGainsDb = equalizerGainsDb.toList())
    }

    override fun toggleRepeatMode() {
        logicalRepeatMode = when (logicalRepeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        // The app queue owns repeat behavior; ExoPlayer has only the active resolved stream.
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putInt("playback_repeat_mode", logicalRepeatMode).apply()
        _playerState.value = _playerState.value.copy(repeatMode = logicalRepeatMode)
    }
    override fun toggleShuffle() {
        logicalShuffleEnabled = !logicalShuffleEnabled
        // The app queue owns shuffle order; the single-item ExoPlayer playlist stays unshuffled.
        exoPlayer?.shuffleModeEnabled = false
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("playback_shuffle_enabled", logicalShuffleEnabled).apply()
        _playerState.value = _playerState.value.copy(shuffleEnabled = logicalShuffleEnabled)
    }
    override fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = safeVolume
        if (safeVolume > 0f) lastNonZeroVolume = safeVolume
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit()
            .putFloat("preferred_volume", safeVolume)
            .putFloat("last_non_zero_volume", lastNonZeroVolume)
            .apply()
        _playerState.value = _playerState.value.copy(volume = safeVolume)
    }
    override fun toggleMute() {
        val player = exoPlayer ?: return
        if (player.volume > 0f) {
            lastNonZeroVolume = player.volume
            player.volume = 0f
        } else player.volume = lastNonZeroVolume.coerceIn(0.05f, 1f)
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
            .edit()
            .putFloat("preferred_volume", player.volume)
            .putFloat("last_non_zero_volume", lastNonZeroVolume)
            .apply()
        _playerState.value = _playerState.value.copy(volume = player.volume)
    }

    override fun release() {
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}

/**
 * One player engine per app process. The Activity/ViewModel and MediaSessionService
 * must control the exact same ExoPlayer instance to avoid duplicate streams.
 */
object PlayerRuntime {
    @Volatile private var instance: CS3IPlayer? = null
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun get(context: Context): CS3IPlayer {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: CS3IPlayer(context.applicationContext, playbackScope).also { instance = it }
        }
    }
}
