package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.ui.MainViewModel
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary
import kotlinx.coroutines.delay
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
    var showControls by remember { mutableStateOf(true) }
    var showQueue by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf(media.progressMs.coerceAtLeast(0L)) }
    val isPhoto = media.type == "PHOTO"
    val isGoogleDriveVideo = !isPhoto &&
        media.url.startsWith("https://drive.google.com/file/d/") &&
        media.url.endsWith("/preview")
    val usesNativePlayer = !isPhoto && !isGoogleDriveVideo
    // Player controls sit on frosted-glass panels; follow the active theme so
    // labels remain readable when light mode renders those panels in white.
    val controlTextColor = MaterialTheme.colorScheme.onSurface
    val controlMutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val controlAccentColor = MaterialTheme.colorScheme.primary

    // Reset transient controls when the queue switches to another media item.
    LaunchedEffect(media.id) {
        showControls = true
        showQueue = false
        isSeeking = false
        seekPreviewMs = media.progressMs.coerceAtLeast(0L)
    }

    // Keep the scrubber in sync with playback, but never overwrite the user's drag preview.
    LaunchedEffect(playerState.currentPositionMs, isSeeking) {
        if (!isSeeking) seekPreviewMs = playerState.currentPositionMs.coerceAtLeast(0L)
    }

    // Handle Android system back
    BackHandler {
        viewModel.player.clearError()
        onClose()
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
        // ExoPlayer Surface View atau gambar lokal/remote
        if (media.type == "PHOTO") {
            AsyncImage(
                model = media.url,
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGoogleDriveVideo) {
            // Drive's /preview endpoint is an HTML player, not a raw MP4 stream.
            // Use WebView so Google's own public-file player can handle playback.
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = WebViewClient()
                        // Track the requested URL separately: Google may redirect the
                        // WebView URL, and comparing webView.url causes reload loops.
                        tag = media.url
                        loadUrl(media.url)
                    }
                },
                update = { webView ->
                    if (webView.tag != media.url) {
                        webView.tag = media.url
                        webView.loadUrl(media.url)
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.removeAllViews()
                    webView.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.player.exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { showControls = !showControls }
            )
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
                        .liquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.2f, borderAlpha = 0.5f, elevation = 16.dp),
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
                        .liquidGlass(
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
                                .clip(CircleShape)
                                .background(if ((MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) < 1.5f) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
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
                                onClick = { showQueue = !showQueue },
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

                if (showQueue) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 76.dp)
                            .width(300.dp)
                            .heightIn(max = 360.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = 0.3f,
                                borderAlpha = 0.55f,
                                elevation = 18.dp
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        val queueItems = mediaList.filter { it.type != "PHOTO" }.take(20)
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

                // Center Liquid Glass Orb Controls
                if (isGoogleDriveVideo) {
                    Text(
                        text = "Gunakan kontrol di pemutar Google Drive untuk putar, jeda, dan pencarian waktu",
                        color = controlMutedColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                            .liquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.2f, borderAlpha = 0.35f)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
                if (usesNativePlayer) Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = { viewModel.player.seekBackward(10000L) },
                        enabled = playerState.isSeekable,
                        modifier = Modifier
                            .size(56.dp)
                            .liquidGlass(shape = CircleShape, alpha = 0.2f, borderAlpha = 0.5f)
                            .liquidGlassTvFocusable(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Mundur 10 Detik",
                            tint = controlTextColor,
                            modifier = Modifier.size(29.dp)
                        )
                    }

                    // Play / Pause Crystal Orb
                    IconButton(
                        onClick = { viewModel.player.togglePlayPause() },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(CyanPrimary, Color(0xFF38BDF8), Color(0xFFA855F7))
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.linearGradient(listOf(Color.White, Color.Transparent)),
                                CircleShape
                            )
                            .liquidGlassTvFocusable(shape = CircleShape, scaleFocused = 1.15f)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { viewModel.player.seekForward(10000L) },
                        enabled = playerState.isSeekable,
                        modifier = Modifier
                            .size(56.dp)
                            .liquidGlass(shape = CircleShape, alpha = 0.2f, borderAlpha = 0.5f)
                            .liquidGlassTvFocusable(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Maju 10 Detik",
                            tint = controlTextColor,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }

                // Bottom Timeline Scrubber Capsule
                if (usesNativePlayer) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            alpha = 0.18f,
                            borderAlpha = 0.4f,
                            elevation = 12.dp
                        )
                        .padding(horizontal = 20.dp, vertical = 13.dp)
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

                            Text(
                                text = if (playerState.isSeekable) "GESER UNTUK MENCARI POSISI" else "LIVE / TIDAK DAPAT DI-SEEK",
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = controlAccentColor,
                                fontWeight = FontWeight.SemiBold
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
                                thumbColor = controlAccentColor,
                                activeTrackColor = controlAccentColor,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
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
                        .liquidGlass(
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
