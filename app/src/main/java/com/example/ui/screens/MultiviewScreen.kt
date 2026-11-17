package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceClassifier
import com.example.data.plugin.CloudStreamSearchItem
import com.example.player.CS3IPlayer
import com.example.player.multiview.CloudStreamMultiviewAdapter
import com.example.player.multiview.GoogleDriveMultiviewAdapter
import com.example.player.multiview.HttpFileServerMultiviewAdapter
import com.example.player.multiview.LocalFileMultiviewAdapter
import com.example.player.multiview.MultiviewResolveResult
import com.example.player.multiview.MultiviewResolvedSource
import com.example.player.multiview.MultiviewSlotStatus
import com.example.player.multiview.MultiviewSourceKind
import com.example.player.multiview.MultiviewSourceRegistry
import com.example.player.multiview.MultiviewSourceRequest
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

private data class MultiviewPluginSearchResult(
    val pluginId: String,
    val repositoryId: String,
    val sourceUrl: String,
    val item: CloudStreamSearchItem
)

private data class MultiviewSlotUi(
    val source: MultiviewResolvedSource? = null,
    val status: MultiviewSlotStatus = MultiviewSlotStatus.EMPTY,
    val error: String? = null
)

@Composable
fun MultiviewScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaItems by viewModel.mediaList.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val pluginRepositories by viewModel.pluginManager.repositories.collectAsState()
    val players = remember {
        List(4) { CS3IPlayer(context.applicationContext, scope, manageMediaSessionService = false) }
    }
    val slots = remember { mutableStateListOf<MultiviewSlotUi>().apply { repeat(4) { add(MultiviewSlotUi()) } } }
    var audioSlot by remember { mutableStateOf(0) }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var pickerKind by remember { mutableStateOf(MultiviewSourceKind.GOOGLE_DRIVE) }
    var fullscreenSlot by remember { mutableStateOf<Int?>(null) }
    var busySlot by remember { mutableStateOf<Int?>(null) }
    var pluginQuery by remember { mutableStateOf("") }
    var pluginSearchLoading by remember { mutableStateOf(false) }
    var pluginSearchResults by remember { mutableStateOf<List<MultiviewPluginSearchResult>>(emptyList()) }

    val registry = remember(viewModel) {
        MultiviewSourceRegistry(
            listOf(
                GoogleDriveMultiviewAdapter(),
                LocalFileMultiviewAdapter(),
                HttpFileServerMultiviewAdapter(),
                CloudStreamMultiviewAdapter { item ->
                    val streams = viewModel.resolveCloudStreamForMultiview(item)
                    val selected = streams.maxByOrNull { it.quality }
                    if (selected == null) {
                        MultiviewResolveResult.Failure("Plugin tidak menemukan stream.")
                    } else {
                        val headers = selected.headers.toMutableMap().apply {
                            if (selected.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) {
                                put("Referer", selected.referer)
                            }
                        }
                        val streamType = selected.streamType.substringAfterLast('.').uppercase()
                        val extension = runCatching { Uri.parse(selected.url).lastPathSegment.orEmpty().substringAfterLast('.', "").uppercase() }.getOrDefault("")
                        val mime = when {
                            streamType.contains("HLS") || streamType.contains("M3U8") || extension == "M3U8" -> "application/x-mpegURL"
                            streamType.contains("DASH") || streamType == "MPD" || extension == "MPD" -> "application/dash+xml"
                            streamType.contains("WEBM") || extension == "WEBM" -> "video/webm"
                            streamType.contains("MKV") || extension == "MKV" -> "video/x-matroska"
                            streamType.contains("MP4") || streamType.contains("VIDEO") || extension == "MP4" -> "video/mp4"
                            else -> null
                        }
                        MultiviewResolveResult.Success(
                            MultiviewResolvedSource(
                                uri = Uri.parse(selected.url),
                                title = item.title,
                                mimeType = mime,
                                requestHeaders = headers,
                                sourceKind = MultiviewSourceKind.CLOUDSTREAM,
                                cloudStream = selected,
                                pluginStreams = streams
                            )
                        )
                    }
                }
            )
        )
    }

    fun searchCloudStreamPlugins() {
        val query = pluginQuery.trim()
        if (query.isBlank() || pluginSearchLoading) return
        scope.launch {
            pluginSearchLoading = true
            pluginSearchResults = emptyList()
            val results = mutableListOf<MultiviewPluginSearchResult>()
            val entries = pluginRepositories.flatMap { repo ->
                repo.plugins.filter { entry ->
                    entry.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(entry)
                }.map { entry ->
                    Triple(entry, viewModel.pluginManager.cloudStreamPluginId(entry), viewModel.pluginManager.cloudStreamRepositoryId(entry))
                }
            }.distinctBy { it.second }
            entries.forEach { (entry, pluginId, repositoryId) ->
                runCatching { viewModel.pluginManager.searchCloudStreamPlugin(entry, query, 1) }
                    .getOrNull()?.items.orEmpty().forEach { item ->
                        results += MultiviewPluginSearchResult(pluginId, repositoryId, entry.url, item)
                    }
            }
            pluginSearchResults = results
            pluginSearchLoading = false
        }
    }

    fun playPluginSearchResult(index: Int, result: MultiviewPluginSearchResult) {
        val item = MediaItem(
            id = "multiview-cloudstream:${java.util.UUID.randomUUID()}",
            profileId = activeProfile?.id ?: "multiview",
            title = result.item.title,
            url = result.item.url,
            thumbnailUrl = result.item.posterUrl.orEmpty(),
            type = "CLOUDSTREAM",
            sourceType = "CLOUDSTREAM",
            category = "CloudStream",
            sourceUrl = result.sourceUrl,
            pluginId = result.pluginId,
            repositoryId = result.repositoryId,
            extraInfo = org.json.JSONObject().put("providerName", result.item.providerName).toString()
        )
        scope.launch {
            resolveAndPlay(
                index,
                MultiviewSourceRequest(
                    kind = MultiviewSourceKind.CLOUDSTREAM,
                    mediaItem = item,
                    displayName = item.title
                )
            )
        }
    }

    fun setAudioFocus(index: Int) {
        audioSlot = index
        players.forEachIndexed { playerIndex, player ->
            player.exoPlayer?.volume = if (playerIndex == index && slots[playerIndex].source != null) 1f else 0f
        }
    }

    fun clearSlot(index: Int) {
        players[index].stop()
        slots[index] = MultiviewSlotUi()
        if (audioSlot == index) {
            val nextAudio = slots.indices.firstOrNull { slots[it].source != null } ?: 0
            setAudioFocus(nextAudio)
        }
    }

    fun startResolvedSource(index: Int, source: MultiviewResolvedSource) {
        slots[index] = MultiviewSlotUi(source = source, status = MultiviewSlotStatus.READY)
        val plugin = source.cloudStream
        players[index].playWithMimeType(
            url = source.uri.toString(),
            title = source.title,
            requestHeaders = source.requestHeaders,
            mimeType = source.mimeType,
            externalSubtitles = plugin?.subtitles.orEmpty(),
            videoInterceptor = plugin?.videoInterceptor,
            pluginStreams = source.pluginStreams
        )
        setAudioFocus(audioSlot)
    }

    suspend fun resolveAndPlay(index: Int, request: MultiviewSourceRequest) {
        players[index].stop()
        slots[index] = MultiviewSlotUi(status = MultiviewSlotStatus.RESOLVING)
        setAudioFocus(audioSlot)
        busySlot = index
        when (val result = registry.resolve(request)) {
            is MultiviewResolveResult.Success -> startResolvedSource(index, result.source)
            is MultiviewResolveResult.Failure -> {
                slots[index] = MultiviewSlotUi(status = MultiviewSlotStatus.ERROR, error = result.reason)
                setAudioFocus(audioSlot)
            }
        }
        busySlot = null
        pickerSlot = null
    }

    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val index = pickerSlot
        if (uri != null && index != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                resolveAndPlay(
                    index,
                    MultiviewSourceRequest(
                        kind = MultiviewSourceKind.LOCAL_FILE,
                        uri = uri,
                        displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "File lokal"
                    )
                )
            }
        }
    }

    DisposableEffect(players) {
        players.forEachIndexed { index, player -> player.exoPlayer?.volume = if (index == 0) 1f else 0f }
        onDispose { players.forEach { runCatching { it.release() } } }
    }

    BackHandler(enabled = true) {
        if (fullscreenSlot != null) fullscreenSlot = null
        else if (pickerSlot != null) pickerSlot = null
        else onClose()
    }

    val focusedSlot = fullscreenSlot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D15))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFF8DE7FF), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("MULTIVIEW", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Empat sumber, satu layar", color = Color(0xFF9AAABD), style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Tutup Multiview", tint = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (focusedSlot != null) {
            MultiviewPanel(
                index = focusedSlot,
                slot = slots[focusedSlot],
                runtime = players[focusedSlot],
                isAudioActive = audioSlot == focusedSlot,
                onChoose = { pickerSlot = focusedSlot },
                onClose = { clearSlot(focusedSlot) },
                onAudio = { setAudioFocus(focusedSlot) },
                onFullscreen = { fullscreenSlot = null },
                modifier = Modifier.fillMaxSize().weight(1f)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    (0..1).forEach { index ->
                        MultiviewPanel(
                            index = index, slot = slots[index], runtime = players[index],
                            isAudioActive = audioSlot == index,
                            onChoose = { pickerSlot = index },
                            onClose = { clearSlot(index) },
                            onAudio = { setAudioFocus(index) },
                            onFullscreen = { fullscreenSlot = index },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    (2..3).forEach { index ->
                        MultiviewPanel(
                            index = index, slot = slots[index], runtime = players[index],
                            isAudioActive = audioSlot == index,
                            onChoose = { pickerSlot = index },
                            onClose = { clearSlot(index) },
                            onAudio = { setAudioFocus(index) },
                            onFullscreen = { fullscreenSlot = index },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
        Text(
            text = "Audio aktif: Panel ${audioSlot + 1}  •  Panel lain tetap berjalan tanpa suara",
            color = Color(0xFF9AAABD),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
    }

    val selectedIndex = pickerSlot
    if (selectedIndex != null) {
        val kindItems = when (pickerKind) {
            MultiviewSourceKind.GOOGLE_DRIVE -> mediaItems.filter { MediaSourceClassifier.isDriveMedia(it) }
            MultiviewSourceKind.CLOUDSTREAM -> mediaItems.filter { it.sourceType == "CLOUDSTREAM" && it.pluginId.isNotBlank() }
            MultiviewSourceKind.LOCAL_FILE -> mediaItems.filter { it.sourceType == "FILE" || it.url.startsWith("content://") || it.url.startsWith("file://") }
            MultiviewSourceKind.FILE_SERVER -> mediaItems.filter { it.sourceType == "SERVER" }
        }
        AlertDialog(
            onDismissRequest = { pickerSlot = null },
            title = { Text("Pilih sumber • Panel ${selectedIndex + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            MultiviewSourceKind.GOOGLE_DRIVE to "Drive",
                            MultiviewSourceKind.CLOUDSTREAM to "Plugin",
                            MultiviewSourceKind.LOCAL_FILE to "Lokal",
                            MultiviewSourceKind.FILE_SERVER to "Server"
                        ).forEach { (kind, label) ->
                            TextButton(onClick = { pickerKind = kind }) {
                                Text(label, color = if (pickerKind == kind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (pickerKind == MultiviewSourceKind.LOCAL_FILE) {
                        TextButton(onClick = { localPicker.launch(arrayOf("video/*", "audio/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Pilih file dari File Manager")
                        }
                    }
                    if (pickerKind == MultiviewSourceKind.CLOUDSTREAM) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = pluginQuery,
                                onValueChange = { pluginQuery = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Cari di plugin terpasang") }
                            )
                            IconButton(onClick = { searchCloudStreamPlugins() }, enabled = pluginQuery.isNotBlank() && !pluginSearchLoading) {
                                Icon(Icons.Default.Search, contentDescription = "Cari plugin")
                            }
                        }
                        if (pluginSearchLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        if (pluginSearchResults.isNotEmpty()) {
                            Column(
                                modifier = Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pluginSearchResults.take(25).forEach { result ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = busySlot == null) {
                                            playPluginSearchResult(selectedIndex, result)
                                        },
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                            Text(result.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${result.item.pluginName} • ${result.item.providerName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (kindItems.isEmpty()) {
                        Text(
                            when (pickerKind) {
                                MultiviewSourceKind.GOOGLE_DRIVE -> "Belum ada media Google Drive di koleksi NSiO."
                                MultiviewSourceKind.CLOUDSTREAM -> "Belum ada riwayat atau item CloudStream tersimpan. Pilih konten plugin terlebih dahulu dari katalog NSiO."
                                MultiviewSourceKind.LOCAL_FILE -> "Pilih file dari File Manager di atas."
                                MultiviewSourceKind.FILE_SERVER -> "Belum ada sumber server. Slot ini sudah disiapkan untuk sumber HTTP/HTTPS saat server tersedia."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            kindItems.forEach { item ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = busySlot == null) {
                                        scope.launch {
                                            resolveAndPlay(
                                                selectedIndex,
                                                MultiviewSourceRequest(kind = pickerKind, mediaItem = item, displayName = item.title)
                                            )
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when (pickerKind) {
                                                MultiviewSourceKind.GOOGLE_DRIVE -> Icons.Default.Cloud
                                                MultiviewSourceKind.CLOUDSTREAM -> Icons.Default.Extension
                                                MultiviewSourceKind.LOCAL_FILE -> Icons.Default.FolderOpen
                                                MultiviewSourceKind.FILE_SERVER -> Icons.Default.Cloud
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (busySlot == selectedIndex) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerSlot = null }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun MultiviewPanel(
    index: Int,
    slot: MultiviewSlotUi,
    runtime: CS3IPlayer,
    isAudioActive: Boolean,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    onAudio: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by runtime.playerState.collectAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF111A27))
            .border(1.dp, if (isAudioActive) Color(0xFF5AC8FA).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(17.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("P${index + 1}", color = Color(0xFF8DE7FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                slot.source?.title ?: "Pilih video",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (slot.source != null) {
                IconButton(onClick = onAudio, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Pilih audio panel ${index + 1}", tint = if (isAudioActive) Color(0xFF8DE7FF) else Color(0xFF9AAABD), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Perbesar panel ${index + 1}", tint = Color.White, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kosongkan panel ${index + 1}", tint = Color(0xFFFF9A9A), modifier = Modifier.size(17.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.Black)
                .clickable(onClick = onChoose),
            contentAlignment = Alignment.Center
        ) {
            if (slot.source != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            this.player = runtime.exoPlayer
                        }
                    },
                    update = { view -> view.player = runtime.exoPlayer }
                )
                if (playerState.isBuffering || slot.status == MultiviewSlotStatus.RESOLVING) {
                    CircularProgressIndicator(color = Color(0xFF8DE7FF), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                if (playerState.error != null || slot.error != null) {
                    Text(
                        slot.error ?: playerState.error ?: "Gagal memutar",
                        color = Color(0xFFFFA6A6),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.75f)).padding(5.dp)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF8DE7FF), modifier = Modifier.size(28.dp))
                    Text("Tambah video", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text("Panel ${index + 1}", color = Color(0xFF9AAABD), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    slot.status == MultiviewSlotStatus.ERROR || playerState.error != null -> "Error"
                    playerState.isBuffering -> "Buffering"
                    playerState.isPlaying -> "Memutar"
                    slot.source != null -> "Siap / dijeda"
                    else -> "Kosong"
                },
                color = if (slot.status == MultiviewSlotStatus.ERROR || playerState.error != null) Color(0xFFFF9A9A) else Color(0xFF9AAABD),
                style = MaterialTheme.typography.labelSmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (slot.source != null) {
                    IconButton(
                        onClick = { runtime.togglePlayPause() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                TextButton(onClick = onChoose) { Text("Sumber", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
