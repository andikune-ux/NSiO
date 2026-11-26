package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.player.CS3IPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

private data class TikTodPlaybackSession(
    val queue: List<com.example.data.model.MediaItem>,
    val mediaId: String,
    val positionMs: Long
)

private const val PRIMARY_PROFILE_AVATAR_URL = "https://raw.githubusercontent.com/andikune-ux/icon/main/1795557171709.jpg"
private const val RUBIK_PUDDLES_FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/rubikpuddles/RubikPuddles-Regular.ttf"
private const val POPPINS_BOLD_FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/poppins/Poppins-Bold.ttf"

@Composable
private fun rememberTikTodNameFont(): FontFamily? {
    val context = LocalContext.current.applicationContext
    var family by remember { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(Unit) {
        family = withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.cacheDir, "poppins-bold.ttf")
                if (!target.exists() || target.length() == 0L) {
                    val temp = File(context.cacheDir, "poppins-bold.tmp")
                    URL(POPPINS_BOLD_FONT_URL).openConnection().apply {
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
    val yangManaNameFont = rememberTikTodNameFont()
    val mediaList by viewModel.mediaList.collectAsState()
    val playlists by viewModel.sourcePlaylists.collectAsState()
    val playerState by viewModel.player.playerState.collectAsState()
    val latestIsPlaying = rememberUpdatedState(playerState.isPlaying)
     val store = remember(context) { TikTodLocalStore(context) }
    val driveIds = remember(playlists) {
        playlists.filter { it.sourceType.equals("DRIVE", true) || MediaSourceClassifier.isGoogleDriveUrl(it.url) }
            .mapTo(hashSetOf()) { it.id }
    }
    val eligible = remember(mediaList, activeProfile?.id, driveIds) {
        activeProfile?.id?.let { TikTodFeedPolicy.eligible(mediaList, it, driveIds) }.orEmpty()
    }
    var avatarAssignments by remember(activeProfile?.id) {
        mutableStateOf(activeProfile?.id?.let(store::avatarAssignments).orEmpty())
    }
    val effectiveAvatarAssignments = remember(avatarAssignments, eligible) {
        avatarAssignments.toMutableMap().apply {
            eligible.groupBy(TikTodFeedPolicy::assignmentKey).forEach { (canonicalKey, matchingItems) ->
                val owner = avatarAssignments[canonicalKey]
                    ?: matchingItems.firstNotNullOfOrNull { avatarAssignments[it.id] }
                if (!owner.isNullOrBlank()) put(canonicalKey, owner)
            }
        }
    }
    var selectedAvatarId by remember(activeProfile?.id) {
        mutableStateOf(activeProfile?.id?.let(store::selectedAvatar) ?: "p1:balqyz")
    }
    var selectedUnassignedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedAssignedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignmentTargetAvatarId by remember { mutableStateOf("p1:balqyz") }
    var showAssignmentTargetMenu by remember { mutableStateOf(false) }
    var activeAssignmentPreviewId by remember { mutableStateOf<String?>(null) }
    val assignmentPreviewScope = rememberCoroutineScope()
    val assignmentPreviewPlayer = remember(context) {
        CS3IPlayer(context.applicationContext, assignmentPreviewScope, manageMediaSessionService = false)
    }
    val assignmentPreviewState by assignmentPreviewPlayer.playerState.collectAsState()
    var showAvatarAppliedDialog by remember { mutableStateOf(false) }
    var appliedAvatarLabel by remember { mutableStateOf("") }
    var feed by remember { mutableStateOf(emptyList<com.example.data.model.MediaItem>()) }
    var forYouFeed by remember(activeProfile?.id) { mutableStateOf(emptyList<com.example.data.model.MediaItem>()) }
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
    // TikTod keeps category navigation independent from the active video feed.
    var selectedTikTodCategory by remember { mutableIntStateOf(2) }
    val categorySource = remember(eligible, selectedTikTodCategory, selectedAvatarId, effectiveAvatarAssignments) {
        when (selectedTikTodCategory) {
            0 -> TikTodFeedPolicy.forAvatar(eligible, effectiveAvatarAssignments, selectedAvatarId)
            1 -> TikTodFeedPolicy.jedagJedug(eligible, effectiveAvatarAssignments)
            2 -> eligible
            else -> emptyList()
        }
    }
    // Avatar names and artwork are sourced from the user's icon repository.
    val yangManaNames = listOf(
        "Balqyz", "BhochiL SD", "Khristain", "Rawina",
        "Amoy", "Via Puten", "MomSky", "Ayu"
    )
    val yangManaAvatarImages = listOf(
        "https://raw.githubusercontent.com/andikune-ux/icon/main/balqyz.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/BhochiL%20SD.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/Khristain.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/Rawina.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/Amoy.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/Via%20Puten.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/MomSky.jpg",
        "https://raw.githubusercontent.com/andikune-ux/icon/main/Ayu.jpg"
    )
    val yangManaAvatarIds = listOf("balqyz", "bhochilsd", "lhojte-khristain", "rawina", "manda-amoy", "via-puten", "mom-sky", "ayu")
    val avatarTargets = remember(yangManaNames) {
        buildList {
            yangManaNames.forEachIndexed { i, name -> add("p1:${yangManaAvatarIds[i]}" to "Halaman 1 • $name") }
            yangManaNames.forEachIndexed { i, name -> add("p2:${yangManaAvatarIds[i]}" to "Halaman 2 • JJ $name") }
        }
    }
    val avatarTargetLabels = remember(avatarTargets) { avatarTargets.toMap() }
    val avatarVideoCounts = remember(eligible, effectiveAvatarAssignments, avatarTargets) {
        TikTodFeedPolicy.avatarVideoCounts(
            eligible,
            effectiveAvatarAssignments,
            avatarTargets.map { it.first }
        )
    }
    // Fixed muted highlighter/charcoal palette; colors stay with their avatar in both modes.
    val yangManaNameColors = listOf(
        Color(0xFFD18BA7), // Balqyz - dusty pink
        Color(0xFFC9BD69), // BhochiL SD - muted yellow
        Color(0xFF91B477), // Lhojte Khristain - muted lime
        Color(0xFF83A9C8), // Rawina - dusty blue
        Color(0xFFD49A72), // Manda Amoy - muted orange
        Color(0xFFB49ACB), // Via Puten - dusty purple
        Color(0xFF82BDB0), // Mom Sky - muted mint
        Color(0xFFCB91B9), // Ayu - muted magenta
        Color(0xFFC98B82)  // Jedag Jedug / Kembali - muted coral
    )
    // Warna glow diambil dari sampel warna foto setelah gambar berhasil dimuat.
    var avatarGlowColors by remember {
        mutableStateOf(
            listOf(
                Color(0xFFD18BA7), Color(0xFFC9BD69), Color(0xFF91B477), Color(0xFF83A9C8),
                Color(0xFFD49A72), Color(0xFFB49ACB), Color(0xFF82BDB0), Color(0xFFCB91B9)
            )
        )
    }
    var isJjCategory by remember { mutableStateOf(false) }
    var isCategoryFlipRunning by remember { mutableStateOf(false) }
    val categoryFlipRotation = remember { Animatable(0f) }
    val categoryFlipScope = rememberCoroutineScope()
    var showYangManaSettings by remember { mutableStateOf(false) }
    var showVideoManager by remember { mutableStateOf(false) }
    var showManagerAddVideos by remember { mutableStateOf(false) }
    var confirmManagerRemoval by remember { mutableStateOf(false) }
    var managerTargetAvatarId by remember { mutableStateOf("p2:balqyz") }
    var lastFeedCategory by remember { mutableIntStateOf(2) }
    var lastFeedAvatarId by remember { mutableStateOf(selectedAvatarId) }
    var categorySessions by remember(activeProfile?.id) {
        mutableStateOf<Map<String, TikTodPlaybackSession>>(emptyMap())
    }
    var activeCategorySessionKey by remember(activeProfile?.id) { mutableStateOf<String?>(null) }

    fun categorySessionKey(category: Int, avatarId: String = selectedAvatarId): String =
        if (category == 0) "0:$avatarId" else category.toString()

    fun saveCurrentCategorySession() {
        if (selectedTikTodCategory == 3) return
        val current = feed.getOrNull(index) ?: viewModel.currentlyPlaying.value ?: return
        val key = categorySessionKey(selectedTikTodCategory)
        categorySessions = categorySessions + (key to TikTodPlaybackSession(
            queue = feed,
            mediaId = current.id,
            positionMs = viewModel.player.playerState.value.currentPositionMs.coerceAtLeast(0L)
        ))
    }

    fun switchTikTodCategory(target: Int) {
        if (target == selectedTikTodCategory) return
        saveCurrentCategorySession()
        viewModel.player.pause()
        viewModel.player.cancelDrivePreload()
        selectedTikTodCategory = target.coerceIn(0, 3)
    }
    var previewJumpIndex by remember { mutableIntStateOf(0) }
    DisposableEffect(assignmentPreviewPlayer) {
        onDispose {
            assignmentPreviewPlayer.stop()
            assignmentPreviewPlayer.release()
        }
    }
    LaunchedEffect(showYangManaSettings, showVideoManager) {
        if (!showYangManaSettings && !showVideoManager) {
            activeAssignmentPreviewId = null
            assignmentPreviewPlayer.stop()
        }
    }
    LaunchedEffect(activeAssignmentPreviewId, assignmentPreviewState.durationMs) {
        val previewId = activeAssignmentPreviewId ?: return@LaunchedEffect
        val actualDuration = assignmentPreviewState.durationMs
        if (actualDuration > 0L) viewModel.savePreviewMediaDuration(previewId, actualDuration)
    }
    LaunchedEffect(activeAssignmentPreviewId, showYangManaSettings, showVideoManager) {
        if (activeAssignmentPreviewId == null || (!showYangManaSettings && !showVideoManager)) return@LaunchedEffect
        previewJumpIndex = 0
        val previewFractions = listOf(0.25f, 0.50f, 0.75f)
        while (true) {
            delay(2600L)
            val player = assignmentPreviewPlayer.exoPlayer ?: continue
            val duration = player.duration
            if (duration > 1800L && player.isCurrentMediaItemSeekable) {
                val target = (duration * previewFractions[previewJumpIndex % previewFractions.size])
                    .toLong().coerceIn(0L, (duration - 500L).coerceAtLeast(0L))
                player.pause()
                player.seekTo(target)
                delay(280L)
                player.play()
                previewJumpIndex = (previewJumpIndex + 1) % previewFractions.size
            }
        }
    }
    val tikTodCategories = listOf("Mau Mu", "Jedag Jedug", "Untuk Mu", "Yang Mana")
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
        if (selectedTikTodCategory == 3 && playerState.isPlaying) viewModel.player.pause()
    }

    LaunchedEffect(media?.id) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
        videoAspectRatio = 0f
    }

    // Keep a small progressive preload for exactly one upcoming Drive video.
    // The shared player cache is reused when the item becomes the active video.
    LaunchedEffect(media?.id) {
        // Navigation changes do not trigger another Drive preload. Only a real
        // playback item change advances the one-item preload window.
        val nextItem = feed.getOrNull(index + 1) ?: return@LaunchedEffect
        if (MediaSourceClassifier.isGoogleDriveUrl(nextItem.url)) {
            viewModel.player.preloadDriveMedia(nextItem.url, nextItem.id)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setTikTodModeActive(true)
        onDispose {
            viewModel.player.cancelDrivePreload()
            viewModel.setTikTodModeActive(false)
        }
    }

    fun startAt(
        target: Int,
        source: List<com.example.data.model.MediaItem> = feed,
        startPositionMs: Long = 0L
    ) {
        if (source.isEmpty()) return
        val targetIndex = target.coerceIn(0, source.lastIndex)
        index = targetIndex
        retryUsed = false
        countedView = false
        isSeeking = false
        val item = source[targetIndex]
        viewModel.playMedia(item.copy(progressMs = startPositionMs.coerceAtLeast(0L)))
        viewModel.player.setPlaybackSpeed(speed)
        likes = store.likeCount(item.id)
        views = store.viewCount(item.id)
        comments = store.comments(item.id)
    }
    fun next() {
        if (index + 1 < feed.size) startAt(index + 1)
    }
    // The For You order is a session-level queue. Switching tabs must never
    // rebuild it or restart its current item; only newly discovered videos are appended.
    LaunchedEffect(eligible, activeProfile?.id) {
        val profileId = activeProfile?.id ?: return@LaunchedEffect
        if (eligible.isEmpty()) return@LaunchedEffect

        val pendingNewIds = store.syncEligibleVideos(profileId, eligible.map { it.id })
        if (!sessionInitialized) {
            forYouFeed = TikTodFeedPolicy.sessionStartOrder(
                items = eligible,
                pendingNewIds = pendingNewIds,
                previousVideoId = store.lastVideoId(profileId)
            )
            sessionInitialized = true
            if (selectedTikTodCategory == 2 && feed.isEmpty() && forYouFeed.isNotEmpty()) {
                feed = forYouFeed
                startAt(0, forYouFeed)
            }
        } else {
            val knownIds = forYouFeed.mapTo(hashSetOf()) { it.id }
            val queuedNewItems = eligible.filter { it.id in pendingNewIds && it.id !in knownIds }
            if (queuedNewItems.isNotEmpty()) {
                forYouFeed = forYouFeed + queuedNewItems
                if (selectedTikTodCategory == 2) {
                    val currentItem = feed.getOrNull(index)
                    feed = if (currentItem != null) {
                        listOf(currentItem) + forYouFeed.filterNot { it.id == currentItem.id }
                    } else forYouFeed
                    index = 0
                }
            }
        }
    }

    // Each playable category owns an independent queue, current item and playhead.
    // Category switches pause the shared player and restore only the target session.
    LaunchedEffect(selectedTikTodCategory, selectedAvatarId, categorySource, forYouFeed) {
        if (selectedTikTodCategory == 3) {
            viewModel.player.pause()
            activeCategorySessionKey = null
            lastFeedCategory = selectedTikTodCategory
            lastFeedAvatarId = selectedAvatarId
            return@LaunchedEffect
        }

        val key = categorySessionKey(selectedTikTodCategory)
        val targetQueue = if (selectedTikTodCategory == 2) forYouFeed else categorySource
        if (targetQueue.isEmpty()) {
            feed = emptyList()
            index = 0
            return@LaunchedEffect
        }

        val targetIds = targetQueue.mapTo(hashSetOf()) { it.id }
        if (activeCategorySessionKey == key) {
            val currentId = feed.getOrNull(index)?.id
            val updatedQueue = feed.filter { it.id in targetIds } +
                targetQueue.filterNot { item -> feed.any { it.id == item.id } }
            feed = updatedQueue
            val retainedIndex = currentId?.let { id -> updatedQueue.indexOfFirst { it.id == id } } ?: -1
            if (retainedIndex >= 0) index = retainedIndex
            else if (updatedQueue.isNotEmpty()) startAt(0, updatedQueue)
            return@LaunchedEffect
        }

        val saved = categorySessions[key]
        val restoredQueue = if (saved != null) {
            saved.queue.filter { it.id in targetIds } +
                targetQueue.filterNot { item -> saved.queue.any { it.id == item.id } }
        } else targetQueue
        feed = restoredQueue
        val restoredIndex = saved?.mediaId?.let { id ->
            restoredQueue.indexOfFirst { it.id == id }
        } ?: -1
        index = restoredIndex.takeIf { it >= 0 } ?: 0
        activeCategorySessionKey = key
        lastFeedCategory = selectedTikTodCategory
        lastFeedAvatarId = selectedAvatarId

        if (restoredQueue.isNotEmpty()) {
            val position = if (restoredIndex >= 0) saved?.positionMs ?: 0L else 0L
            startAt(index, restoredQueue, position)
        }
    }

    // Feed/category effects are the single authority that starts a TikTod item.
    // Do not restart the current index merely because ViewModel.currentlyPlaying
    // briefly lags behind Compose feed state; that race can reset playback or
    // make a stale item appear to be skipped.
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
                            } else if (horizontalDragStarted) {
                                // Track the full swipe distance after the gesture is recognized.
                                latestHorizontalOffset = horizontal
                                tracked.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!multiTouch && horizontalDragStarted) {
                        val horizontalThreshold = (size.width * 0.18f).coerceAtLeast(viewConfiguration.touchSlop * 2f)
                        if (kotlin.math.abs(latestHorizontalOffset) >= horizontalThreshold) {
                            // Swipe changes one category at a time; stop at the first and last category.
                            switchTikTodCategory((selectedTikTodCategory + if (latestHorizontalOffset < 0f) 1 else -1)
                                .coerceIn(0, tikTodCategories.lastIndex))
                        }
                    } else if (!multiTouch && verticalDragStarted) {
                        val distanceThreshold = (size.height * 0.18f).coerceAtLeast(viewConfiguration.touchSlop * 2f)
                        val offset = latestDragOffset
                        val refreshAtTop = offset > distanceThreshold && index == 0 && feed.isNotEmpty()
                        val target = when {
                            offset < -distanceThreshold && index < feed.lastIndex -> index + 1
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
                                    // Manual pull-to-refresh is the only in-feed action
                                    // that reshuffles the active category's queue.
                                    val refreshedFeed = TikTodFeedPolicy.newCycle(categorySource, media?.id)
                                    if (refreshedFeed.isNotEmpty()) {
                                        feed = refreshedFeed
                                        if (selectedTikTodCategory == 2) forYouFeed = refreshedFeed
                                        index = 0
                                        activeCategorySessionKey = categorySessionKey(selectedTikTodCategory)
                                        categorySessions = categorySessions + (
                                            categorySessionKey(selectedTikTodCategory) to
                                                TikTodPlaybackSession(refreshedFeed, refreshedFeed.first().id, 0L)
                                        )
                                        startAt(0, refreshedFeed)
                                    }
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
                    .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tikTodCategories.forEachIndexed { categoryIndex, category ->
                    val selected = selectedTikTodCategory == categoryIndex
                    Column(
                        modifier = Modifier.clickable { switchTikTodCategory(categoryIndex) },
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
        if (selectedTikTodCategory == 3) {
            // Yang Mana is the avatar selector; its grid is rendered above the player layer.
        } else if (media == null) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    when (selectedTikTodCategory) {
                        0 -> "Mau Mu"
                        1 -> "Jedag Jedug"
                        else -> "Untuk Mu"
                    },
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when (selectedTikTodCategory) {
                        0 -> "Belum ada video yang ditetapkan untuk ${avatarTargetLabels[selectedAvatarId] ?: "Avatar pilihan"}."
                        1 -> "Belum ada video yang ditetapkan ke Avatar halaman 2 (JJ)."
                        else -> "Belum ada video Google Drive yang memenuhi syarat."
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (selectedTikTodCategory == 0) TextButton(onClick = { selectedTikTodCategory = 3 }) { Text("Pilih Avatar") }
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
                val assignedAvatarId = TikTodFeedPolicy.assignmentOwner(media, effectiveAvatarAssignments)?.takeIf { it.isNotBlank() }
                val assignedAvatarIndex = assignedAvatarId?.substringAfter(':')?.let { id ->
                    yangManaAvatarIds.indexOf(id).takeIf { it >= 0 }
                }
                val isDefaultKuntiMeong = assignedAvatarId == null || assignedAvatarIndex == null
                val displayedAvatarName = if (isDefaultKuntiMeong) {
                    "Kunti Meong Collection"
                } else {
                    val baseName = yangManaNames[assignedAvatarIndex!!]
                    if (assignedAvatarId!!.startsWith("p2:")) "JJ $baseName" else baseName
                }
                val displayedAvatarImage = if (isDefaultKuntiMeong) {
                    PRIMARY_PROFILE_AVATAR_URL
                } else {
                    yangManaAvatarImages[assignedAvatarIndex!!]
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable {
                            managerTargetAvatarId = assignedAvatarId ?: selectedAvatarId
                            showManagerAddVideos = false
                            showVideoManager = true
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = .20f),
                        modifier = Modifier.size(if (isDefaultKuntiMeong) 34.dp else 30.dp)
                    ) {
                        AsyncImage(
                            model = displayedAvatarImage,
                            contentDescription = "Avatar $displayedAvatarName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Text(
                        displayedAvatarName,
                        color = Color.White,
                        fontSize = if (isDefaultKuntiMeong) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (isDefaultKuntiMeong) primaryDisplayFont else null,
                        maxLines = 1
                    )
                    if (isDefaultKuntiMeong) {
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
            // Draw category navigation last inside the video layer so PlayerView cannot cover it.
            if (!isCleanMode) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tikTodCategories.forEachIndexed { categoryIndex, category ->
                        val selected = selectedTikTodCategory == categoryIndex
                        Column(
                            modifier = Modifier.clickable { switchTikTodCategory(categoryIndex) },
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
        }
        }
    }


    if (selectedTikTodCategory == 3) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(selectedTikTodCategory) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val pointerId = down.id
                        var horizontal = 0f
                        var recognized = false
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1) multiTouch = true
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (!multiTouch && change != null && change.pressed) {
                                val delta = change.position - down.position
                                if (!recognized &&
                                    kotlin.math.abs(delta.x) > viewConfiguration.touchSlop &&
                                    kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) * 1.2f
                                ) recognized = true
                                if (recognized) {
                                    horizontal = delta.x
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        val threshold = maxOf(48.dp.toPx(), size.width * 0.08f)
                        if (!multiTouch && recognized && kotlin.math.abs(horizontal) >= threshold) {
                            selectedTikTodCategory = (selectedTikTodCategory + if (horizontal < 0f) 1 else -1)
                                .coerceIn(0, tikTodCategories.lastIndex)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tikTodCategories.forEachIndexed { categoryIndex, category ->
                    val selected = selectedTikTodCategory == categoryIndex
                    Column(
                        modifier = Modifier.clickable { switchTikTodCategory(categoryIndex) },
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
                    val isJjCard = isJjCategory || isCategoryFlipRunning
                    val cardName = when {
                        profileIndex == 8 && isJjCategory -> "Kembali"
                        profileIndex == 8 -> "Jedag Jedug"
                        isJjCategory -> "JJ ${yangManaNames[profileIndex]}"
                        else -> yangManaNames[profileIndex]
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        AvatarGlowFrame(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            tint = avatarGlowColors.getOrElse(profileIndex) { Color.Transparent },
                            enabled = profileIndex < 8
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = categoryFlipRotation.value
                                        cameraDistance = 24f * density
                                    }
                                    .clickable(enabled = !isCategoryFlipRunning) {
                                        if (profileIndex == 8) {
                                            isCategoryFlipRunning = true
                                            categoryFlipScope.launch {
                                                categoryFlipRotation.snapTo(0f)
                                                categoryFlipRotation.animateTo(180f, spring(dampingRatio = 0.78f, stiffness = 380f))
                                                isJjCategory = !isJjCategory
                                                categoryFlipRotation.animateTo(360f, spring(dampingRatio = 0.78f, stiffness = 380f))
                                                categoryFlipRotation.snapTo(0f)
                                                isCategoryFlipRunning = false
                                            }
                                        } else {
                                            val avatarId = "${if (isJjCategory) "p2" else "p1"}:${yangManaAvatarIds[profileIndex]}"
                                            selectedAvatarId = avatarId
                                            val profileId = activeProfile?.id
                                            if (profileId != null) {
                                                store.setSelectedAvatar(profileId, avatarId)
                                                appliedAvatarLabel = (if (isJjCategory) "JJ " else "") + yangManaNames[profileIndex]
                                                showAvatarAppliedDialog = true
                                            } else {
                                                Toast.makeText(context, "Profil aktif tidak ditemukan; pilihan belum tersimpan.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(23.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                border = BorderStroke(
                                    if (isJjCard || (profileIndex < 8 && selectedAvatarId == "${if (isJjCategory) "p2" else "p1"}:${yangManaAvatarIds[profileIndex]}")) 1.8.dp else 1.dp,
                                    when {
                                        profileIndex < 8 && selectedAvatarId == "${if (isJjCategory) "p2" else "p1"}:${yangManaAvatarIds[profileIndex]}" -> Color(0xFF8DE1C1)
                                        isJjCard -> Color(0xFFFFD54F)
                                        else -> Color.White.copy(alpha = 0.38f)
                                    }
                                ),
                                shadowElevation = if (isJjCard) 14.dp else 8.dp
                            ) {
                                Box {
                                    AsyncImage(
                                        model = yangManaAvatarImages.getOrNull(profileIndex) ?: PRIMARY_PROFILE_AVATAR_URL,
                                        contentDescription = cardName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        onSuccess = { result ->
                                            val tint = extractAvatarTint(result.result.drawable)
                                            avatarGlowColors = avatarGlowColors.toMutableList().also { colors ->
                                                if (profileIndex < colors.size) colors[profileIndex] = tint
                                            }
                                        }
                                    )
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        Text(
                            cardName,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            color = yangManaNameColors.getOrElse(profileIndex) { Color(0xFFC98B82) },
                            fontFamily = yangManaNameFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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

    if (showYangManaSettings) {
        ModalBottomSheet(
            onDismissRequest = {
                showYangManaSettings = false
                selectedUnassignedVideoIds = emptySet()
                selectedAssignedVideoIds = emptySet()
                showAssignmentTargetMenu = false
            },
            containerColor = Color(0xFF17181D),
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.62f)
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pengaturan pembagian video", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Pilih video Drive, lalu tentukan satu Avatar pemilik. Satu video hanya bisa dimiliki satu Avatar.",
                    color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, lineHeight = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Avatar tujuan", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(onClick = { showAssignmentTargetMenu = true }) {
                            Text(avatarTargetLabels[assignmentTargetAvatarId] ?: "Belum ditentukan", maxLines = 1)
                        }
                        DropdownMenu(expanded = showAssignmentTargetMenu, onDismissRequest = { showAssignmentTargetMenu = false }) {
                            DropdownMenuItem(text = { Text("Belum ditentukan") }, onClick = {
                                assignmentTargetAvatarId = ""
                                selectedUnassignedVideoIds = emptySet()
                                selectedAssignedVideoIds = emptySet()
                                showAssignmentTargetMenu = false
                            })
                            avatarTargets.forEach { (id, label) ->
                                val videoCount = avatarVideoCounts[id] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
                                            Text(
                                                "$videoCount video",
                                                color = if (videoCount > 0) Color(0xFF9DDCCB) else Color.White.copy(alpha = 0.48f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    onClick = {
                                    assignmentTargetAvatarId = id
                                    selectedUnassignedVideoIds = emptySet()
                                    selectedAssignedVideoIds = emptySet()
                                    showAssignmentTargetMenu = false
                                    val previewOwner = activeAssignmentPreviewId?.let { id -> eligible.firstOrNull { it.id == id }?.let { TikTodFeedPolicy.assignmentOwner(it, effectiveAvatarAssignments) } }
                                    if (activeAssignmentPreviewId != null && previewOwner != null && previewOwner != id) {
                                        activeAssignmentPreviewId = null
                                        assignmentPreviewPlayer.stop()
                                    }
                                })
                            }
                        }
                    }
                }
                val unassignedVideos = eligible.filter { TikTodFeedPolicy.assignmentOwner(it, effectiveAvatarAssignments).isNullOrBlank() }
                val targetAssignedVideos = eligible.filter { TikTodFeedPolicy.assignmentOwner(it, effectiveAvatarAssignments) == assignmentTargetAvatarId && assignmentTargetAvatarId.isNotBlank() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedUnassignedVideoIds.size + selectedAssignedVideoIds.size} video dipilih • ${eligible.size} video tersedia",
                        modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                    TextButton(onClick = {
                        selectedUnassignedVideoIds = if (selectedUnassignedVideoIds.size == unassignedVideos.size) emptySet()
                        else unassignedVideos.map { it.id }.toSet()
                        selectedAssignedVideoIds = emptySet()
                    }, enabled = unassignedVideos.isNotEmpty()) {
                        Text(if (unassignedVideos.isNotEmpty() && selectedUnassignedVideoIds.size == unassignedVideos.size) "Batal pilih" else "Pilih semua")
                    }
                }
                if (eligible.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Belum ada video Drive yang memenuhi syarat untuk dibagi.", color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        @Composable
                        fun AssignmentVideoColumn(
                            title: String,
                            items: List<com.example.data.model.MediaItem>,
                            selectedColumn: Boolean
                        ) {
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.055f))
                                    .padding(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    if (selectedColumn) (avatarTargetLabels[assignmentTargetAvatarId] ?: "Tujuan belum ditentukan")
                                    else "Belum masuk pilihan",
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${items.size} video", color = Color(0xFF9DDCCB), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                LazyColumn(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                                        val checked = if (selectedColumn) item.id in selectedAssignedVideoIds else item.id in selectedUnassignedVideoIds
                                        val isPreview = activeAssignmentPreviewId == item.id
                                        val previewDurationMs = if (isPreview && assignmentPreviewState.durationMs > 0L) {
                                            assignmentPreviewState.durationMs
                                        } else item.durationMs
                                        val ownerLabel = avatarTargetLabels[TikTodFeedPolicy.assignmentOwner(item, effectiveAvatarAssignments)] ?: "Belum ditentukan"
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = if (checked) 0.13f else 0.045f))
                                                .clickable {
                                                    if (selectedColumn) {
                                                        selectedAssignedVideoIds = if (checked) selectedAssignedVideoIds - item.id else selectedAssignedVideoIds + item.id
                                                    } else {
                                                        selectedUnassignedVideoIds = if (checked) selectedUnassignedVideoIds - item.id else selectedUnassignedVideoIds + item.id
                                                    }
                                                    if (activeAssignmentPreviewId == item.id) {
                                                        activeAssignmentPreviewId = null
                                                        assignmentPreviewPlayer.stop()
                                                    } else {
                                                        activeAssignmentPreviewId = item.id
                                                        assignmentPreviewPlayer.exoPlayer?.volume = 0f
                                                        assignmentPreviewPlayer.play(
                                                            item.url,
                                                            item.title.ifBlank { "Pratinjau video" },
                                                            0L,
                                                            mediaCacheKey = "${item.profileId}:${item.id}"
                                                        )
                                                    }
                                                }
                                                .padding(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Color.Black)
                                            ) {
                                                if (isPreview) {
                                                    AndroidView(
                                                        factory = { viewContext ->
                                                            PlayerView(viewContext).apply {
                                                                player = assignmentPreviewPlayer.exoPlayer
                                                                useController = false
                                                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                                            }
                                                        },
                                                        update = { it.player = assignmentPreviewPlayer.exoPlayer },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    if (assignmentPreviewState.isBuffering) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.align(Alignment.Center).size(22.dp),
                                                            color = Color.White,
                                                            strokeWidth = 2.dp
                                                        )
                                                    }
                                                } else if (item.thumbnailUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = item.thumbnailUrl,
                                                        contentDescription = "Thumbnail ${item.title}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = "Pratinjau video",
                                                        tint = Color.White,
                                                        modifier = Modifier.align(Alignment.Center).size(30.dp)
                                                    )
                                                }
                                                if (isPreview) {
                                                    Surface(
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                                        shape = RoundedCornerShape(5.dp),
                                                        color = Color.Black.copy(alpha = 0.68f)
                                                    ) {
                                                        Text("PREVIEW", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                            color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = { value ->
                                                        if (selectedColumn) {
                                                            selectedAssignedVideoIds = if (value) selectedAssignedVideoIds + item.id else selectedAssignedVideoIds - item.id
                                                        } else {
                                                            selectedUnassignedVideoIds = if (value) selectedUnassignedVideoIds + item.id else selectedUnassignedVideoIds - item.id
                                                        }
                                                    },
                                                    modifier = Modifier.size(25.dp),
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color(0xFF78CDB3),
                                                        uncheckedColor = Color.White.copy(alpha = 0.55f)
                                                    )
                                                )
                                                Text(
                                                    item.title.ifBlank { "Video tanpa judul" },
                                                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Clip,
                                                    lineHeight = 13.sp
                                                )
                                            }
                                            Text(
                                                "Pemilik: $ownerLabel",
                                                color = Color.White.copy(alpha = 0.52f),
                                                fontSize = 8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AssignmentVideoColumn(
                            title = "Belum dipilih",
                            items = unassignedVideos,
                            selectedColumn = false
                        )
                        AssignmentVideoColumn(
                            title = "Sudah dipilih",
                            items = targetAssignedVideos,
                            selectedColumn = true
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selectedUnassignedVideoIds.isNotEmpty() && assignmentTargetAvatarId.isNotBlank() && activeProfile != null,
                        onClick = {
                            activeProfile?.id?.let { profileId ->
                                store.assignVideos(
                                    profileId,
                                    selectedUnassignedVideoIds.mapNotNull { id -> eligible.firstOrNull { it.id == id }?.let(TikTodFeedPolicy::assignmentKey) },
                                    assignmentTargetAvatarId
                                )
                                avatarAssignments = store.avatarAssignments(profileId)
                                selectedUnassignedVideoIds = emptySet()
                                selectedAssignedVideoIds = emptySet()
                            }
                        }
                    ) { Text("Simpan ke Avatar", maxLines = 1) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = selectedAssignedVideoIds.isNotEmpty() && activeProfile != null,
                        onClick = {
                            activeProfile?.id?.let { profileId ->
                                store.assignVideos(
                                    profileId,
                                    selectedAssignedVideoIds.flatMap { id ->
                                        eligible.firstOrNull { it.id == id }?.let { listOf(TikTodFeedPolicy.assignmentKey(it), it.id) }.orEmpty()
                                    },
                                    ""
                                )
                                avatarAssignments = store.avatarAssignments(profileId)
                                selectedAssignedVideoIds = emptySet()
                                selectedUnassignedVideoIds = emptySet()
                            }
                        }
                    ) { Text("Keluarkan dari Avatar", maxLines = 1) }
                }
            }
        }
    }


    if (showVideoManager) {
        // Dedicated Avatar profile manager: independent UI from Yang Mana settings and Jedag Jedug.
        val managerVideos = TikTodFeedPolicy.forAvatar(
            eligible, effectiveAvatarAssignments, managerTargetAvatarId
        )
        val unassignedManagerVideos = eligible.filter {
            TikTodFeedPolicy.assignmentOwner(it, effectiveAvatarAssignments).isNullOrBlank()
        }
        val targetSlug = managerTargetAvatarId.substringAfter(':')
        val targetIndex = yangManaAvatarIds.indexOf(targetSlug)
        val targetName = if (targetIndex >= 0) {
            yangManaNames[targetIndex]
        } else {
            avatarTargetLabels[managerTargetAvatarId] ?: "Avatar"
        }
        val targetImage = if (targetIndex >= 0) yangManaAvatarImages[targetIndex] else PRIMARY_PROFILE_AVATAR_URL

        Dialog(
            onDismissRequest = {
                showVideoManager = false
                showManagerAddVideos = false
                confirmManagerRemoval = false
                selectedAssignedVideoIds = emptySet()
                selectedUnassignedVideoIds = emptySet()
                activeAssignmentPreviewId = null
                assignmentPreviewPlayer.stop()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF101217)
            ) {
                Column(
                    Modifier.fillMaxSize().navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Profil Avatar", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = {
                            showVideoManager = false
                            showManagerAddVideos = false
                            selectedAssignedVideoIds = emptySet()
                            selectedUnassignedVideoIds = emptySet()
                            activeAssignmentPreviewId = null
                            assignmentPreviewPlayer.stop()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup profil Avatar", tint = Color.White)
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(104.dp),
                            shape = CircleShape,
                            color = Color(0xFF292D36),
                            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            AsyncImage(
                                model = targetImage,
                                contentDescription = "Foto profil $targetName",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(targetName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${managerVideos.size} video dalam profil ini",
                            color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Video Avatar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (selectedAssignedVideoIds.isNotEmpty()) {
                            Text("${selectedAssignedVideoIds.size} dipilih", color = Color(0xFF9DDCCB),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (managerVideos.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Belum ada video di profil $targetName. Tekan Tambah Video untuk memilih dari katalog.",
                                color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(managerVideos, key = { it.id }) { item ->
                                val checked = item.id in selectedAssignedVideoIds
                                val previewDurationMs = if (activeAssignmentPreviewId == item.id && assignmentPreviewState.durationMs > 0L) {
                                    assignmentPreviewState.durationMs
                                } else item.durationMs
                                val isCurrent = media?.id == item.id
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222630)),
                                    border = BorderStroke(
                                        if (checked) 2.dp else 1.dp,
                                        if (checked) Color(0xFF9DDCCB) else Color.White.copy(alpha = 0.09f)
                                    )
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            Modifier.fillMaxWidth().aspectRatio(4f / 5f)
                                                .clip(RoundedCornerShape(10.dp)).background(Color.Black)
                                        ) {
                                            if (item.thumbnailUrl.isNotBlank()) {
                                                AsyncImage(model = item.thumbnailUrl,
                                                    contentDescription = "Video ${item.title}",
                                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Video",
                                                    tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
                                            }
                                            Surface(
                                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(25.dp),
                                                shape = CircleShape,
                                                color = if (checked) Color(0xFF8ED8BF) else Color.Black.copy(alpha = 0.58f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (checked) {
                                                        Icon(Icons.Default.Verified, contentDescription = "Dipilih",
                                                            tint = Color(0xFF10251F), modifier = Modifier.size(17.dp))
                                                    } else {
                                                        Text("○", color = Color.White, fontSize = 19.sp,
                                                            fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            if (isCurrent) {
                                                Surface(Modifier.align(Alignment.BottomStart).padding(6.dp),
                                                    shape = RoundedCornerShape(5.dp), color = Color(0xDD1A5A4A)) {
                                                    Text("SEDANG DIPUTAR", Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                                        color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp),
                                                shape = RoundedCornerShape(5.dp), color = Color.Black.copy(alpha = 0.68f)) {
                                                Text(formatTikTodTime(previewDurationMs),
                                                    Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                                    color = Color.White, fontSize = 8.sp)
                                            }
                                        }
                                        Text(item.title.ifBlank { "Video tanpa judul" }, color = Color.White,
                                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedAssignedVideoIds = if (checked) {
                                                        selectedAssignedVideoIds - item.id
                                                    } else {
                                                        selectedAssignedVideoIds + item.id
                                                    }
                                                }
                                                .padding(horizontal = 2.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { selected ->
                                                    selectedAssignedVideoIds = if (selected) {
                                                        selectedAssignedVideoIds + item.id
                                                    } else {
                                                        selectedAssignedVideoIds - item.id
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp),
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF8ED8BF),
                                                    uncheckedColor = Color.White.copy(alpha = 0.62f),
                                                    checkmarkColor = Color(0xFF10251F)
                                                )
                                            )
                                            Text(
                                                "Tandai untuk keluarkan",
                                                color = if (checked) Color(0xFF9DDCCB) else Color.White.copy(alpha = 0.72f),
                                                fontSize = 9.sp,
                                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                activeAssignmentPreviewId = item.id
                                                assignmentPreviewPlayer.exoPlayer?.volume = 0f
                                                assignmentPreviewPlayer.play(item.url,
                                                    item.title.ifBlank { "Pratinjau video" }, 0L,
                                                    mediaCacheKey = "${item.profileId}:${item.id}")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 2.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                                modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Preview", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = selectedAssignedVideoIds.isNotEmpty(),
                            onClick = { confirmManagerRemoval = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA6A6)),
                            border = BorderStroke(1.dp, Color(0xFFB96C76))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Keluarkan", maxLines = 1)
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showManagerAddVideos = true
                                selectedUnassignedVideoIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF287B68))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Tambah Video", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (showManagerAddVideos) {
            Dialog(
                onDismissRequest = {
                    showManagerAddVideos = false
                    selectedUnassignedVideoIds = emptySet()
                    activeAssignmentPreviewId = null
                    assignmentPreviewPlayer.stop()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    Modifier.fillMaxSize().padding(10.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF171B22),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Column(
                        Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Katalog Video", color = Color.White, fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold)
                                Text("Pilih video yang belum dimiliki Avatar mana pun",
                                    color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp)
                            }
                            IconButton(onClick = {
                                showManagerAddVideos = false
                                selectedUnassignedVideoIds = emptySet()
                                activeAssignmentPreviewId = null
                                assignmentPreviewPlayer.stop()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup katalog", tint = Color.White)
                            }
                        }
                        Text("${unassignedManagerVideos.size} video tersedia",
                            color = Color(0xFF9DDCCB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (unassignedManagerVideos.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("Tidak ada video yang belum memiliki Avatar.",
                                    color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(unassignedManagerVideos, key = { it.id }) { item ->
                                    val checked = item.id in selectedUnassignedVideoIds
                                    val previewDurationMs = if (activeAssignmentPreviewId == item.id && assignmentPreviewState.durationMs > 0L) {
                                        assignmentPreviewState.durationMs
                                    } else item.durationMs
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedUnassignedVideoIds = if (checked) {
                                                selectedUnassignedVideoIds - item.id
                                            } else {
                                                selectedUnassignedVideoIds + item.id
                                            }
                                        },
                                        shape = RoundedCornerShape(13.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252B35)),
                                        border = BorderStroke(if (checked) 2.dp else 1.dp,
                                            if (checked) Color(0xFF9DDCCB) else Color.White.copy(alpha = 0.10f))
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(7.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f)
                                                .clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
                                                if (item.thumbnailUrl.isNotBlank()) {
                                                    AsyncImage(model = item.thumbnailUrl,
                                                        contentDescription = "Video ${item.title}",
                                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Video",
                                                        tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                                                }
                                                Surface(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                                                    shape = CircleShape,
                                                    color = if (checked) Color(0xFF8ED8BF) else Color.Black.copy(alpha = 0.62f)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (checked) {
                                                            Icon(Icons.Default.Verified, contentDescription = "Dipilih",
                                                                tint = Color(0xFF10251F), modifier = Modifier.size(16.dp))
                                                        } else {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Pilih video",
                                                                tint = Color.White, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                                Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp),
                                                    shape = RoundedCornerShape(5.dp), color = Color.Black.copy(alpha = 0.68f)) {
                                                    Text(formatTikTodTime(previewDurationMs),
                                                        Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                                        color = Color.White, fontSize = 8.sp)
                                                }
                                            }
                                            Text(item.title.ifBlank { "Video tanpa judul" }, color = Color.White,
                                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                                            OutlinedButton(
                                                onClick = {
                                                    activeAssignmentPreviewId = item.id
                                                    assignmentPreviewPlayer.exoPlayer?.volume = 0f
                                                    assignmentPreviewPlayer.play(item.url,
                                                        item.title.ifBlank { "Pratinjau video" }, 0L,
                                                        mediaCacheKey = "${item.profileId}:${item.id}")
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(vertical = 1.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null,
                                                    modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("Preview", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${selectedUnassignedVideoIds.size} dipilih",
                                Modifier.weight(1f), color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)
                            OutlinedButton(onClick = {
                                showManagerAddVideos = false
                                selectedUnassignedVideoIds = emptySet()
                            }) { Text("Batal") }
                            Button(
                                enabled = selectedUnassignedVideoIds.isNotEmpty() && managerTargetAvatarId.isNotBlank(),
                                onClick = {
                                    activeProfile?.id?.let { profileId ->
                                        val keys = selectedUnassignedVideoIds.mapNotNull { id ->
                                            unassignedManagerVideos.firstOrNull { it.id == id }
                                                ?.let(TikTodFeedPolicy::assignmentKey)
                                        }
                                        store.assignVideos(profileId, keys, managerTargetAvatarId)
                                        avatarAssignments = store.avatarAssignments(profileId)
                                    }
                                    selectedUnassignedVideoIds = emptySet()
                                    showManagerAddVideos = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF287B68))
                            ) { Text("Tambahkan") }
                        }
                    }
                }
            }
        }

        if (confirmManagerRemoval) {
            AlertDialog(
                onDismissRequest = { confirmManagerRemoval = false },
                containerColor = Color(0xFF222630),
                title = { Text("Keluarkan video?", color = Color.White) },
                text = {
                    Text("${selectedAssignedVideoIds.size} video akan dilepas dari profil $targetName. File video tidak dihapus.",
                        color = Color.White.copy(alpha = 0.78f))
                },
                confirmButton = {
                    TextButton(onClick = {
                        activeProfile?.id?.let { profileId ->
                            val keys = selectedAssignedVideoIds.flatMap { id ->
                                managerVideos.firstOrNull { it.id == id }?.let {
                                    listOf(TikTodFeedPolicy.assignmentKey(it), it.id)
                                }.orEmpty()
                            }
                            store.assignVideos(profileId, keys, "")
                            avatarAssignments = store.avatarAssignments(profileId)
                        }
                        selectedAssignedVideoIds = emptySet()
                        confirmManagerRemoval = false
                    }) { Text("Keluarkan", color = Color(0xFFFFA6A6)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmManagerRemoval = false }) {
                        Text("Batal", color = Color.White.copy(alpha = 0.72f))
                    }
                }
            )
        }
    }

    if (activeAssignmentPreviewId != null && showVideoManager) {
        val previewItem = eligible.firstOrNull { it.id == activeAssignmentPreviewId }
        if (previewItem != null) {
            Dialog(
                onDismissRequest = {
                    activeAssignmentPreviewId = null
                    assignmentPreviewPlayer.stop()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF17181D),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    shadowElevation = 20.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            previewItem.title.ifBlank { "Pratinjau video" },
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)).background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        player = assignmentPreviewPlayer.exoPlayer
                                        useController = false
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    }
                                },
                                update = { it.player = assignmentPreviewPlayer.exoPlayer },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (assignmentPreviewState.isBuffering) {
                                CircularProgressIndicator(
                                    Modifier.align(Alignment.Center).size(28.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                            }
                        }
                        Text(
                            "Preview otomatis melompat ke sekitar 25%, 50%, dan 75% durasi video.",
                            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp
                        )
                        Button(
                            onClick = {
                                activeAssignmentPreviewId = null
                                assignmentPreviewPlayer.stop()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Tutup preview") }
                    }
                }
            }
        }
    }

    if (showAvatarAppliedDialog) {
        Dialog(
            onDismissRequest = { showAvatarAppliedDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val dialogScale = remember { Animatable(0.88f) }
            val dialogAlpha = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                launch { dialogScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 360f)) }
                launch { dialogAlpha.animateTo(1f, tween(240)) }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .graphicsLayer {
                        scaleX = dialogScale.value
                        scaleY = dialogScale.value
                        alpha = dialogAlpha.value
                    },
                shape = RoundedCornerShape(28.dp),
                color = Color(0xF01C222B),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.62f), Color(0xFF8ADBC8).copy(alpha = 0.42f), Color.White.copy(alpha = 0.12f))
                    )
                ),
                shadowElevation = 28.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF9CE7D0), Color(0xFF4F9D91)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF102A26), modifier = Modifier.size(29.dp))
                    }
                    Text("Avatar berhasil dipilih", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(appliedAvatarLabel, color = Color(0xFF9CE7D0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildAnnotatedString {
                            append("Kategori udah diterapin, mau langsung menuju ke halaman ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Mau Mu") }
                            append("?")
                        },
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showAvatarAppliedDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Nanti", fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = {
                                showAvatarAppliedDialog = false
                                selectedTikTodCategory = 0
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9CE7D0),
                                contentColor = Color(0xFF142722)
                            )
                        ) { Text("Mau", fontWeight = FontWeight.Bold) }
                    }
                }
            }
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


@Composable
private fun AvatarGlowFrame(
    modifier: Modifier = Modifier,
    tint: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        // Same linear 360-degree path animation used by HOME.iO NeonFocusBorder.
        while (true) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(2500, easing = LinearEasing))
        }
    }

    val density = LocalDensity.current
    val tintInt = tint.copy(alpha = 1f).toArgb()
    val gold = Color(0xFFFFD36A)
    val goldBlend = Color(
        red = tint.red * 0.52f + gold.red * 0.48f,
        green = tint.green * 0.52f + gold.green * 0.48f,
        blue = tint.blue * 0.52f + gold.blue * 0.48f,
        alpha = 1f
    ).toArgb()
    val haloRadius = with(density) { 15.dp.toPx() }
    val trailRadius = with(density) { 8.dp.toPx() }
    val corner = with(density) { 23.dp.toPx() }

    Box(modifier.graphicsLayer { clip = false }) {
        if (enabled) {
            Canvas(Modifier.matchParentSize()) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val outset = haloRadius * 0.48f
                    val glowRect = RectF(-outset, -outset, size.width + outset, size.height + outset)
                    val glowCorner = corner + outset

                    // Broad, soft photo-tinted light outside the card.
                    val broadHalo = AndroidPaint().apply {
                        isAntiAlias = true
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = with(density) { 8.dp.toPx() }
                        color = tintInt
                        alpha = 118
                        maskFilter = BlurMaskFilter(haloRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                    native.drawRoundRect(glowRect, glowCorner, glowCorner, broadHalo)

                    val nearHalo = AndroidPaint().apply {
                        isAntiAlias = true
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = with(density) { 3.dp.toPx() }
                        color = tintInt
                        alpha = 155
                        maskFilter = BlurMaskFilter(trailRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                    native.drawRoundRect(glowRect, glowCorner, glowCorner, nearHalo)

                    // Gold + image-tint trail follows the same measured rounded path as HOME.iO.
                    val path = Path().apply {
                        addRoundRect(glowRect, glowCorner, glowCorner, Path.Direction.CW)
                    }
                    val pathMeasure = PathMeasure(path, false)
                    val pathLength = pathMeasure.length
                    val dotCount = 30
                    val tailLength = 0.34f
                    val headPosition = rotation.value / 360f
                    val trailPaint = AndroidPaint().apply { isAntiAlias = true }
                    for (i in 0 until dotCount) {
                        val t = headPosition - (i.toFloat() / dotCount) * tailLength
                        val wrapped = ((t % 1f) + 1f) % 1f
                        val point = FloatArray(2)
                        pathMeasure.getPosTan(wrapped * pathLength, point, null)
                        val linear = 1f - i.toFloat() / dotCount
                        val alpha = (linear * linear * 0.88f + linear * 0.12f).coerceIn(0f, 1f)
                        val radius = trailRadius * (0.42f + 0.58f * linear)
                        trailPaint.shader = RadialGradient(
                            point[0], point[1], radius,
                            intArrayOf(
                                withAlpha(goldBlend, alpha),
                                withAlpha(tintInt, alpha * 0.72f),
                                withAlpha(goldBlend, alpha * 0.28f),
                                withAlpha(goldBlend, 0f)
                            ),
                            floatArrayOf(0f, 0.32f, 0.72f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        trailPaint.maskFilter = BlurMaskFilter(radius * 0.55f, BlurMaskFilter.Blur.NORMAL)
                        native.drawCircle(point[0], point[1], radius, trailPaint)
                    }
                }
            }
        }
        content()
    }
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (a shl 24)
}

private fun extractAvatarTint(drawable: Drawable): Color {
    val sample = Bitmap.createBitmap(18, 18, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(sample)
    val bitmapDrawable = drawable as? BitmapDrawable
    val sourceBitmap = bitmapDrawable?.bitmap

    if (sourceBitmap != null) {
        // Coil may supply HARDWARE bitmaps, which cannot be drawn to a software Canvas.
        // Copy only those images to a mutable software bitmap before sampling their colors.
        val isHardwareBitmap =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                sourceBitmap.config == Bitmap.Config.HARDWARE
        val softwareBitmap = if (isHardwareBitmap) {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            sourceBitmap
        }
        if (softwareBitmap != null) {
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                softwareBitmap,
                null,
                android.graphics.Rect(0, 0, 18, 18),
                paint
            )
            if (softwareBitmap !== sourceBitmap) softwareBitmap.recycle()
        }
    } else {
        // Preserve the drawable's original bounds when sampling non-bitmap drawables.
        val previousBounds = android.graphics.Rect(drawable.bounds)
        drawable.setBounds(0, 0, 18, 18)
        drawable.draw(canvas)
        drawable.bounds = previousBounds
    }

    var hueX = 0.0
    var hueY = 0.0
    var saturationTotal = 0.0
    var valueTotal = 0.0
    var weightTotal = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until 18) {
        for (x in 0 until 18) {
            val pixel = sample.getPixel(x, y)
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (hsv[1] < 0.16f || hsv[2] < 0.12f || hsv[2] > 0.97f) continue
            val weight = hsv[1].toDouble() * (0.35 + hsv[2].toDouble() * 0.65)
            val radians = Math.toRadians(hsv[0].toDouble())
            hueX += kotlin.math.cos(radians) * weight
            hueY += kotlin.math.sin(radians) * weight
            saturationTotal += hsv[1] * weight
            valueTotal += hsv[2] * weight
            weightTotal += weight
        }
    }
    sample.recycle()
    if (weightTotal <= 0.0) return Color(0xFFD6B56C)
    val hue = ((Math.toDegrees(kotlin.math.atan2(hueY, hueX)) + 360.0) % 360.0).toFloat()
    val saturation = (saturationTotal / weightTotal).toFloat().coerceIn(0.34f, 0.78f)
    val value = (valueTotal / weightTotal).toFloat().coerceIn(0.68f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
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