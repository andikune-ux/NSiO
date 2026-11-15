package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItem
import com.example.player.adjacentMedia
import com.example.player.normalizePlaybackUrl
import com.example.ui.MainViewModel
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    media: MediaItem,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val playerState by viewModel.player.playerState.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var showQueue by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var selectedResizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf(media.progressMs.coerceAtLeast(0L)) }
    var previewFrame by remember(media.id) { mutableStateOf<Bitmap?>(null) }
    var activeGesture by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
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
        isSeeking = false
        seekPreviewMs = media.progressMs.coerceAtLeast(0L)
        previewFrame = null
        activeGesture = null
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
    LaunchedEffect(isSeeking, seekPreviewMs, media.url) {
        if (!isSeeking || !usesNativePlayer) {
            previewFrame = null
        } else {
            // Clear immediately on every drag update; never display a frame from
            // the previous timestamp while the next frame is being extracted.
            previewFrame = null
            delay(220)
            val extracted = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = Uri.parse(media.url)
                    if (uri.scheme == "content" || uri.scheme == "file" || uri.scheme == "android.resource") {
                        retriever.setDataSource(context, uri)
                    } else {
                        retriever.setDataSource(normalizePlaybackUrl(media.url), mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36 NetStream/2.0"))
                    }
                    retriever.getFrameAtTime(seekPreviewMs.coerceAtLeast(0L) * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) { }
                }
            }
            if (extracted != null) previewFrame = extracted
        }
    }

    LaunchedEffect(activeGesture) {
        if (activeGesture != null) {
            delay(900)
            activeGesture = null
        }
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
    LaunchedEffect(showControls, playerState.isPlaying) {
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
                                if (usesNativePlayer) viewModel.skipPlayback(direction)
                                else adjacentMedia(mediaList, media.id, direction)?.let(viewModel::playMedia)
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
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { view ->
                        view.player = viewModel.player.exoPlayer
                        view.resizeMode = selectedResizeMode
                        playerViewRef = view
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

        if (activeGesture != null) {
            val isBrightness = activeGesture == "brightness"
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
                        imageVector = if (isBrightness) Icons.Default.Brightness6 else if (playerState.volume <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isBrightness) "${(brightnessLevel * 100).toInt()}%" else "${(playerState.volume * 100).toInt()}%",
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

        // Custom iOS 26 Liquid Glass Player Controls Overlay
        AnimatedVisibility(
            visible = showControls && playerState.error == null,
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
                        Text("RASIO LAYAR", color = controlAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf(
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
                                }.padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                        }
                        Text("KUALITAS VIDEO", color = controlAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (playerState.videoTracks.isEmpty()) {
                            Text("Pilihan kualitas tidak disediakan oleh sumber ini", color = controlMutedColor, fontSize = 11.sp)
                        } else {
                            playerState.videoTracks.forEach { track ->
                                Text(
                                    text = (if (track.selected) "✓ " else "") + track.label,
                                    color = if (track.selected) controlAccentColor else controlTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { viewModel.player.selectTrack(track) }.padding(horizontal = 8.dp, vertical = 7.dp)
                                )
                            }
                        }
                        Text("AUDIO", color = controlAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        if (playerState.audioTracks.isEmpty()) {
                            Text("Tidak ada pilihan track audio tambahan", color = controlMutedColor, fontSize = 11.sp)
                        } else {
                            playerState.audioTracks.forEach { track ->
                                Text(
                                    text = (if (track.selected) "✓ " else "") + track.label,
                                    color = if (track.selected) controlAccentColor else controlTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { viewModel.player.selectTrack(track) }.padding(horizontal = 8.dp, vertical = 7.dp)
                                )
                            }
                        }
                        Text("SUBTITLE", color = controlAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            text = if (playerState.subtitlesDisabled) "✓ Nonaktif" else "Nonaktifkan subtitle",
                            color = if (playerState.subtitlesDisabled) controlAccentColor else controlTextColor,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { viewModel.player.disableSubtitles() }.padding(horizontal = 8.dp, vertical = 7.dp)
                        )
                        if (playerState.subtitleTracks.isEmpty()) {
                            Text("Tidak ada subtitle tertanam pada sumber ini", color = controlMutedColor, fontSize = 11.sp)
                        } else {
                            playerState.subtitleTracks.forEach { track ->
                                Text(
                                    text = (if (track.selected) "✓ " else "") + track.label,
                                    color = if (track.selected) controlAccentColor else controlTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { viewModel.player.selectTrack(track) }.padding(horizontal = 8.dp, vertical = 7.dp)
                                )
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
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.10f, borderAlpha = 0.16f, elevation = 3.dp)
                            .padding(horizontal = 1.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.skipPlayback(-1) }, enabled = hasPrevious, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Media sebelumnya", tint = if (hasPrevious) controlTextColor else controlMutedColor, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.player.seekBackward(10000L) }, enabled = playerState.isSeekable, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 Detik", tint = controlTextColor, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { viewModel.player.togglePlayPause() },
                            modifier = Modifier.size(48.dp)
                                .liquidGlassTvFocusable(shape = CircleShape, scaleFocused = 1.05f)
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.player.seekForward(10000L) }, enabled = playerState.isSeekable, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(Icons.Default.Forward10, contentDescription = "Maju 10 Detik", tint = controlTextColor, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.skipPlayback(1) }, enabled = hasNext, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Media berikutnya", tint = if (hasNext) controlTextColor else controlMutedColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Secondary playback options remain compact and separate from transport.
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 164.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(13.dp), alpha = 0.09f, borderAlpha = 0.14f, elevation = 3.dp)
                            .padding(horizontal = 1.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val currentIndex = speeds.indexOfFirst { kotlin.math.abs(it - playerState.playbackSpeed) < 0.01f }
                            viewModel.player.setPlaybackSpeed(speeds[(currentIndex + 1).mod(speeds.size)])
                        }, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Text("${playerState.playbackSpeed}×", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = controlAccentColor)
                        }
                        IconButton(onClick = { viewModel.togglePlaybackShuffle() }, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Acak antrean", tint = if (playerState.shuffleEnabled) controlAccentColor else controlTextColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.player.toggleRepeatMode() }, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(if (playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Mode ulang", tint = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) controlAccentColor else controlTextColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.player.toggleMute() }, modifier = Modifier.size(48.dp).liquidGlassTvFocusable(shape = CircleShape)) {
                            Icon(if (playerState.volume <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = "Bisukan suara", tint = controlTextColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Floating frame preview: extract the actual video frame at the seek timestamp.
                if (isSeeking && usesNativePlayer && playerState.durationMs > 0L) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 174.dp)
                            .width(132.dp)
                            .playerLiquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.30f, borderAlpha = 0.20f, elevation = 12.dp)
                            .padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
                            }
                            Text(
                                text = formatMs(seekPreviewMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Timeline stays near the top edge, directly below the media header.
                if (usesNativePlayer) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 104.dp)
                        .fillMaxWidth(0.88f)
                        .playerLiquidGlass(
                            shape = RoundedCornerShape(12.dp),
                            alpha = 0.12f,
                            borderAlpha = 0.16f,
                            elevation = 5.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = controlTextColor
                            )

                        }

                        val total = playerState.durationMs.coerceAtLeast(1L)
                        val displayedPosition = if (isSeeking) seekPreviewMs else playerState.currentPositionMs
                        val currentRatio = (displayedPosition.toFloat() / total.toFloat()).coerceIn(0f, 1f)

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
                                // Liquid Glass timeline: white played track, blue seek head,
                                // and a subtle frosted-white unplayed track.
                                thumbColor = Color(0xFF48BFFF),
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                                disabledThumbColor = Color(0xFF48BFFF).copy(alpha = 0.55f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.55f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .liquidGlassTvFocusable(shape = RoundedCornerShape(8.dp))
                        )
                    }
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
