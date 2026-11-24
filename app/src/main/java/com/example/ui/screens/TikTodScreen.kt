package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import com.example.data.model.MediaSourceClassifier
import com.example.ui.MainViewModel
import com.example.tiktok.TikTodFeedPolicy
import com.example.tiktok.TikTodLocalStore
import kotlinx.coroutines.delay

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTodScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findTikTodActivity() }
    val originalOrientation = remember(hostActivity) { hostActivity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val owner = LocalLifecycleOwner.current
    val activeProfile by viewModel.activeProfile.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val playlists by viewModel.sourcePlaylists.collectAsState()
    val playerState by viewModel.player.playerState.collectAsState()
    val latestIsPlaying = rememberUpdatedState(playerState.isPlaying)
    val currentMedia by viewModel.currentlyPlaying.collectAsState()
    val store = remember(context) { TikTodLocalStore(context) }
    val driveIds = remember(playlists) {
        playlists.filter { it.sourceType.equals("DRIVE", true) || MediaSourceClassifier.isGoogleDriveUrl(it.url) }
            .mapTo(hashSetOf()) { it.id }
    }
    val eligible = remember(mediaList, activeProfile?.id, driveIds) {
        activeProfile?.id?.let { TikTodFeedPolicy.eligible(mediaList, it, driveIds) }.orEmpty()
    }
    var feed by remember { mutableStateOf(TikTodFeedPolicy.newCycle(eligible)) }
    var index by remember { mutableIntStateOf(0) }
    var retryUsed by remember { mutableStateOf(false) }
    var countedView by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(emptyList<String>()) }
    var likes by remember { mutableIntStateOf(0) }
    var views by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(store.speed()) }
    var quality by remember { mutableStateOf(store.quality()) }
    var resumeOnReturn by remember { mutableStateOf(false) }
    var landscapeMode by remember { mutableStateOf(false) }
    var backAt by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(0f) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val media = feed.getOrNull(index)

    LaunchedEffect(media?.id) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

    fun startAt(target: Int) {
        if (feed.isEmpty()) return
        index = target.coerceIn(0, feed.lastIndex)
        retryUsed = false
        countedView = false
        isSeeking = false
        val item = feed[index]
        viewModel.playMedia(item.copy(progressMs = 0L))
        viewModel.player.setPlaybackSpeed(speed)
        likes = store.likeCount(item.id)
        views = store.viewCount(item.id)
        comments = store.comments(item.id)
    }
    fun next() {
        if (index + 1 < feed.size) startAt(index + 1)
        else {
            feed = TikTodFeedPolicy.newCycle(eligible, feed.firstOrNull()?.id)
            if (feed.isNotEmpty()) startAt(0)
        }
    }
    LaunchedEffect(eligible) {
        if (feed.isEmpty() && eligible.isNotEmpty()) {
            feed = TikTodFeedPolicy.newCycle(eligible)
            startAt(0)
        } else {
            val newItems = eligible.filter { item -> feed.none { it.id == item.id } }
            if (newItems.isNotEmpty()) feed = feed.take(index + 1) + newItems + feed.drop(index + 1)
        }
    }
    LaunchedEffect(media?.id, currentMedia?.id) {
        if (media != null && currentMedia?.id != media.id) startAt(index)
    }
    LaunchedEffect(playerState.isPlaying, media?.id) {
        if (media != null && playerState.isPlaying && !countedView) {
            views = store.addView(media.id)
            countedView = true
        }
    }
    LaunchedEffect(playerState.playbackEnded, playerState.error, media?.id) {
        if (media != null && playerState.playbackEnded) next()
        else if (media != null && playerState.error != null) {
            if (!retryUsed) {
                retryUsed = true
                delay(500)
                viewModel.player.retry()
            } else next()
        }
    }
    DisposableEffect(hostActivity) {
        onDispose { hostActivity?.requestedOrientation = originalOrientation }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnReturn = latestIsPlaying.value
                    viewModel.pausePlaybackForBackground()
                }
                Lifecycle.Event.ON_START -> if (resumeOnReturn) {
                    viewModel.player.togglePlayPause()
                    resumeOnReturn = false
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - backAt < 650L) {
            viewModel.closePlayer()
            onClose()
        } else {
            backAt = now
            Toast.makeText(context, "Tekan Back sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(media?.id, speed) {
                detectTapGestures(
                    onTap = { viewModel.player.togglePlayPause() },
                    onLongPress = { viewModel.player.setPlaybackSpeed(3f) },
                    onPress = { try { awaitRelease() } finally { viewModel.player.setPlaybackSpeed(speed) } }
                )
            }
            .pointerInput(feed, index) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (drag < -55f) { if (index < feed.lastIndex) startAt(index + 1) else next() }
                        else if (drag > 55f && index > 0) startAt(index - 1)
                        drag = 0f
                    }
                )
            }
    ) {
        if (media == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum ada video Google Drive", color = Color.White)
                TextButton(onClick = onClose) { Text("Kembali") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                    }
                    .pointerInput(media.id) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val previousScale = zoomScale
                            val nextScale = (previousScale * zoom).coerceIn(1f, 5f)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val factor = nextScale / previousScale
                            val nextOffset = (zoomOffset + center - centroid) * factor + centroid - center + pan
                            val maxX = size.width * (nextScale - 1f) / 2f
                            val maxY = size.height * (nextScale - 1f) / 2f
                            zoomScale = nextScale
                            zoomOffset = if (nextScale <= 1f) Offset.Zero else Offset(
                                nextOffset.x.coerceIn(-maxX, maxX),
                                nextOffset.y.coerceIn(-maxY, maxY)
                            )
                        }
                    },
                factory = { PlayerView(it).apply {
                    useController = false
                    player = viewModel.player.exoPlayer
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } },
                update = { it.player = viewModel.player.exoPlayer }
            )
            if (!playerState.isPlaying) Icon(
                Icons.Default.PlayArrow, null,
                Modifier.align(Alignment.Center).size(54.dp),
                tint = Color.White
            )
            if (playerState.isBuffering) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = .2f)
                )
            }

            // TikTok-style creator identity and caption: compact, anchored bottom-left.
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 82.dp, bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .20f), modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(activeProfile?.avatarEmoji?.takeIf { it.isNotBlank() } ?: "👤", fontSize = 17.sp)
                        }
                    }
                    Text(
                        activeProfile?.name?.takeIf { it.isNotBlank() } ?: "Profil",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Text(
                    media.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Text(
                    "${index + 1} / ${feed.size}",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp
                )
            }

            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                IconButton(onClick = { likes = store.addLike(media.id) }) {
                    Icon(Icons.Default.Favorite, "Suka", tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Text(likes.toString(), color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                IconButton(onClick = { comments = store.comments(media.id); showComments = true }) {
                    Icon(Icons.Default.Comment, "Komentar", tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Text(comments.size.toString(), color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Text("◉", color = Color.White, fontSize = 25.sp)
                Text(views.toString(), color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                IconButton(onClick = { showOptions = true }) {
                    Icon(Icons.Default.MoreVert, "Opsi", tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }

            // A real seek control; position is committed only when the drag finishes.
            val duration = playerState.durationMs.coerceAtLeast(0L)
            val shownPosition = if (isSeeking) seekPosition else playerState.currentPositionMs.toFloat()
            Slider(
                value = if (duration > 0L) shownPosition.coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = {
                    if (duration > 0L) {
                        isSeeking = true
                        seekPosition = it
                    }
                },
                onValueChangeFinished = {
                    if (duration > 0L && isSeeking) viewModel.player.exoPlayer?.seekTo(seekPosition.toLong())
                    isSeeking = false
                },
                enabled = duration > 0L && playerState.isSeekable,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 22.dp)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = .35f)
                )
            )
        }
    }

    if (showComments && media != null) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = Color(0xFF171717),
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = .28f),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = .35f)) }
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 560.dp)
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    "${comments.size} komentar",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (comments.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Belum ada komentar. Jadilah yang pertama.", color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        itemsIndexed(comments) { i, comment ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = Color(0xFF343434), modifier = Modifier.size(34.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text("👤", fontSize = 17.sp) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(activeProfile?.name ?: "Pengguna", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(comment, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                                    Text("Komentar lokal", color = Color.White.copy(alpha = .45f), fontSize = 10.sp)
                                }
                                TextButton(
                                    onClick = { comments = store.deleteComment(media.id, i) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text("Hapus", color = Color.White.copy(alpha = .6f), fontSize = 10.sp) }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Tambahkan komentar...", color = Color.White.copy(alpha = .5f), fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = .45f),
                            unfocusedBorderColor = Color.White.copy(alpha = .2f),
                            cursorColor = Color.White
                        )
                    )
                    TextButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            comments = store.addComment(media.id, draft)
                            draft = ""
                        }
                    ) { Text("Kirim", color = Color(0xFF80CBC4), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            containerColor = Color(0xFF171717),
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = .28f),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = .35f)) }
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Pengaturan video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Orientasi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Mode layar video", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        landscapeMode = !landscapeMode
                        hostActivity?.requestedOrientation = if (landscapeMode) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }) { Text(if (landscapeMode) "Potret" else "Lanskap", color = Color(0xFF80CBC4)) }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .12f))
                Text("Kecepatan pemutaran", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { value ->
                            FilterChip(
                                selected = speed == value,
                                onClick = {
                                    speed = value
                                    store.setSpeed(value)
                                    viewModel.player.setPlaybackSpeed(value)
                                },
                                label = { Text("${value}×", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF315D59),
                                    selectedLabelColor = Color.White,
                                    labelColor = Color.White.copy(alpha = .85f)
                                )
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .12f))
                Text("Kualitas sumber", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                listOf("Original", "2160p", "1440p", "1080p", "720p", "480p", "360p").chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .clickable { quality = value; store.setQuality(value) }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = quality == value,
                                    onClick = { quality = value; store.setQuality(value) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF80CBC4), unselectedColor = Color.White.copy(alpha = .6f))
                                )
                                Text(value, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Text(
                    "Pilihan kualitas mengikuti track yang tersedia. Jika Drive hanya menyediakan satu resolusi, video tetap diputar pada kualitas asli.",
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                TextButton(
                    onClick = { showOptions = false },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Selesai", color = Color(0xFF80CBC4)) }
            }
        }
    }
}

private fun Context.findTikTodActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
