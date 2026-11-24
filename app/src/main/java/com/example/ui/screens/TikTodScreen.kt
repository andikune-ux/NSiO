package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
@Composable
fun TikTodScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val activeProfile by viewModel.activeProfile.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val playlists by viewModel.sourcePlaylists.collectAsState()
    val playerState by viewModel.player.playerState.collectAsState()
    val currentMedia by viewModel.currentlyPlaying.collectAsState()
    val store = remember(context) { TikTodLocalStore(context) }
    val driveIds = remember(playlists) { playlists.filter { it.sourceType.equals("DRIVE", true) || MediaSourceClassifier.isGoogleDriveUrl(it.url) }.mapTo(hashSetOf()) { it.id } }
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
    var backAt by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(0f) }
    val media = feed.getOrNull(index)

    fun startAt(target: Int) {
        if (feed.isEmpty()) return
        index = target.coerceIn(0, feed.lastIndex)
        retryUsed = false
        countedView = false
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
            if (newItems.isNotEmpty()) feed = feed + newItems
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
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnReturn = playerState.isPlaying
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
                modifier = Modifier.fillMaxSize(),
                factory = { PlayerView(it).apply { useController = false; player = viewModel.player.exoPlayer; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM } },
                update = { it.player = viewModel.player.exoPlayer }
            )
            if (!playerState.isPlaying) Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.Center).size(56.dp), tint = Color.White)
            Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                IconButton(onClick = { viewModel.closePlayer(); onClose() }) { Icon(Icons.Default.ArrowBack, "Keluar", tint = Color.White) }
                Text(media.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(activeProfile?.name.orEmpty(), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { likes = store.addLike(media.id) }) { Icon(Icons.Default.Favorite, "Suka", tint = Color.White) }
                Text(likes.toString(), color = Color.White)
                IconButton(onClick = { comments = store.comments(media.id); showComments = true }) { Icon(Icons.Default.Comment, "Komentar", tint = Color.White) }
                Text(comments.size.toString(), color = Color.White)
                Text("◉", color = Color.White)
                Text(views.toString(), color = Color.White)
                IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, "Opsi", tint = Color.White) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 72.dp, bottom = 18.dp)) {
                Text("${index + 1} / ${feed.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { if (playerState.durationMs > 0) (playerState.currentPositionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = .25f)
                )
            }
        }
    }
    if (showOptions) AlertDialog(
        onDismissRequest = { showOptions = false },
        title = { Text("Pengaturan TikTod") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kecepatan")
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { value -> FilterChip(selected = speed == value, onClick = {
                            speed = value; store.setSpeed(value); viewModel.player.setPlaybackSpeed(value)
                        }, label = { Text("${value}×") }) }
                    }
                }
                Divider()
                Text("Kualitas sumber")
                listOf("Original", "2160p", "1440p", "1080p", "720p", "480p", "360p").forEach { value ->
                    Row(Modifier.fillMaxWidth().clickable { quality = value; store.setQuality(value) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(quality == value, onClick = { quality = value; store.setQuality(value) })
                        Text(value)
                    }
                }
                Text("Resolusi tidak dipaksakan. Jika file Drive hanya menyediakan satu kualitas, kualitas asli yang diputar.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { showOptions = false }) { Text("Selesai") } }
    )
    if (showComments && media != null) AlertDialog(
        onDismissRequest = { showComments = false },
        title = { Text("Komentar") },
        text = {
            Column {
                OutlinedTextField(draft, { draft = it }, label = { Text("Tulis komentar") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(comments) { i, comment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comment, Modifier.weight(1f))
                            TextButton(onClick = { comments = store.deleteComment(media.id, i) }) { Text("Hapus") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (draft.isNotBlank()) { comments = store.addComment(media.id, draft); draft = "" }
        }) { Text("Kirim") } },
        dismissButton = { TextButton(onClick = { showComments = false }) { Text("Tutup") } }
    )
}
