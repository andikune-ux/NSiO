package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Toast
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.player.VideoSkipRepository
import com.example.player.VideoSkipStamp
import com.example.player.adjacentMedia
import com.example.player.normalizePlaybackUrl
import com.example.ui.MainViewModel
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private object PlayerFramePreviewCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    media: MediaItem,
    viewModel: MainViewModel,
    onClose: () -> Unit,
    isInPipMode: Boolean = false
) {
    val playerState by viewModel.player.playerState.collectAsState()
    val previewUrl = playerState.selectedPluginStreamUrl?.takeIf { it.isNotBlank() } ?: media.url
    val previewHeaders = remember(playerState.selectedPluginStreamUrl, playerState.pluginStreams) {
        playerState.pluginStreams.firstOrNull { it.url == playerState.selectedPluginStreamUrl }
            ?.let { stream ->
                stream.headers.toMutableMap().apply {
                    if (stream.referer.isNotBlank() && keys.none { it.equals("Referer", ignoreCase = true) }) {
                        put("Referer", stream.referer)
                    }
                    if (keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                        put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36 NetStream/2.0")
                    }
                }
            }
            ?: mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36 NetStream/2.0")
    }
    val playbackPreparationMessage by viewModel.playbackPreparationMessage.collectAsState()
    // Cast callbacks can outlive a playback-position update; always read the latest state.
    val latestCastPlayerState by rememberUpdatedState(playerState)
    val latestCastMedia by rememberUpdatedState(media)
    val mediaList by viewModel.mediaList.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableStateOf(0) }
    fun revealControls() {
        showControls = true
        controlsInteraction++
    }
    var showQueue by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var selectedPlayerSetting by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var selectedResizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf(media.progressMs.coerceAtLeast(0L)) }
    var previewFrame by remember(media.id) { mutableStateOf<Bitmap?>(null) }
    var activeGesture by remember { mutableStateOf<String?>(null) }
    var isRewindingHeld by remember { mutableStateOf(false) }
    var isFastForwardHeld by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var skipStamps by remember(media.id) { mutableStateOf<List<VideoSkipStamp>>(emptyList()) }
    var skipStampLoadFinished by remember(media.id) { mutableStateOf(false) }
    var sleepTimerEndAtMs by remember { mutableStateOf<Long?>(null) }
    var sleepTimerRemainingSeconds by remember { mutableStateOf(0L) }
    var sleepTimerDurationMinutes by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val playerPreferences = remember(context) {
        context.getSharedPreferences("nsio_player_preferences", Context.MODE_PRIVATE)
    }
    var selectedOrientation by remember(context) {
        mutableStateOf(
            playerPreferences.getString("player_orientation", "Otomatis")
                ?.takeIf { it in setOf("Otomatis", "Potret", "Lanskap") } ?: "Otomatis"
        )
    }
    var subtitleScale by remember(context) {
        mutableStateOf(playerPreferences.getFloat("subtitle_scale", 1f).coerceIn(0.75f, 1.5f))
    }
    var subtitleTextColor by remember(context) {
        mutableStateOf(playerPreferences.getInt("subtitle_text_color", android.graphics.Color.WHITE))
    }
    var subtitleBackgroundColor by remember(context) {
        mutableStateOf(playerPreferences.getInt("subtitle_background_color", android.graphics.Color.TRANSPARENT))
    }
    var subtitleBottomPadding by remember(context) {
        mutableStateOf(playerPreferences.getFloat("subtitle_bottom_padding", 0.08f).coerceIn(0.02f, 0.35f))
    }
    var subtitleEdgeType by remember(context) {
        mutableStateOf(playerPreferences.getInt("subtitle_edge_type", CaptionStyleCompat.EDGE_TYPE_OUTLINE))
    }
    val externalAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: uri.lastPathSegment.orEmpty().substringAfterLast(':').substringAfterLast('/')
                require(displayName.isNotBlank()) { "Nama file audio tidak ditemukan." }
                viewModel.player.setExternalAudioTrack(uri, displayName)
                selectedPlayerSetting = null
                showTrackMenu = true
            }.onFailure {
                Toast.makeText(context, "Audio eksternal tidak dapat dibuka: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val externalSubtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty().substringAfterLast(':').substringAfterLast('/')
                val extension = displayName.substringAfterLast('.', "").lowercase()
                val mime = context.contentResolver.getType(uri) ?: when (extension) {
                    "srt" -> "application/x-subrip"
                    "vtt" -> "text/vtt"
                    "ass", "ssa" -> "text/x-ssa"
                    "ttml", "xml" -> "application/ttml+xml"
                    else -> null
                }
                viewModel.player.addExternalSubtitle(uri, displayName, mime)
                selectedPlayerSetting = null
                showTrackMenu = true
            }.onFailure {
                Toast.makeText(context, "Subtitle tidak dapat dibuka: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val castContext = remember(context) { runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val activity = remember(context) { context.findPlayerActivity() }
    LaunchedEffect(activity, selectedOrientation) {
        activity?.requestedOrientation = when (selectedOrientation) {
            "Potret" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "Lanskap" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    val originalRequestedOrientation = remember(activity) { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val originalSystemUiVisibility = remember(activity) { activity?.window?.decorView?.systemUiVisibility ?: 0 }
    var brightnessLevel by remember(activity) { mutableStateOf(if (originalBrightness < 0f) 0.5f else originalBrightness.coerceIn(0.05f, 1f)) }
    val isPhoto = media.type == "PHOTO"
    val usesNativePlayer = !isPhoto
    val isTelevision = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    // Player overlays sit above arbitrary video/photo colors, so keep their
    // typography consistently high-contrast regardless of the app theme.
    val controlTextColor = Color.White
    val controlMutedColor = Color.White.copy(alpha = 0.62f)
    val controlAccentColor = Color(0xFF8BE9FF)

    // Reset transient controls when the queue switches to another media item.
    LaunchedEffect(media.id) {
        showControls = true
        showQueue = false
        showTrackMenu = false
        controlsLocked = false
        isSeeking = false
        seekPreviewMs = media.progressMs.coerceAtLeast(0L)
        previewFrame = null
        activeGesture = null
    }

    LaunchedEffect(subtitleScale, subtitleTextColor, subtitleBackgroundColor, subtitleBottomPadding, subtitleEdgeType, playerViewRef) {
        playerPreferences.edit()
            .putFloat("subtitle_scale", subtitleScale)
            .putInt("subtitle_text_color", subtitleTextColor)
            .putInt("subtitle_background_color", subtitleBackgroundColor)
            .putFloat("subtitle_bottom_padding", subtitleBottomPadding)
            .putInt("subtitle_edge_type", subtitleEdgeType)
            .apply()
        playerViewRef?.subtitleView?.apply {
            setStyle(CaptionStyleCompat(
                subtitleTextColor,
                subtitleBackgroundColor,
                android.graphics.Color.TRANSPARENT,
                subtitleEdgeType,
                android.graphics.Color.BLACK,
                null
            ))
            setFractionalTextSize(0.055f * subtitleScale)
            setBottomPaddingFraction(subtitleBottomPadding)
        }
    }

    // Resolve OP/ED/recap markers from the same public databases used by CloudStream.
    // IDs and episode coordinates are passed through the plugin detail -> stream picker -> history.
    LaunchedEffect(media.id, media.extraInfo, playerState.durationMs) {
        skipStamps = emptyList()
        skipStampLoadFinished = false
        if (usesNativePlayer && playerState.durationMs > 0L && media.extraInfo.isNotBlank()) {
            skipStamps = VideoSkipRepository.findStamps(media.extraInfo, playerState.durationMs)
        }
        skipStampLoadFinished = true
    }

    // Keep the scrubber in sync with playback, but never overwrite the user's drag preview.
    LaunchedEffect(playerState.currentPositionMs, isSeeking) {
        if (!isSeeking) seekPreviewMs = playerState.currentPositionMs.coerceAtLeast(0L)
    }

    // Restore the user's original window brightness when leaving the player.
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                val attributes = window.attributes
                attributes.screenBrightness = originalBrightness
                window.attributes = attributes
                window.decorView.systemUiVisibility = originalSystemUiVisibility
                activity.requestedOrientation = originalRequestedOrientation
            }
        }
    }

    // Extract a real frame near the requested seek timestamp off the UI thread.
    // Some protected/HLS sources do not expose frames through MediaMetadataRetriever;
    // in that case the UI falls back to the media poster when available.
    LaunchedEffect(isSeeking, seekPreviewMs, previewUrl, previewHeaders, playerState.isLive) {
        if (!isSeeking || !usesNativePlayer || playerState.isLive) {
            previewFrame = null
        } else {
            // Never leave a frame from the previous drag timestamp on screen.
            previewFrame = null
            delay(220)
            val cacheKey = "${normalizePlaybackUrl(previewUrl)}|${previewHeaders.hashCode()}|${seekPreviewMs.coerceAtLeast(0L) / 500L}"
            val cachedFrame = PlayerFramePreviewCache.get(cacheKey)
            if (cachedFrame != null) {
                previewFrame = cachedFrame
            } else {
                val extracted = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        val uri = Uri.parse(previewUrl)
                        if (uri.scheme == "content" || uri.scheme == "file" || uri.scheme == "android.resource") {
                            retriever.setDataSource(context, uri)
                        } else {
                            retriever.setDataSource(normalizePlaybackUrl(previewUrl), previewHeaders)
                        }
                        retriever.getFrameAtTime(
                            seekPreviewMs.coerceAtLeast(0L) * 1_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { retriever.release() } catch (_: Exception) { }
                    }
                }
                if (extracted != null) {
                    PlayerFramePreviewCache.put(cacheKey, extracted)
                    previewFrame = extracted
                }
            }
        }
    }

    LaunchedEffect(sleepTimerEndAtMs) {
        val deadline = sleepTimerEndAtMs ?: run {
            sleepTimerRemainingSeconds = 0L
            sleepTimerDurationMinutes = 0
            return@LaunchedEffect
        }
        while (true) {
            val remaining = ((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            sleepTimerRemainingSeconds = remaining
            if (remaining <= 0L) {
                if (viewModel.player.playerState.value.isPlaying) viewModel.player.togglePlayPause()
                sleepTimerEndAtMs = null
                sleepTimerDurationMinutes = 0
                break
            }
            delay(1000L)
        }
    }

    LaunchedEffect(activeGesture) {
        if (activeGesture != null && activeGesture !in setOf("rewind", "fast-forward")) {
            delay(900)
            activeGesture = null
        }
    }

    LaunchedEffect(isRewindingHeld) {
        while (isRewindingHeld) {
            viewModel.player.seekBackward(500L)
            delay(250L)
        }
    }

    LaunchedEffect(isFastForwardHeld) {
        if (isFastForwardHeld) {
            val previousSpeed = playerState.playbackSpeed
            viewModel.player.setPlaybackSpeed(4f)
            try {
                while (isFastForwardHeld) delay(60L)
            } finally {
                viewModel.player.setPlaybackSpeed(previousSpeed)
            }
        }
    }

    DisposableEffect(castContext, media.id, media.url, latestCastPlayerState.selectedPluginStreamUrl) {
        val sessionManager = castContext?.sessionManager
        // Prefer the provider's selected stream, but fall back to the URL
        // currently opened in the player for ordinary catalogue/network media.
        val activeUrl = latestCastPlayerState.selectedPluginStreamUrl
            ?.takeIf { it.isNotBlank() }
            ?: latestCastMedia.url
        fun sendCurrentMedia(session: CastSession?): Boolean {
            if (session == null || activeUrl.isBlank()) return false
            val selectedStream = latestCastPlayerState.pluginStreams.firstOrNull { it.url == activeUrl }
            val parsedUri = runCatching { Uri.parse(activeUrl) }.getOrNull()
            val scheme = parsedUri?.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Toast.makeText(context, "Cast hanya mendukung media dengan URL HTTP/HTTPS yang dapat dijangkau TV.", Toast.LENGTH_LONG).show()
                return false
            }
            // Default Media Receiver cannot reproduce CloudStream request headers,
            // Referer requirements, or app-side OkHttp interceptors.
            if (selectedStream != null && (
                    selectedStream.headers.isNotEmpty() ||
                    selectedStream.referer.isNotBlank() ||
                    selectedStream.videoInterceptor != null
                )
            ) {
                Toast.makeText(context, "Sumber ini membutuhkan header/autentikasi plugin. Cast standar belum mendukungnya; pemutaran tetap berjalan di perangkat.", Toast.LENGTH_LONG).show()
                return false
            }
            val extension = runCatching { parsedUri?.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase() }.getOrDefault("")
            val streamType = selectedStream?.streamType.orEmpty().uppercase()
            val contentType = when {
                extension == "m3u8" || streamType.contains("HLS") -> "application/x-mpegURL"
                extension == "mpd" || streamType.contains("DASH") -> "application/dash+xml"
                extension == "webm" || streamType.contains("WEBM") -> "video/webm"
                extension == "mkv" || streamType.contains("MKV") -> "video/x-matroska"
                else -> "video/mp4"
            }
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, latestCastPlayerState.title.ifBlank { latestCastMedia.title })
                if (latestCastMedia.thumbnailUrl.isNotBlank() && latestCastMedia.thumbnailUrl.startsWith("http", true)) {
                    addImage(WebImage(Uri.parse(latestCastMedia.thumbnailUrl)))
                }
            }
            val info = MediaInfo.Builder(activeUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadata)
                .apply { if (latestCastPlayerState.durationMs > 0L) setStreamDuration(latestCastPlayerState.durationMs) }
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(latestCastPlayerState.currentPositionMs.coerceAtLeast(0L))
                .build()
            val remoteClient = session.remoteMediaClient ?: return false
            remoteClient.load(request)
            return true
        }
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                // A Cast session now owns audio/video output. Stop local playback
                // after loading the current item to avoid duplicate sound on the phone/TV.
                if (sendCurrentMedia(session)) viewModel.player.pause()
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionEnded(session: CastSession, error: Int) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                if (sendCurrentMedia(session)) viewModel.player.pause()
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }
        sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { session ->
            if (sendCurrentMedia(session)) viewModel.player.pause()
        }
        onDispose { sessionManager?.removeSessionManagerListener(listener, CastSession::class.java) }
    }


    // Back exits immersive fullscreen first; a second Back closes the player.
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.let { host ->
                host.window.decorView.systemUiVisibility = originalSystemUiVisibility
                host.requestedOrientation = originalRequestedOrientation
            }
        } else {
            viewModel.player.clearError()
            onClose()
        }
    }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, playerState.isPlaying, controlsInteraction) {
        if (showControls && playerState.isPlaying && playerState.error == null) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        // Swipe kiri/kanan pada area media untuk berpindah item dalam daftar,
        // termasuk perpindahan video <-> foto. Kontrol overlay tetap menerima sentuhan sendiri.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(media.id, mediaList) {
                    var horizontalDistance = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta -> horizontalDistance += delta },
                        onDragEnd = {
                            val direction = when {
                                horizontalDistance <= -72f -> 1
                                horizontalDistance >= 72f -> -1
                                else -> 0
                            }
                            if (direction != 0) {
                                if (media.type.equals("PHOTO", ignoreCase = true)) {
                                    adjacentMedia(mediaList, media.id, direction)?.let(viewModel::playMedia)
                                } else {
                                    viewModel.skipPlayback(direction)
                                }
                                showControls = true
                            }
                            horizontalDistance = 0f
                        },
                        onDragCancel = { horizontalDistance = 0f }
                    )
                }
        ) {
            if (media.type == "PHOTO") {
                AsyncImage(
                    model = media.url,
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx)
                            .inflate(R.layout.player_view_texture, null, false) as PlayerView).apply {
                            player = viewModel.player.exoPlayer
                            resizeMode = selectedResizeMode
                            playerViewRef = this
                            subtitleView?.apply {
                                setStyle(CaptionStyleCompat(
                                    subtitleTextColor,
                                    subtitleBackgroundColor,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                ))
                                setFractionalTextSize(0.055f * subtitleScale)
                            }
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { view ->
                        view.player = viewModel.player.exoPlayer
                        view.resizeMode = selectedResizeMode
                        playerViewRef = view
                        view.subtitleView?.apply {
                            setStyle(CaptionStyleCompat(
                                subtitleTextColor,
                                subtitleBackgroundColor,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            ))
                            setFractionalTextSize(0.055f * subtitleScale)
                        }
                    },
                    modifier = Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { showControls = !showControls }
                )
            }
        }


        // Gesture zones: left adjusts display brightness, right adjusts player volume.
        if (usesNativePlayer) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.28f)
                    .fillMaxHeight()
                    .pointerInput(media.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                                revealControls()
                            }
                        }
                    }
                    .pointerInput(media.id) {
                        var currentBrightness = brightnessLevel
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                currentBrightness = (currentBrightness - dragAmount / size.height.toFloat()).coerceIn(0.05f, 1f)
                                brightnessLevel = currentBrightness
                                activity?.window?.let { window ->
                                    val attributes = window.attributes
                                    attributes.screenBrightness = currentBrightness
                                    window.attributes = attributes
                                }
                                activeGesture = "brightness"
                                showControls = true
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.28f)
                    .fillMaxHeight()
                    .pointerInput(media.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null && (up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                                revealControls()
                            }
                        }
                    }
                    .pointerInput(media.id) {
                        var currentVolume = viewModel.player.exoPlayer?.volume ?: playerState.volume
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                currentVolume = (currentVolume - dragAmount / size.height.toFloat()).coerceIn(0f, 1f)
                                viewModel.player.setVolume(currentVolume)
                                activeGesture = "volume"
                                showControls = true
                            }
                        )
                    }
            )
        }

        // Hold zones: right side temporarily plays at 4×; left side rewinds at 2×.
        // Outer screen zones remain dedicated to brightness and volume gestures.
        if (usesNativePlayer) {
            Row(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.44f).fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(media.id) {
                        detectTapGestures(
                            onTap = { revealControls() },
                            onPress = {
                                val releasedBeforeLongPress = kotlinx.coroutines.withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    tryAwaitRelease()
                                }
                                if (releasedBeforeLongPress == null) {
                                    isRewindingHeld = true
                                    activeGesture = "rewind"
                                    showControls = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isRewindingHeld = false
                                        activeGesture = null
                                    }
                                }
                            }
                        )
                    }
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(media.id) {
                        detectTapGestures(
                            onTap = { revealControls() },
                            onPress = {
                                val releasedBeforeLongPress = kotlinx.coroutines.withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    tryAwaitRelease()
                                }
                                if (releasedBeforeLongPress == null) {
                                    isFastForwardHeld = true
                                    activeGesture = "fast-forward"
                                    showControls = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isFastForwardHeld = false
                                        activeGesture = null
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }

        if (activeGesture != null) {
            val isBrightness = activeGesture == "brightness"
            val isRewinding = activeGesture == "rewind"
            val isFastForwarding = activeGesture == "fast-forward"
            Box(
                modifier = Modifier
                    .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 22.dp)
                    .width(76.dp)
                    .playerLiquidGlass(shape = RoundedCornerShape(18.dp), alpha = 0.36f, borderAlpha = 0.22f, elevation = 12.dp)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = when {
                            isBrightness -> Icons.Default.Brightness6
                            isRewinding -> Icons.Default.Replay10
                            isFastForwarding -> Icons.Default.Forward10
                            playerState.volume <= 0f -> Icons.Default.VolumeOff
                            else -> Icons.Default.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = when {
                            isBrightness -> "${(brightnessLevel * 100).toInt()}%"
                            isRewinding -> "2×"
                            isFastForwarding -> "4×"
                            else -> "${(playerState.volume * 100).toInt()}%"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Buffering Liquid Glass Indicator
        if (playerState.isBuffering && playerState.error == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .playerLiquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.2f, borderAlpha = 0.5f, elevation = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = controlAccentColor,
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp
                    )
                }
            }
        }

        // Immediate player shell while a provider resolves the actual stream URL.
        if (playbackPreparationMessage != null && playerState.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    if (media.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = media.thumbnailUrl,
                            contentDescription = "Poster ${media.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(112.dp)
                                .height(158.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    CircularProgressIndicator(
                        color = controlAccentColor,
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Menyiapkan video",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playbackPreparationMessage.orEmpty(),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Custom iOS 26 Liquid Glass Player Controls Overlay
        AnimatedVisibility(
            visible = showControls && playerState.error == null && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                // Header stretches to use the available width without crowding its actions.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.96f)
                        .playerLiquidGlass(
                            shape = RoundedCornerShape(22.dp),
                            alpha = 0.2f,
                            borderAlpha = 0.42f,
                            elevation = 14.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.player.clearError()
                                onClose()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .playerLiquidGlass(shape = CircleShape, alpha = 0.22f, borderAlpha = 0.52f, elevation = 6.dp)
                                .liquidGlassTvFocusable(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = controlTextColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playerState.title.ifEmpty { media.title },
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                color = controlTextColor
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = media.category,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = controlAccentColor
                                )
                                Text(
                                    text = "• ${media.sourceType}",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = controlMutedColor
                                )
                                if (playerState.isLive) {
                                    Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5A))
                                    if (playerState.isSeekable) {
                                        Text(
                                            "KE LIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = controlAccentColor,
                                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable {
                                                viewModel.player.seekToLive()
                                            }.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (!isPhoto) {
                            IconButton(
                                onClick = {
                                    val enteringFullscreen = !isFullscreen
                                    isFullscreen = enteringFullscreen
                                    activity?.let { host ->
                                        val decor = host.window.decorView
                                        if (enteringFullscreen) {
                                            decor.systemUiVisibility = (
                                                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            )
                                            if (!isTelevision) {
                                                val videoSize = viewModel.player.exoPlayer?.videoSize
                                                val width = videoSize?.width ?: 0
                                                val height = videoSize?.height ?: 0
                                                val rotation = videoSize?.unappliedRotationDegrees ?: 0
                                                val portraitVideo = if (rotation == 90 || rotation == 270) width > height else height > width
                                                if (width > 0 && height > 0) {
                                                    host.requestedOrientation = if (portraitVideo) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                }
                                            }
                                        } else {
                                            decor.systemUiVisibility = originalSystemUiVisibility
                                            host.requestedOrientation = originalRequestedOrientation
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp).liquidGlassTvFocusable(shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Keluar layar penuh" else "Layar penuh",
                                    tint = controlTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            AndroidView(
                                factory = { viewContext ->
                                    ContextThemeWrapper(viewContext, com.example.R.style.Theme_NSiO_CastButton).let { castContext ->
                                        MediaRouteButton(castContext).apply {
                                            minimumWidth = 40
                                            minimumHeight = 40
                                            runCatching { CastButtonFactory.setUpMediaRouteButton(castContext, this) }
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            )
                            IconButton(
                                onClick = { showTrackMenu = !showTrackMenu; showQueue = false },
                                modifier = Modifier.size(40.dp).liquidGlassTvFocusable(shape = CircleShape)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Pengaturan pemutaran", tint = if (showTrackMenu) controlAccentColor else controlTextColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { showQueue = !showQueue; showTrackMenu = false },
                                modifier = Modifier
                                    .size(44.dp)
                                    .liquidGlassTvFocusable(shape = CircleShape)
                            ) {
                                Icon(
                                    Icons.Filled.List,
                                    contentDescription = "Daftar video",
                                    tint = controlTextColor,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }

                if (showTrackMenu && usesNativePlayer) {
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = 76.dp)
                            .width(300.dp)
                            .heightIn(max = 380.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.32f, borderAlpha = 0.52f, elevation = 18.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedPlayerSetting == null) {
                            Text("PENGATURAN PEMUTARAN", color = controlAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            listOf(
                                "Kecepatan pemutaran" to "${playerState.playbackSpeed}×",
                                "Rasio layar" to when (selectedResizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                    else -> "Fit"
                                },
                                "Sumber" to (playerState.pluginStreams.firstOrNull { it.url == playerState.selectedPluginStreamUrl }?.name?.ifBlank { "Sumber aktif" } ?: "Default"),
                                "Kualitas video" to (playerState.videoTracks.firstOrNull { it.selected }?.label ?: "Otomatis"),
                                "Audio" to (playerState.audioTracks.firstOrNull { it.selected }?.label ?: "Default"),
                                "Audio eksternal" to (playerState.externalAudioName ?: "Pilih file audio"),
                                "Bahasa audio" to (playerState.preferredAudioLanguage ?: "Otomatis"),
                                "Audio Delay" to (if (playerState.audioDelayMs > 0) "+${playerState.audioDelayMs} ms" else "${playerState.audioDelayMs} ms"),
                                "Subtitle Delay" to (if (playerState.subtitleDelayMs > 0) "+${playerState.subtitleDelayMs} ms" else "${playerState.subtitleDelayMs} ms"),
                                "A-B Repeat" to run {
                                    val repeatA = playerState.repeatA
                                    val repeatB = playerState.repeatB
                                    when {
                                        repeatA != null && repeatB != null -> "${formatMs(repeatA)} – ${formatMs(repeatB)}"
                                        repeatA != null -> "A ${formatMs(repeatA)} · pilih B"
                                        else -> "Mati"
                                    }
                                },
                                "Info Pemutaran" to (playerState.playbackDiagnostics.firstOrNull() ?: "Menunggu data"),
                                "Kompresor audio" to if (playerState.audioCompressorEnabled) "Aktif" else "Mati",
                                "Subtitle" to (playerState.subtitleTracks.firstOrNull { it.selected }?.label ?: if (playerState.subtitlesDisabled) "Mati" else "Otomatis"),
                                "Bahasa subtitle" to (playerState.preferredSubtitleLanguage ?: "Otomatis"),
                                "Ukuran subtitle" to "${(subtitleScale * 100).toInt()}%",
                                "Warna subtitle" to when (subtitleTextColor) {
                                    android.graphics.Color.YELLOW -> "Kuning"
                                    android.graphics.Color.CYAN -> "Cyan"
                                    android.graphics.Color.GREEN -> "Hijau"
                                    else -> "Putih"
                                },
                                "Latar subtitle" to if (subtitleBackgroundColor == android.graphics.Color.TRANSPARENT) "Transparan" else "Gelap",
                                "Posisi subtitle" to when {
                                    subtitleBottomPadding < 0.07f -> "Bawah"
                                    subtitleBottomPadding < 0.14f -> "Agak naik"
                                    subtitleBottomPadding < 0.22f -> "Tengah bawah"
                                    else -> "Tengah"
                                },
                                "Efek tepi subtitle" to when (subtitleEdgeType) {
                                    CaptionStyleCompat.EDGE_TYPE_NONE -> "Tanpa efek"
                                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> "Bayangan"
                                    CaptionStyleCompat.EDGE_TYPE_RAISED -> "Timbul"
                                    CaptionStyleCompat.EDGE_TYPE_DEPRESSED -> "Cekung"
                                    else -> "Outline"
                                },
                                "Timer tidur" to (if (sleepTimerRemainingSeconds > 0L) "${sleepTimerRemainingSeconds / 60}:${(sleepTimerRemainingSeconds % 60).toString().padStart(2, '0')}" else "Mati"),
                                "Orientasi layar" to selectedOrientation,
                                "Kunci kontrol" to if (controlsLocked) "Aktif" else "Mati"
                            ).forEach { (label, current) ->
                                Text(
                                    text = "$label  ·  $current  ›",
                                    color = controlTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                        .clickable { selectedPlayerSetting = label }
                                        .padding(horizontal = 10.dp, vertical = 11.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "‹  $selectedPlayerSetting",
                                color = controlAccentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedPlayerSetting = null }
                                    .padding(horizontal = 8.dp, vertical = 9.dp)
                            )
                            when (selectedPlayerSetting) {
                                "Timer tidur" -> {
                                    Text("Pemutaran akan dijeda otomatis saat waktu habis.", color = controlMutedColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    listOf(0, 15, 30, 45, 60, 90, 120).forEach { minutes ->
                                        val isSelected = sleepTimerDurationMinutes == minutes
                                        Text(
                                            text = (if (isSelected) "✓ " else "") + if (minutes == 0) "Mati" else "$minutes menit",
                                            color = if (isSelected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                sleepTimerDurationMinutes = minutes
                                                sleepTimerEndAtMs = if (minutes == 0) null else System.currentTimeMillis() + minutes * 60_000L
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Orientasi layar" -> listOf("Otomatis", "Potret", "Lanskap").forEach { orientation ->
                                    Text(
                                        text = (if (selectedOrientation == orientation) "✓ " else "") + orientation,
                                        color = if (selectedOrientation == orientation) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            selectedOrientation = orientation
                                            playerPreferences.edit().putString("player_orientation", orientation).apply()
                                            activity?.requestedOrientation = when (orientation) {
                                                "Potret" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                "Lanskap" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                            }
                                            selectedPlayerSetting = null
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Kunci kontrol" -> {
                                    Text("Saat aktif, sentuhan pada video tidak mengubah pemutaran. Ketuk layar untuk membuka kunci.", color = controlMutedColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    Text(
                                        text = if (controlsLocked) "Buka kunci kontrol" else "Kunci semua kontrol",
                                        color = controlAccentColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            controlsLocked = !controlsLocked
                                            selectedPlayerSetting = null
                                            showTrackMenu = false
                                        }.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                                "Kecepatan pemutaran" -> listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f).forEach { speed ->
                                    Text(
                                        text = (if (kotlin.math.abs(playerState.playbackSpeed - speed) < 0.01f) "✓ " else "") + "${speed}×",
                                        color = if (kotlin.math.abs(playerState.playbackSpeed - speed) < 0.01f) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            viewModel.player.setPlaybackSpeed(speed)
                                            selectedPlayerSetting = null
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Rasio layar" -> listOf(
                                    "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                    "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                                    "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                ).forEach { (label, mode) ->
                                    Text(
                                        text = (if (selectedResizeMode == mode) "✓ " else "") + label,
                                        color = if (selectedResizeMode == mode) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            selectedResizeMode = mode
                                            playerViewRef?.resizeMode = mode
                                            selectedPlayerSetting = null
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Sumber" -> {
                                    val availableSources = playerState.pluginStreams.distinctBy { it.url }
                                        .sortedWith(compareByDescending<com.example.data.plugin.CloudStreamStream> { it.quality }.thenBy { it.name })
                                    if (availableSources.isEmpty()) {
                                        Text("Provider tidak menyediakan pilihan sumber/server lain", color = controlMutedColor, fontSize = 11.sp)
                                    }
                                    availableSources.forEach { stream ->
                                        val qualityLabel = stream.quality.takeIf { it > 0 }?.let { "${it}p" } ?: "Kualitas otomatis"
                                        val detail = listOf(stream.name, stream.source, stream.streamType).filter { it.isNotBlank() }.distinct().joinToString(" • ")
                                        Text(
                                            text = (if (playerState.selectedPluginStreamUrl == stream.url) "✓ " else "") + qualityLabel + " · " + detail,
                                            color = if (playerState.selectedPluginStreamUrl == stream.url) controlAccentColor else controlTextColor,
                                            fontSize = 11.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.selectPluginStream(stream)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Kualitas video" -> {
                                    if (playerState.videoTracks.isEmpty()) {
                                        Text("Pilihan kualitas tidak disediakan oleh sumber ini", color = controlMutedColor, fontSize = 11.sp)
                                    }
                                    playerState.videoTracks.forEach { track ->
                                        Text(
                                            text = (if (track.selected) "✓ " else "") + track.label,
                                            color = if (track.selected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.selectTrack(track)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Audio" -> {
                                    if (playerState.audioTracks.isEmpty()) Text("Tidak ada pilihan audio tambahan dari sumber ini", color = controlMutedColor, fontSize = 11.sp)
                                    playerState.audioTracks.forEach { track ->
                                        Text(
                                            text = (if (track.selected) "✓ " else "") + track.label,
                                            color = if (track.selected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.selectTrack(track)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Audio eksternal" -> {
                                    Text(
                                        "Tambahkan file audio dari perangkat sebagai track alternatif. Video tetap memakai sumber aktif; pilih track audio yang diinginkan setelah file dimuat.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = if (playerState.externalAudioName != null) "✓ ${playerState.externalAudioName}" else "Belum ada audio eksternal",
                                        color = if (playerState.externalAudioName != null) controlAccentColor else controlMutedColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                    Text(
                                        "＋ Pilih file audio dari perangkat",
                                        color = controlAccentColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            externalAudioPicker.launch(arrayOf("audio/*", "application/octet-stream"))
                                        }.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                    if (playerState.externalAudioName != null) {
                                        Text(
                                            "Gunakan audio bawaan video",
                                            color = controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.clearExternalAudioTrack()
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                    if (playerState.audioTracks.isNotEmpty()) {
                                        Text("Pilih track audio", color = controlMutedColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        playerState.audioTracks.forEach { track ->
                                            Text(
                                                text = (if (track.selected) "✓ " else "") + track.label,
                                                color = if (track.selected) controlAccentColor else controlTextColor,
                                                fontSize = 12.sp,
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                    viewModel.player.selectTrack(track)
                                                    selectedPlayerSetting = null
                                                }.padding(horizontal = 10.dp, vertical = 9.dp)
                                            )
                                        }
                                    }
                                }
                                "Bahasa audio" -> {
                                    Text("Bahasa ini menjadi prioritas saat sumber menyediakan beberapa track audio. Pilihan disimpan untuk pemutaran berikutnya.", color = controlMutedColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    val languageOptions = (listOf<String?>(null) + playerState.audioTracks.mapNotNull { it.language } + listOfNotNull(playerState.preferredAudioLanguage)).distinct()
                                    languageOptions.forEach { language ->
                                        val selected = playerState.preferredAudioLanguage == language
                                        val label = language?.let { "${Locale.forLanguageTag(it).getDisplayName(Locale.getDefault())} ($it)" } ?: "Otomatis"
                                        Text(
                                            text = (if (selected) "✓ " else "") + label,
                                            color = if (selected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.setPreferredAudioLanguage(language)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                    if (playerState.audioTracks.isEmpty()) Text("Daftar bahasa akan muncul saat sumber memuat track audio.", color = controlMutedColor, fontSize = 10.sp)
                                }
                                "Bahasa subtitle" -> {
                                    Text("Bahasa ini menjadi prioritas subtitle saat tersedia. Pilihan Otomatis mengikuti pemilihan track bawaan sumber.", color = controlMutedColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    val languageOptions = (listOf<String?>(null) + playerState.subtitleTracks.mapNotNull { it.language } + listOfNotNull(playerState.preferredSubtitleLanguage)).distinct()
                                    languageOptions.forEach { language ->
                                        val selected = playerState.preferredSubtitleLanguage == language
                                        val label = language?.let { "${Locale.forLanguageTag(it).getDisplayName(Locale.getDefault())} ($it)" } ?: "Otomatis"
                                        Text(
                                            text = (if (selected) "✓ " else "") + label,
                                            color = if (selected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.setPreferredSubtitleLanguage(language)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                    if (playerState.subtitleTracks.isEmpty()) Text("Daftar bahasa akan muncul saat sumber memuat track subtitle.", color = controlMutedColor, fontSize = 10.sp)
                                }
                                "Kompresor audio" -> {
                                    Text(
                                        "Mengurangi lonjakan suara keras dan membantu dialog lebih terdengar. Efek bekerja pada audio PCM; format passthrough/encoded tidak diproses.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    listOf(false, true).forEach { enabled ->
                                        Text(
                                            text = (if (playerState.audioCompressorEnabled == enabled) "✓ " else "") + if (enabled) "Aktif" else "Mati",
                                            color = if (playerState.audioCompressorEnabled == enabled) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.setAudioCompressorEnabled(enabled)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Info Pemutaran" -> {
                                    Text(
                                        "Informasi teknis stream yang sedang diputar. Nilai bergantung pada metadata sumber dan decoder; tidak semua provider menyediakan bitrate atau frame rate.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    (playerState.playbackDiagnostics.ifEmpty { listOf("Informasi belum tersedia. Tunggu sampai video selesai dimuat.") }).forEach { detail ->
                                        Text(
                                            detail,
                                            color = controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                                "A-B Repeat" -> {
                                    Text(
                                        "Ulangi segmen video dari titik A sampai titik B. Tandai A, lanjutkan ke bagian akhir, lalu tandai B. Minimal jarak A–B adalah 0,5 detik.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    val repeatA = playerState.repeatA
                                    val repeatB = playerState.repeatB
                                    Text(
                                        text = if (repeatA == null) "＋ Tandai titik A" else "✓ A · ${formatMs(repeatA)}",
                                        color = if (repeatA == null) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            viewModel.player.setRepeatPointA()
                                        }.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                    Text(
                                        text = if (repeatB == null) "＋ Tandai titik B" else "✓ B · ${formatMs(repeatB)}",
                                        color = if (repeatB == null) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = repeatA != null) { viewModel.player.setRepeatPointB() }
                                            .padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                    if (repeatA != null && repeatB != null) {
                                        Text("Loop aktif · ${formatMs(repeatA)} – ${formatMs(repeatB)}", color = controlAccentColor, fontSize = 11.sp)
                                    }
                                    Text(
                                        "Hapus A–B",
                                        color = controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            viewModel.player.clearABRepeat()
                                        }.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                                "Audio Delay" -> {
                                    Text(
                                        "Atur sinkronisasi suara terhadap gambar. Nilai positif membuat suara lebih lambat; nilai negatif memajukan suara.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    (-500..500 step 50).forEach { delayMs ->
                                        Text(
                                            text = (if (playerState.audioDelayMs == delayMs) "✓ " else "") +
                                                if (delayMs > 0) "+${delayMs} ms" else "${delayMs} ms",
                                            color = if (playerState.audioDelayMs == delayMs) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.setAudioDelay(delayMs)
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Subtitle Delay" -> {
                                    Text(
                                        "Sinkronisasi waktu subtitle eksternal dari plugin atau file lokal. Positif menampilkan subtitle lebih lambat; negatif lebih cepat. Track subtitle bawaan di dalam video tidak terpengaruh.",
                                        color = controlMutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(-500, -100, -50, 50, 100, 500).forEach { step ->
                                            Text(
                                                text = if (step > 0) "+${step}" else "${step}",
                                                color = controlTextColor,
                                                fontSize = 11.sp,
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.player.setSubtitleDelay(playerState.subtitleDelayMs + step) }
                                                    .padding(vertical = 9.dp)
                                            )
                                        }
                                    }
                                    listOf(-5000, -2000, -1000, -500, -250, -100, 0, 100, 250, 500, 1000, 2000, 5000).forEach { delayMs ->
                                        Text(
                                            text = (if (playerState.subtitleDelayMs == delayMs) "✓ " else "") +
                                                if (delayMs > 0) "+${delayMs} ms" else "${delayMs} ms",
                                            color = if (playerState.subtitleDelayMs == delayMs) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.setSubtitleDelay(delayMs)
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Subtitle" -> {
                                    Text(
                                        text = "＋ Muat subtitle dari perangkat",
                                        color = controlAccentColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            externalSubtitlePicker.launch(arrayOf(
                                                "text/*",
                                                "application/x-subrip",
                                                "application/octet-stream"
                                            ))
                                        }.padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                    Text(
                                        text = if (playerState.subtitlesDisabled) "✓ Mati" else "Mati",
                                        color = if (playerState.subtitlesDisabled) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            viewModel.player.disableSubtitles()
                                            selectedPlayerSetting = null
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                    if (playerState.subtitleTracks.isEmpty()) Text("Tidak ada subtitle dari sumber ini", color = controlMutedColor, fontSize = 11.sp)
                                    playerState.subtitleTracks.forEach { track ->
                                        Text(
                                            text = (if (track.selected) "✓ " else "") + track.label,
                                            color = if (track.selected) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                viewModel.player.selectTrack(track)
                                                selectedPlayerSetting = null
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Ukuran subtitle" -> {
                                    listOf(0.75f, 0.9f, 1f, 1.15f, 1.3f, 1.5f).forEach { scale ->
                                        Text(
                                            text = (if (kotlin.math.abs(subtitleScale - scale) < 0.01f) "✓ " else "") + "${(scale * 100).toInt()}%",
                                            color = if (kotlin.math.abs(subtitleScale - scale) < 0.01f) controlAccentColor else controlTextColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                                subtitleScale = scale
                                                playerViewRef?.subtitleView?.setFractionalTextSize(0.055f * scale)
                                            }.padding(horizontal = 10.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                "Warna subtitle" -> listOf(
                                    "Putih" to android.graphics.Color.WHITE,
                                    "Kuning" to android.graphics.Color.YELLOW,
                                    "Cyan" to android.graphics.Color.CYAN,
                                    "Hijau" to android.graphics.Color.GREEN
                                ).forEach { (label, color) ->
                                    Text(
                                        text = (if (subtitleTextColor == color) "✓ " else "") + label,
                                        color = if (subtitleTextColor == color) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            subtitleTextColor = color
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Latar subtitle" -> listOf(
                                    "Transparan" to android.graphics.Color.TRANSPARENT,
                                    "Gelap" to android.graphics.Color.argb(165, 0, 0, 0)
                                ).forEach { (label, color) ->
                                    Text(
                                        text = (if (subtitleBackgroundColor == color) "✓ " else "") + label,
                                        color = if (subtitleBackgroundColor == color) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            subtitleBackgroundColor = color
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Posisi subtitle" -> listOf(
                                    "Bawah" to 0.04f,
                                    "Agak naik" to 0.10f,
                                    "Tengah bawah" to 0.18f,
                                    "Tengah" to 0.27f
                                ).forEach { (label, padding) ->
                                    Text(
                                        text = (if (kotlin.math.abs(subtitleBottomPadding - padding) < 0.01f) "✓ " else "") + label,
                                        color = if (kotlin.math.abs(subtitleBottomPadding - padding) < 0.01f) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            subtitleBottomPadding = padding
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                                "Efek tepi subtitle" -> listOf(
                                    "Outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    "Bayangan" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                    "Timbul" to CaptionStyleCompat.EDGE_TYPE_RAISED,
                                    "Cekung" to CaptionStyleCompat.EDGE_TYPE_DEPRESSED,
                                    "Tanpa efek" to CaptionStyleCompat.EDGE_TYPE_NONE
                                ).forEach { (label, edgeType) ->
                                    Text(
                                        text = (if (subtitleEdgeType == edgeType) "✓ " else "") + label,
                                        color = if (subtitleEdgeType == edgeType) controlAccentColor else controlTextColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                            subtitleEdgeType = edgeType
                                        }.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showQueue) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 76.dp)
                            .width(300.dp)
                            .heightIn(max = 360.dp)
                            .playerLiquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = 0.3f,
                                borderAlpha = 0.55f,
                                elevation = 18.dp
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        val queueItems = playbackQueue
                        if (queueItems.isEmpty()) {
                            Text("Belum ada video di antrean", color = controlMutedColor, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                        queueItems.forEach { next ->
                            Text(
                                text = next.title,
                                color = if (next.id == media.id) controlAccentColor else controlTextColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showQueue = false
                                        viewModel.playMedia(next)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp)
                            )
                        }
                    }
                }

                // Compact primary transport controls stay in the bottom dock.
                if (usesNativePlayer) {
                    val hasPrevious = viewModel.canSkipPlayback(-1)
                    val hasNext = viewModel.canSkipPlayback(1)

                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.10f, borderAlpha = 0.16f, elevation = 3.dp)
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Keep each 48dp hit target comfortable while shrinking the visible glass controls.
                        IconButton(onClick = { viewModel.skipPlayback(-1) }, enabled = hasPrevious, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Box(
                                modifier = Modifier.size(30.dp).playerLiquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.28f, elevation = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Media sebelumnya", tint = if (hasPrevious) controlTextColor else controlMutedColor, modifier = Modifier.size(19.dp))
                            }
                        }
                        IconButton(onClick = { viewModel.player.seekBackward(10000L) }, enabled = playerState.isSeekable, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Box(
                                modifier = Modifier.size(34.dp).playerLiquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.28f, elevation = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 Detik", tint = controlTextColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(
                            onClick = { viewModel.player.togglePlayPause() },
                            modifier = Modifier.size(48.dp)
                                .liquidGlassTvFocusable(shape = CircleShape, scaleFocused = 1.05f)
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).playerLiquidGlass(shape = CircleShape, alpha = 0.24f, borderAlpha = 0.38f, elevation = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.player.seekForward(10000L) }, enabled = playerState.isSeekable, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Box(
                                modifier = Modifier.size(34.dp).playerLiquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.28f, elevation = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Maju 10 Detik", tint = controlTextColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { viewModel.skipPlayback(1) }, enabled = hasNext, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Box(
                                modifier = Modifier.size(30.dp).playerLiquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.28f, elevation = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Media berikutnya", tint = if (hasNext) controlTextColor else controlMutedColor, modifier = Modifier.size(19.dp))
                            }
                        }
                    }


                }

                // CloudStream-style manual skip prompt: only appears while the playhead is inside a known segment.
                val activeSkipStamp = if (skipStampLoadFinished && usesNativePlayer && !playerState.isLive) {
                    skipStamps.firstOrNull { stamp ->
                        playerState.currentPositionMs >= stamp.startMs &&
                            playerState.currentPositionMs < (stamp.endMs - 1_000L).coerceAtLeast(stamp.startMs + 1L)
                    }
                } else null
                if (activeSkipStamp != null && !controlsLocked) {
                    Button(
                        onClick = {
                            viewModel.player.seekTo(activeSkipStamp.endMs.coerceAtMost(playerState.durationMs))
                            revealControls()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 22.dp, bottom = 164.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.94f),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "Lewati ${activeSkipStamp.label}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                // Floating frame preview: extract the actual video frame at the seek timestamp.
                if (isSeeking && usesNativePlayer && !playerState.isLive && playerState.durationMs > 0L) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 174.dp)
                            // Keep the compact preview inside the screen. At the far right,
                            // its right edge aligns with the timeline edge (so it sits left of the playhead).
                            .offset(x = ((screenWidth - 36.dp) * ((if (playerState.durationMs > 0L) seekPreviewMs.toFloat() / playerState.durationMs.toFloat() else 0.5f) - 0.5f) * 0.88f).coerceIn(-((screenWidth - 36.dp) / 2f - 54.dp), (screenWidth - 36.dp) / 2f - 54.dp))
                            .width(108.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(12.dp), alpha = 0.26f, borderAlpha = 0.18f, elevation = 8.dp)
                            .padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewFrame != null || media.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = previewFrame ?: media.thumbnailUrl,
                                    contentDescription = "Thumbnail video",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                            }
                            Text(
                                text = formatMs(seekPreviewMs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Live sources without a seekable DVR window have no meaningful timeline.
                // Minimal timeline: a 1.5dp glass rail with a tiny red scrubber dot.
                // Keep the full 48dp interaction target without enlarging the visible control.
                if (usesNativePlayer && (!playerState.isLive || playerState.isSeekable)) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 104.dp)
                        .fillMaxWidth(0.88f)
                        .padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentFormatted = formatMs(playerState.currentPositionMs)
                            val totalFormatted = formatMs(playerState.durationMs)
                            Text(
                                text = "$currentFormatted / $totalFormatted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = controlTextColor
                            )
                        }

                        val total = playerState.durationMs.coerceAtLeast(1L)
                        val displayedPosition = if (isSeeking) seekPreviewMs else playerState.currentPositionMs
                        val currentRatio = (displayedPosition.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Ultra-slim 1.5dp Liquid Glass rail; the 48dp touch target stays separate.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .playerLiquidGlass(
                                        shape = RoundedCornerShape(50),
                                        alpha = 0.10f,
                                        borderAlpha = 0.12f,
                                        elevation = 1.dp
                                    )
                            )

                            Slider(
                                value = currentRatio,
                                enabled = playerState.isSeekable && playerState.durationMs > 0L,
                                onValueChange = { ratio ->
                                    isSeeking = true
                                    seekPreviewMs = (ratio * total).toLong().coerceIn(0L, total)
                                },
                                onValueChangeFinished = {
                                    if (isSeeking) viewModel.player.seekTo(seekPreviewMs)
                                    isSeeking = false
                                },
                                colors = SliderDefaults.colors(
                                    // White played rail, translucent unplayed rail.
                                    // The thumb is drawn separately as a tiny red dot.
                                    thumbColor = Color.Transparent,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                                    disabledThumbColor = Color.Transparent,
                                    disabledActiveTrackColor = Color.White.copy(alpha = 0.55f),
                                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    // Keep the visible progress rail very thin while preserving the full touch area.
                                    .graphicsLayer { scaleY = 0.35f }
                                    .liquidGlassTvFocusable(shape = RoundedCornerShape(8.dp))
                            )

                            // Explicit circular seek head: never a vertical Material thumb.
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                drawCircle(
                                    color = Color(0xFFFF3030),
                                    radius = 3.dp.toPx(),
                                    center = Offset(
                                        x = size.width * currentRatio,
                                        y = size.height / 2f
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Locked overlay blocks accidental touches while keeping a one-tap unlock affordance.
        if (controlsLocked && usesNativePlayer) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)).clickable { controlsLocked = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.playerLiquidGlass(shape = RoundedCornerShape(18.dp), alpha = 0.34f, borderAlpha = 0.28f, elevation = 8.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Kontrol terkunci", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Kontrol terkunci · ketuk untuk membuka", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Liquid Glass Error Overlay
        if (playerState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .playerLiquidGlass(
                            shape = RoundedCornerShape(26.dp),
                            alpha = 0.22f,
                            borderAlpha = 0.6f,
                            elevation = 20.dp
                        )
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Kendala Pemutaran",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Kendala Pemutaran Media",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playerState.error ?: "Tidak dapat memutar video.",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }

                        // Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (playerState.isFallbackAvailable) {
                                Button(
                                    onClick = {
                                        viewModel.player.playFallback(media.title)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(16.dp)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanPrimary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Putar Video Demo (bukan sumber asli)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.player.retry()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Lagi", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.player.clearError()
                                        onClose()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tutup", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Player-scoped Liquid Glass surface.
 *
 * Intentionally local to PlayerScreen: the app-wide legacy liquidGlass()
 * modifier remains untouched so Home and every other screen keep their theme.
 * The layered translucent fill and specular gradient border provide a glass
 * treatment without changing the video/photo rendering surface underneath.
 */
private fun Modifier.playerLiquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.White,
    alpha: Float = 0.20f,
    borderAlpha: Float = 0.38f,
    elevation: Dp = 10.dp
): Modifier {
    val fill = Brush.linearGradient(
        colors = listOf(
            Color(0xFF142033).copy(alpha = (0.30f + alpha * 0.38f).coerceIn(0f, 0.82f)),
            tint.copy(alpha = (0.08f + alpha * 0.26f).coerceIn(0f, 0.48f)),
            Color(0xFF24364B).copy(alpha = (0.22f + alpha * 0.34f).coerceIn(0f, 0.78f))
        )
    )
    val specularEdge = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (borderAlpha * 1.15f).coerceIn(0f, 0.82f)),
            Color(0xFFBDEBFF).copy(alpha = (borderAlpha * 0.72f).coerceIn(0f, 0.68f)),
            Color.White.copy(alpha = (borderAlpha * 0.16f).coerceIn(0f, 0.28f)),
            Color.White.copy(alpha = (borderAlpha * 0.58f).coerceIn(0f, 0.68f))
        )
    )
    return this
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(fill)
        .border(0.9.dp, specularEdge, shape)
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}


private fun Context.findPlayerActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
