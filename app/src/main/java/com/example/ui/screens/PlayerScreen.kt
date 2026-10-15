package com.example.ui.screens

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
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
    var showControls by remember { mutableStateOf(true) }

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
        // ExoPlayer Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player.exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        )

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
                        color = CyanPrimary,
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
                    .padding(16.dp)
            ) {
                // Top Header: Floating Liquid Glass Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            alpha = 0.18f,
                            borderAlpha = 0.4f,
                            elevation = 12.dp
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.player.clearError()
                                onClose()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .liquidGlassTvFocusable(shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = playerState.title.ifEmpty { media.title },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = media.category,
                                    fontSize = 10.sp,
                                    color = CyanPrimary
                                )
                                Text(
                                    text = "•  ${media.sourceType}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // Center Liquid Glass Orb Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = { viewModel.player.seekBackward(10000L) },
                        modifier = Modifier
                            .size(50.dp)
                            .liquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.45f)
                            .liquidGlassTvFocusable(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Mundur 10 Detik",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Play / Pause Crystal Orb
                    IconButton(
                        onClick = { viewModel.player.togglePlayPause() },
                        modifier = Modifier
                            .size(66.dp)
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
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { viewModel.player.seekForward(10000L) },
                        modifier = Modifier
                            .size(50.dp)
                            .liquidGlass(shape = CircleShape, alpha = 0.18f, borderAlpha = 0.45f)
                            .liquidGlassTvFocusable(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Maju 10 Detik",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Bottom Timeline Scrubber Capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            alpha = 0.18f,
                            borderAlpha = 0.4f,
                            elevation = 12.dp
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "iOS 26 Liquid Cinema Engine",
                                fontSize = 9.sp,
                                color = CyanPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val total = playerState.durationMs.coerceAtLeast(1L)
                        val currentRatio = (playerState.currentPositionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                        Slider(
                            value = currentRatio,
                            onValueChange = { ratio ->
                                val target = (ratio * total).toLong()
                                viewModel.player.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
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
                                        .height(48.dp)
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
                                        text = "Putar Stream Demo / Cadangan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
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
                                        .height(44.dp)
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
                                        .height(44.dp)
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
