package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import android.graphics.Typeface
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

private const val PRIMARY_PROFILE_AVATAR_URL = "https://raw.githubusercontent.com/andikune-ux/icon/main/1795557171709.jpg"
private const val RUBIK_PUDDLES_FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/rubikpuddles/RubikPuddles-Regular.ttf"

@Composable
private fun rememberTikTodDisplayFont(): FontFamily? {
    val context = LocalContext.current.applicationContext
    var family by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(Unit) {
        family = withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.cacheDir, "rubik-puddles-regular.ttf")
                if (!target.exists() || target.length() == 0L) {
                    val temp = File(context.cacheDir, "rubik-puddles-regular.tmp")
                    URL(RUBIK_PUDDLES_FONT_URL).openConnection().apply {
                        connectTimeout = 10_000
                        readTimeout = 15_000
                    }.getInputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }
                FontFamily(Typeface.createFromFile(target))
            }.getOrNull()
        }
    }
    return family
}

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTodScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findTikTodActivity() }
    val originalOrientation = remember(hostActivity) { hostActivity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val owner = LocalLifecycleOwner.current
    val activeProfile by viewModel.activeProfile.collectAsState()
    val primaryDisplayFont = rememberTikTodDisplayFont()
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
    var feed by remember { mutableStateOf(emptyList<com.example.data.model.MediaItem>()) }
    var index by remember { mutableIntStateOf(0) }
    var sessionInitialized by remember(activeProfile?.id) { mutableStateOf(false) }
    var retryUsed by remember { mutableStateOf(false) }
    var countedView by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var isCleanMode by remember { mutableStateOf(false) }
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
    // Category navigation is ready now; Mau Mu and Yang Mana will receive their own feeds later.
    var selectedTikTodCategory by remember { mutableIntStateOf(1) }
    val yangManaPreferences = remember(context) { context.getSharedPreferences("tiktod_yang_mana", Context.MODE_PRIVATE) }
    var yangManaNames by remember { mutableStateOf((1..9).map { index -> yangManaPreferences.getString("name_$index", "Profil $index") ?: "Profil $index" }) }
    var editingYangManaIndex by remember { mutableIntStateOf(-1) }
    var editingYangManaName by remember { mutableStateOf("") }
    var showYangManaSettings by remember { mutableStateOf(false) }
    var resumeAfterCategory by remember { mutableStateOf(false) }
    val tikTodCategories = listOf("Mau Mu", "Untuk Mu", "Yang Mana")
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeAnimationScope = rememberCoroutineScope()
    var swipeAnimationJob by remember { mutableStateOf<Job?>(null) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    // zoomScale is relative to the initial cover-filled frame (1x). It may
    // shrink to the full-fit boundary when the user pinches out.
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var videoAspectRatio by remember { mutableFloatStateOf(0f) }
    val media = feed.getOrNull(index)
    var hasRenderedFirstFrame by remember(media?.id) { mutableStateOf(false) }
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (hasRenderedFirstFrame) 0f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "TikTod thumbnail fade"
    )
    val exoPlayer = viewModel.player.exoPlayer

    DisposableEffect(exoPlayer, media?.id) {
        val expectedMediaId = media?.id
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (expectedMediaId != null && viewModel.currentlyPlaying.value?.id == expectedMediaId) {
                    hasRenderedFirstFrame = true
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (expectedMediaId != null && viewModel.currentlyPlaying.value?.id == expectedMediaId &&
                    videoSize.width > 0 && videoSize.height > 0
                ) {
                    var aspect = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio /
                        videoSize.height.toFloat()
                    if (videoSize.unappliedRotationDegrees % 180 != 0) aspect = 1f / aspect
                    videoAspectRatio = aspect.takeIf { it.isFinite() && it > 0f } ?: 0f
                }
            }
        }
        exoPlayer?.addListener(listener)
        onDispose { exoPlayer?.removeListener(listener) }
    }

    LaunchedEffect(selectedTikTodCategory) {
        if (selectedTikTodCategory != 1) {
            resumeAfterCategory = playerState.isPlaying
            if (playerState.isPlaying) viewModel.player.pause()
        } else if (resumeAfterCategory) {
            viewModel.player.togglePlayPause()
            resumeAfterCategory = false
        }
    }

    LaunchedEffect(media?.id) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
        videoAspectRatio = 0f
    }

    // Keep a small progressive preload for exactly one upcoming Drive video.
    // The shared player cache is reused when the item becomes the active video.
    LaunchedEffect(media?.id, index, feed) {
        val nextItem = feed.getOrNull(index + 1) ?: return@LaunchedEffect
        if (MediaSourceClassifier.isGoogleDriveUrl(nextItem.url)) {
            viewModel.player.preloadDriveMedia(nextItem.url, "${nextItem.profileId}:${nextItem.id}")
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setTikTodModeActive(true)
        onDispose {
            viewModel.player.cancelDrivePreload()
            viewModel.setTikTodModeActive(false)
        }
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
    LaunchedEffect(eligible, activeProfile?.id) {
        val profileId = activeProfile?.id ?: return@LaunchedEffect
        if (eligible.isEmpty()) return@LaunchedEffect

        val pendingNewIds = store.syncEligibleVideos(profileId, eligible.map { it.id })
        if (!sessionInitialized) {
            feed = TikTodFeedPolicy.sessionStartOrder(
                items = eligible,
                pendingNewIds = pendingNewIds,
                previousVideoId = store.lastVideoId(profileId)
            )
            sessionInitialized = true
            if (feed.isNotEmpty()) startAt(0)
        } else {
            // Do not interrupt the current video. Newly discovered items are
            // inserted immediately after it and remain prioritized until started.
            val queuedNewItems = eligible
                .filter { it.id in pendingNewIds && feed.none { queued -> queued.id == it.id } }
                .shuffled()
            if (queuedNewItems.isNotEmpty()) {
                feed = feed.take(index + 1) + queuedNewItems + feed.drop(index + 1)
            }
        }
    }
    LaunchedEffect(media?.id, currentMedia?.id) {
        if (media != null && currentMedia?.id != media.id) startAt(index)
    }
    LaunchedEffect(playerState.isPlaying, media?.id, activeProfile?.id) {
        val profileId = activeProfile?.id
        if (media != null && profileId != null && playerState.isPlaying) {
            // "Viewed" means playback actually started, not that the video completed.
            store.markVideoStarted(profileId, media.id)
            if (!countedView) {
                views = store.addView(media.id)
                countedView = true
            }
        }
    }
    LaunchedEffect(playerState.error, media?.id) {
        if (media != null && playerState.error != null && !retryUsed) {
            // Keep the existing single automatic retry. If it fails, the UI
            // exposes the source error plus explicit Retry and Skip actions.
            retryUsed = true
            delay(900)
            if (viewModel.player.playerState.value.error != null &&
                feed.getOrNull(index)?.id == media.id
            ) {
                viewModel.player.retry()
            }
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
                    viewModel.player.cancelDrivePreload()
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
        if (backAt != 0L && now - backAt < 1_500L) {
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
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    swipeAnimationJob?.cancel()
                    swipeAnimationJob = null
                    val pointerId = down.id
                    var latestDragOffset = 0f
                    var latestHorizontalOffset = 0f
                    var multiTouch = false
                    var verticalDragStarted = false
                    var horizontalDragStarted = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > 1) {
                            multiTouch = true
                            latestDragOffset = 0f
                            swipeOffset = 0f
                        }
                        val tracked = event.changes.firstOrNull { it.id == pointerId }
                        if (!multiTouch && tracked != null && tracked.pressed) {
                            val delta = tracked.position - down.position
                            val vertical = delta.y
                            val horizontal = delta.x
                            if (!horizontalDragStarted && kotlin.math.abs(vertical) > viewConfiguration.touchSlop &&
                                kotlin.math.abs(vertical) > kotlin.math.abs(horizontal) * 1.2f
                            ) {
                                verticalDragStarted = true
                                latestDragOffset = vertical
                                swipeOffset = vertical
                                tracked.consume()
                            } else if (!verticalDragStarted && kotlin.math.abs(horizontal) > viewConfiguration.touchSlop &&
                                kotlin.math.abs(horizontal) > kotlin.math.abs(vertical) * 1.2f
                            ) {
                                horizontalDragStarted = true
                                latestHorizontalOffset = horizontal
                                tracked.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!multiTouch && horizontalDragStarted) {
                        val horizontalThreshold = (size.width * 0.18f).coerceAtLeast(viewConfiguration.touchSlop * 2f)
                        if (kotlin.math.abs(latestHorizontalOffset) >= horizontalThreshold) {
                            // Swipe right from the default center tab opens Yang Mana;
                            // swipe left opens Mau Mu. Do not wrap at either edge.
                            selectedTikTodCategory = (selectedTikTodCategory + if (latestHorizontalOffset > 0f) 1 else -1)
                                .coerceIn(0, tikTodCategories.lastIndex)
                        }
                    } else if (!multiTouch && verticalDragStarted) {
                        val distanceThreshold = (size.height * 0.18f).coerceAtLeast(viewConfiguration.touchSlop * 2f)
                        val offset = latestDragOffset
                        val refreshAtTop = offset > distanceThreshold && index == 0 && feed.isNotEmpty()
                        val target = when {
                            offset < -distanceThreshold && index < feed.lastIndex -> index + 1
                            offset < -distanceThreshold && index == feed.lastIndex && feed.isNotEmpty() -> 0
                            offset > distanceThreshold && index > 0 -> index - 1
                            else -> null
                        }
                        if (target != null || refreshAtTop) {
                            val direction = if (offset < 0f) -1f else 1f
                            swipeAnimationJob = swipeAnimationScope.launch {
                                Animatable(offset).animateTo(
                                    direction * size.height.toFloat(),
                                    tween(durationMillis = 220)
                                ) { swipeOffset = value }
                                if (refreshAtTop) {
                                    // Pulling down beyond the first item refreshes only
                                    // the feed order; all other swipe directions stay unchanged.
                                    val refreshedFeed = TikTodFeedPolicy.newCycle(eligible, media?.id)
                                    if (refreshedFeed.isNotEmpty()) {
                                        feed = refreshedFeed
                                        startAt(0)
                                    }
                                } else if (target == 0 && index == feed.lastIndex) {
                                    feed = TikTodFeedPolicy.newCycle(eligible, feed.firstOrNull()?.id)
                                    if (feed.isNotEmpty()) startAt(0)
                                } else {
                                    target?.let(::startAt)
                                }
                                swipeOffset = 0f
                            }
                        } else {
                            swipeAnimationJob = swipeAnimationScope.launch {
                                Animatable(offset).animateTo(0f, tween(durationMillis = 180)) {
                                    swipeOffset = value
                                }
                                swipeOffset = 0f
                            }
                        }
                    } else if (multiTouch) {
                        swipeOffset = 0f
                    }
                }
            }
    ) {
        val previewMedia = when {
            swipeOffset < 0f -> feed.getOrNull(index + 1) ?: if (index == feed.lastIndex) feed.firstOrNull() else null
            swipeOffset > 0f -> feed.getOrNull(index - 1)
            else -> null
        }
        if (previewMedia != null && kotlin.math.abs(swipeOffset) > 0.5f) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        translationY = if (swipeOffset < 0f) size.height + swipeOffset
                        else -size.height + swipeOffset
                    }
                    .background(Color.Black)
            ) {
                if (previewMedia.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = previewMedia.thumbnailUrl,
                        contentDescription = previewMedia.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = .48f)).padding(18.dp)
                ) {
                    Text(previewMedia.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = swipeOffset
            }
        ) {
        if (!isCleanMode) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 18.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tikTodCategories.forEachIndexed { categoryIndex, category ->
                    val selected = selectedTikTodCategory == categoryIndex
                    Column(
                        modifier = Modifier.clickable { selectedTikTodCategory = categoryIndex },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                            fontSize = if (selected) 15.sp else 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .width(if (selected) 22.dp else 0.dp)
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color.White else Color.Transparent)
                        )
                    }
                }
            }
        }
        if (media == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum ada video Google Drive", color = Color.White)
                TextButton(onClick = onClose) { Text("Kembali") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        val containerAspect = if (size.height > 0f) size.width / size.height else 1f
                        val aspect = videoAspectRatio.takeIf { it > 0f } ?: containerAspect
                        val coverScale = maxOf(aspect / containerAspect, containerAspect / aspect)
                        scaleX = coverScale * zoomScale
                        scaleY = coverScale * zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                    }
                    .pointerInput(media.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var previousCentroid: Offset? = null
                            var previousDistance = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val first = pressed[0].position
                                    val second = pressed[1].position
                                    val centroid = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
                                    val dx = first.x - second.x
                                    val dy = first.y - second.y
                                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                    val oldCentroid = previousCentroid
                                    if (oldCentroid != null && previousDistance > 0f && distance > 0f) {
                                        val containerAspect = size.width.toFloat() / size.height.toFloat()
                                        val aspect = videoAspectRatio.takeIf { it > 0f } ?: containerAspect
                                        val coverScale = maxOf(aspect / containerAspect, containerAspect / aspect)
                                        val minimumZoom = 1f / coverScale
                                        val previousScale = zoomScale
                                        val nextScale = (previousScale * (distance / previousDistance))
                                            .coerceIn(minimumZoom, 5f)
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val factor = nextScale / previousScale

                                        // Keep the image point under the fingers stable while pinching,
                                        // and translate by the centroid movement for two-finger panning.
                                        val nextOffset = zoomOffset * factor +
                                            (centroid - center) - (oldCentroid - center) * factor

                                        // PlayerView uses FIT. These are the dimensions of that fitted
                                        // image before the cover transform, so bounds follow the real
                                        // video aspect ratio rather than the full screen rectangle.
                                        val fitWidth = if (aspect >= containerAspect) {
                                            size.width.toFloat()
                                        } else {
                                            size.height.toFloat() * aspect
                                        }
                                        val fitHeight = fitWidth / aspect
                                        val effectiveScale = coverScale * nextScale
                                        val maxX = ((fitWidth * effectiveScale - size.width) / 2f).coerceAtLeast(0f)
                                        val maxY = ((fitHeight * effectiveScale - size.height) / 2f).coerceAtLeast(0f)

                                        zoomScale = nextScale
                                        zoomOffset = Offset(
                                            nextOffset.x.coerceIn(-maxX, maxX),
                                            nextOffset.y.coerceIn(-maxY, maxY)
                                        )
                                    }
                                    previousCentroid = centroid
                                    previousDistance = distance
                                    // Consume only multi-touch input. One-finger tap, hold and feed swipe
                                    // remain owned by their existing parent gesture handlers.
                                    pressed.forEach { it.consume() }
                                } else {
                                    // Reset the baseline whenever the pointer count drops to one.
                                    previousCentroid = null
                                    previousDistance = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                factory = { PlayerView(it).apply {
                    useController = false
                    player = viewModel.player.exoPlayer
                    // FIT keeps the original uncropped frame available; the Compose cover transform
                    // recreates the current fill-screen appearance and lets pinch-out reveal the edges.
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                } },
                update = { it.player = viewModel.player.exoPlayer }
            )
            // Keep the destination thumbnail above the player until Media3 reports
            // that an actual video frame has been rendered. This prevents PlayerView
            // from exposing its black shutter while the next Drive stream prepares.
            if (thumbnailAlpha > 0.001f) {
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = thumbnailAlpha }
                        .background(Color.Black)
                ) {
                    if (media.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = media.thumbnailUrl,
                            contentDescription = "${media.title} - pratinjau",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            if (!isCleanMode && !playerState.isPlaying && hasRenderedFirstFrame) Icon(
                Icons.Default.PlayArrow, null,
                Modifier.align(Alignment.Center).size(54.dp),
                tint = Color.White
            )
            if (!isCleanMode && playerState.isBuffering) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = .2f)
                )
            }
            if (playerState.error != null && retryUsed) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                        .fillMaxWidth(.88f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = .88f))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Kendala pemutaran media", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        playerState.error ?: "Sumber video gagal diproses.",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.player.retry() }
                        ) { Text("Coba Lagi", color = Color.White) }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { next() }
                        ) { Text("Lewati") }
                    }
                }
            }
            if (!isCleanMode && playerState.playbackEnded && playerState.error == null) {
                Text(
                    "Video selesai • Geser ke atas untuk video berikutnya",
                    modifier = Modifier.align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = .72f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }


            // TikTok-style creator identity and caption: compact, anchored bottom-left.
            if (!isCleanMode) {
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 82.dp, bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val isPrimaryProfile = activeProfile?.isDefault == true
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = .20f),
                        modifier = Modifier.size(if (isPrimaryProfile) 34.dp else 30.dp)
                    ) {
                        if (isPrimaryProfile) {
                            AsyncImage(
                                model = PRIMARY_PROFILE_AVATAR_URL,
                                contentDescription = "Avatar Kunti Meong Collection",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(activeProfile?.avatarEmoji?.takeIf { it.isNotBlank() } ?: "👤", fontSize = 17.sp)
                            }
                        }
                    }
                    Text(
                        if (isPrimaryProfile) "Kunti Meong Collection" else activeProfile?.name?.takeIf { it.isNotBlank() } ?: "Profil",
                        color = Color.White,
                        fontSize = if (isPrimaryProfile) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (isPrimaryProfile) primaryDisplayFont else null,
                        maxLines = 1
                    )
                    if (isPrimaryProfile) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Verified,
                            contentDescription = "Terverifikasi",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(15.dp)
                        )
                    }
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
            }

            // Separate TikTok-style action buttons: each icon has its own background and spacing.
            if (!isCleanMode) {
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 90.dp)
                    .width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = .30f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Visibility, "Dilihat", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }
                    Text(views.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(42.dp).clickable { likes = store.addLike(media.id) },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = .30f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Favorite, "Suka", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Text(likes.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(42.dp).clickable {
                            comments = store.comments(media.id)
                            showComments = true
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = .30f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Comment, "Komentar", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }
                    Text(comments.size.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    modifier = Modifier.size(42.dp).clickable { showOptions = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .30f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, "Opsi", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }

            }

            if (isCleanMode) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 14.dp)
                        .size(34.dp)
                        .clickable { isCleanMode = false },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .38f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Keluar Clean", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Thin TikTok-style seek bar. All positions are mapped to the real media duration.
            val duration = playerState.durationMs.coerceAtLeast(0L)
            val shownPosition = if (isSeeking) seekPosition else playerState.currentPositionMs.toFloat()
            val seekFraction = if (duration > 0L) {
                (shownPosition / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Canvas(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 22.dp)
                    .height(14.dp)
                    .pointerInput(media.id, duration, playerState.isSeekable) {
                        fun updateFromX(x: Float) {
                            if (duration > 0L && playerState.isSeekable && size.width > 0) {
                                val fraction = (x / size.width).coerceIn(0f, 1f)
                                seekPosition = fraction * duration.toFloat()
                                isSeeking = true
                            }
                        }
                        detectTapGestures { point ->
                            updateFromX(point.x)
                            if (duration > 0L && playerState.isSeekable) {
                                viewModel.player.exoPlayer?.let { player ->
                                    player.seekTo(seekPosition.toLong())
                                    player.play()
                                }
                                isSeeking = false
                            }
                        }
                    }
                    .pointerInput(media.id, duration, playerState.isSeekable) {
                        fun updateFromX(x: Float) {
                            if (duration > 0L && playerState.isSeekable && size.width > 0) {
                                seekPosition = (x / size.width).coerceIn(0f, 1f) * duration.toFloat()
                                isSeeking = true
                            }
                        }
                        detectDragGestures(
                            onDragStart = { point -> updateFromX(point.x) },
                            onDrag = { change, _ ->
                                change.consume()
                                updateFromX(change.position.x)
                            },
                            onDragEnd = {
                                if (duration > 0L && isSeeking) {
                                    viewModel.player.exoPlayer?.let { player ->
                                    player.seekTo(seekPosition.toLong())
                                    player.play()
                                }
                                }
                                isSeeking = false
                            },
                            onDragCancel = { isSeeking = false }
                        )
                    }
            ) {
                val centerY = size.height / 2f
                val startX = 0f
                val endX = size.width
                drawLine(
                    color = Color.White.copy(alpha = .38f),
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                val progressX = size.width * seekFraction
                drawLine(
                    color = Color.White,
                    start = Offset(startX, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(progressX, centerY)
                )
            }
            if (isSeeking && duration > 0L) {
                BoxWithConstraints(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 22.dp)
                        .height(36.dp)
                ) {
                    val bubbleWidth = 48.dp
                    val bubbleX = (maxWidth * seekFraction - bubbleWidth / 2)
                        .coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp))
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).offset(x = bubbleX),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = .82f)
                    ) {
                        Text(
                            formatTikTodTime(seekPosition.toLong()),
                            modifier = Modifier.width(bubbleWidth).padding(horizontal = 3.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                    }
                    Text(
                        formatTikTodTime(duration),
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        }
    }


    if (selectedTikTodCategory == 2) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 18.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tikTodCategories.forEachIndexed { categoryIndex, category ->
                    val selected = selectedTikTodCategory == categoryIndex
                    Column(
                        modifier = Modifier.clickable { selectedTikTodCategory = categoryIndex },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                            fontSize = if (selected) 15.sp else 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        Box(
                            Modifier.width(if (selected) 22.dp else 0.dp)
                                .height(2.dp).clip(CircleShape)
                                .background(if (selected) Color.White else Color.Transparent)
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 66.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(9) { profileIndex ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)
                                .clickable {
                                    editingYangManaIndex = profileIndex
                                    editingYangManaName = yangManaNames[profileIndex]
                                },
                            shape = RoundedCornerShape(23.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
                            shadowElevation = 8.dp
                        ) {
                            Box {
                                AsyncImage(
                                    model = "https://picsum.photos/seed/nsio-yangmana-${profileIndex + 1}/640/640",
                                    contentDescription = yangManaNames[profileIndex],
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))
                                        )
                                    )
                                )
                                Text(
                                    yangManaNames[profileIndex],
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .padding(start = 12.dp, end = 38.dp, bottom = 11.dp),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit nama ${yangManaNames[profileIndex]}",
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                        .padding(end = 10.dp, bottom = 10.dp).size(20.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)
                            .clickable { showYangManaSettings = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Pengaturan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (editingYangManaIndex in 0..8) {
        AlertDialog(
            onDismissRequest = { editingYangManaIndex = -1 },
            title = { Text("Ubah nama profil") },
            text = {
                OutlinedTextField(
                    value = editingYangManaName,
                    onValueChange = { editingYangManaName = it },
                    singleLine = true,
                    label = { Text("Nama profil") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val index = editingYangManaIndex
                    val updated = yangManaNames.toMutableList()
                    updated[index] = editingYangManaName.trim().ifBlank { "Profil ${index + 1}" }
                    yangManaNames = updated
                    yangManaPreferences.edit().putString("name_${index + 1}", updated[index]).apply()
                    editingYangManaIndex = -1
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { editingYangManaIndex = -1 }) { Text("Batal") }
            },
            containerColor = Color(0xFF202126),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showYangManaSettings) {
        AlertDialog(
            onDismissRequest = { showYangManaSettings = false },
            title = { Text("Pengaturan Yang Mana") },
            text = { Text("Pengaturan profil akan ditambahkan setelah isi dan fungsinya ditentukan.") },
            confirmButton = {
                TextButton(onClick = { showYangManaSettings = false }) { Text("Tutup") }
            },
            containerColor = Color(0xFF202126),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
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
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { showOptions = false; isCleanMode = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Clean Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Sembunyikan semua elemen selain video dan seek bar", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Close, contentDescription = "Aktifkan Clean Mode", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = Color.White.copy(alpha = .12f))
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

private fun formatTikTodTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", totalSeconds / 60L, seconds)
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