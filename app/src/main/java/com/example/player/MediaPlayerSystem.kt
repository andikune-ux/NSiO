package com.example.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lagradost.cloudstream3.app
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
    val isLive: Boolean = false
)

interface IPlayer {
    val playerState: StateFlow<PlayerState>
    fun play(url: String, title: String, startPositionMs: Long = 0L, requestHeaders: Map<String, String> = emptyMap())
    fun togglePlayPause()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun setPlaybackSpeed(speed: Float)
    fun setAudioDelay(delayMs: Int)
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
    private var lastMimeType: String? = null
    private var lastExternalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList()
    private val userExternalSubtitles = mutableListOf<com.example.data.plugin.CloudStreamSubtitle>()
    private var lastVideoInterceptor: okhttp3.Interceptor? = null
    private var lastPluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList()
    private val attemptedPluginStreamKeys = mutableSetOf<String>()
    private var bufferingStartedAtMs: Long = 0L
    private var bufferingFallbackRequestedForUrl: String? = null
    @Volatile private var cloudStreamRefreshInProgress = false

    /** Called when resolved plugin URLs fail with an expired-link HTTP response. */
    var onCloudStreamRefreshRequested: (suspend (Long) -> Boolean)? = null
    private var lastNonZeroVolume: Float = 1f
    private var audioDelayMs: Int = 0
    private var logicalRepeatMode: Int = Player.REPEAT_MODE_OFF
    private var logicalShuffleEnabled: Boolean = false
    private lateinit var httpDataSourceFactory: HttpDataSource.Factory

    companion object {
        const val DEFAULT_FALLBACK_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        const val STANDARD_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 NetStream/2.0"
    }

    init {
        initExoPlayer()
    }

    private fun createHttpDataSourceFactory(
        interceptor: okhttp3.Interceptor?,
        subtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList()
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
        return OkHttpDataSource.Factory(client)
            .setUserAgent(STANDARD_USER_AGENT)
            .setDefaultRequestProperties(baseHeaders)
    }

    private fun initExoPlayer() {
        httpDataSourceFactory = createHttpDataSourceFactory(null)
        val dataSourceFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                return DefaultDataSource.Factory(context, httpDataSourceFactory).createDataSource()
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(AudioDelayProcessor(audioDelayMs)))
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
                        if (responseCode == 401 || responseCode == 403 || responseCode == 404) {
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
                                "Koneksi ke server video gagal atau terputus. Periksa koneksi internet dan coba sumber lain."
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
        _playerState.value = _playerState.value.copy(
            audioTracks = audio,
            subtitleTracks = subtitles,
            videoTracks = video,
            subtitlesDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
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
    fun addExternalSubtitle(uri: Uri, label: String, mimeType: String?) {
        val player = exoPlayer ?: return
        if (lastUrl.isBlank()) return
        val subtitle = com.example.data.plugin.CloudStreamSubtitle(
            language = label.ifBlank { "Subtitle eksternal" },
            url = uri.toString(),
            mimeType = mimeType
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

    override fun play(url: String, title: String, startPositionMs: Long, requestHeaders: Map<String, String>) {
        attemptedPluginStreamKeys.clear()
        cloudStreamRefreshInProgress = false
        playInternal(url, title, startPositionMs, requestHeaders, null, pluginStreams = emptyList())
    }

    fun playWithMimeType(
        url: String,
        title: String,
        startPositionMs: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap(),
        mimeType: String? = null,
        externalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        videoInterceptor: okhttp3.Interceptor? = null,
        pluginStreams: List<com.example.data.plugin.CloudStreamStream> = emptyList()
    ) {
        attemptedPluginStreamKeys.clear()
        playInternal(url, title, startPositionMs, requestHeaders, mimeType, externalSubtitles, videoInterceptor, pluginStreams)
    }

    private fun playInternal(
        url: String,
        title: String,
        startPositionMs: Long,
        requestHeaders: Map<String, String>,
        mimeType: String?,
        externalSubtitles: List<com.example.data.plugin.CloudStreamSubtitle> = emptyList(),
        videoInterceptor: okhttp3.Interceptor? = null,
        pluginStreams: List<com.example.data.plugin.CloudStreamStream> = lastPluginStreams
    ) {
        if (lastTitle.isNotBlank() && title != lastTitle) userExternalSubtitles.clear()
        lastUrl = url
        lastTitle = title
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
                    audioDelayMs = audioDelayMs
                )
                val mediaItemBuilder = ExoMediaItem.Builder()
                    .setUri(normalizePlaybackUrl(url.trim()))
                mimeType?.takeIf { it.isNotBlank() }?.let(mediaItemBuilder::setMimeType)
                val subtitleConfigurations = mergedExternalSubtitles.filter { it.url.isNotBlank() }.map { subtitle ->
                    val extension = runCatching { Uri.parse(subtitle.url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase() }.getOrDefault("")
                    val subtitleMime = when (subtitle.mimeType?.lowercase()) {
                        "text/vtt", "application/x-subrip", "application/ttml+xml", "text/x-ssa" -> subtitle.mimeType
                        else -> when (extension) {
                            "vtt" -> "text/vtt"
                            "srt" -> "application/x-subrip"
                            "ttml", "xml" -> "application/ttml+xml"
                            "ass", "ssa" -> "text/x-ssa"
                            else -> null
                        }
                    }
                    ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setLanguage(subtitle.language.takeIf { it.isNotBlank() && it != "Unknown" })
                        .setLabel(subtitle.language.ifBlank { "Subtitle" })
                        .setSelectionFlags(0)
                        .apply { subtitleMime?.let(::setMimeType) }
                        .build()
                }
                if (subtitleConfigurations.isNotEmpty()) {
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
                }
                player.setMediaItem(mediaItemBuilder.build())
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
            val current = exoPlayer?.currentPosition?.takeIf { it > 0L } ?: lastPositionMs
            playWithMimeType(lastUrl, lastTitle, current.coerceAtLeast(0L), lastHeaders, lastMimeType, lastExternalSubtitles, lastVideoInterceptor, lastPluginStreams)
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
        _playerState.value = PlayerState(audioDelayMs = audioDelayMs)
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

    override fun setPlaybackSpeed(speed: Float) {
        val player = exoPlayer ?: return
        if (speed <= 0f) return
        player.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    override fun setAudioDelay(delayMs: Int) {
        val safeDelay = delayMs.coerceIn(-2000, 2000)
        if (safeDelay == audioDelayMs) return
        audioDelayMs = safeDelay
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
    override fun toggleRepeatMode() {
        logicalRepeatMode = when (logicalRepeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        // The app queue owns repeat behavior; ExoPlayer has only the active resolved stream.
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        _playerState.value = _playerState.value.copy(repeatMode = logicalRepeatMode)
    }
    override fun toggleShuffle() {
        logicalShuffleEnabled = !logicalShuffleEnabled
        // The app queue owns shuffle order; the single-item ExoPlayer playlist stays unshuffled.
        exoPlayer?.shuffleModeEnabled = false
        _playerState.value = _playerState.value.copy(shuffleEnabled = logicalShuffleEnabled)
    }
    override fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = safeVolume
        if (safeVolume > 0f) lastNonZeroVolume = safeVolume
        _playerState.value = _playerState.value.copy(volume = safeVolume)
    }
    override fun toggleMute() {
        val player = exoPlayer ?: return
        if (player.volume > 0f) {
            lastNonZeroVolume = player.volume
            player.volume = 0f
        } else player.volume = lastNonZeroVolume.coerceIn(0.05f, 1f)
        _playerState.value = _playerState.value.copy(volume = player.volume)
    }

    override fun release() {
        progressJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
