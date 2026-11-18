package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.AuthState
import com.example.data.model.MediaSourceClassifier
import com.example.data.model.MediaItem
import com.example.data.model.WatchHistoryPolicy
import com.example.ui.MainViewModel
import com.example.ui.components.MediaGridCard
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary
import com.example.embedded.engine.EmbeddedModuleManager
import com.example.embedded.modules.ddtv.server.DdtvCategory
import org.json.JSONObject
import com.example.embedded.api.ModuleMediaBridgeRegistry
import com.example.embedded.api.ModuleMediaRequest
import com.example.embedded.api.ModuleMediaResult
import com.example.embedded.api.ModuleMediaSource
import com.example.embedded.host.CatalogMediaAdapter
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToExtension: () -> Unit,
    onNavigateToMultiview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    var selectedVideoFilters by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val authState by viewModel.authState.collectAsState()
    val isSyncing by viewModel.isCloudSyncing.collectAsState()
    val sourcePlaylists by viewModel.sourcePlaylists.collectAsState()
    val cloudStreamRepositories by viewModel.pluginManager.repositories.collectAsState()
    val isLoadingMoreCloudStreamHome by viewModel.isLoadingMoreCloudStreamHome.collectAsState()
    val cloudStreamSourceHealth by viewModel.cloudStreamSourceHealth.collectAsState()
    val cloudStreamPluginOrder by viewModel.cloudStreamPluginOrder.collectAsState()
    val refreshingCloudStreamPluginId by viewModel.refreshingCloudStreamPluginId.collectAsState()
    val cloudStreamPluginFilters = remember(cloudStreamRepositories) {
        cloudStreamRepositories.flatMap { repo ->
            repo.plugins.filter { entry ->
                entry.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(entry)
            }.map { entry ->
                viewModel.pluginManager.cloudStreamPluginId(entry) to entry.name
            }
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val drivePlaylists = remember(sourcePlaylists, mediaList) {
        sourcePlaylists.filter { playlist ->
            playlist.sourceType.equals("DRIVE", ignoreCase = true) ||
                MediaSourceClassifier.isGoogleDriveUrl(playlist.url) ||
                mediaList.any { item ->
                    item.playlistId == playlist.id && MediaSourceClassifier.isDriveMedia(item)
                }
        }.sortedBy { it.name.lowercase() }
    }
    val drivePlaylistIds = remember(drivePlaylists) { drivePlaylists.mapTo(hashSetOf()) { it.id } }
    val orderedCloudStreamPluginFilters = remember(cloudStreamPluginFilters, cloudStreamPluginOrder) {
        cloudStreamPluginFilters.sortedWith(compareBy<Pair<String, String>> { pair ->
            val index = cloudStreamPluginOrder.indexOf(pair.first)
            if (index < 0) Int.MAX_VALUE else index
        }.thenBy { it.second.lowercase() })
    }
    val cloudStreamRepositoryFilters = remember(cloudStreamRepositories) {
        cloudStreamRepositories.mapNotNull { repo ->
            repo.plugins.firstOrNull()?.let { entry ->
                viewModel.pluginManager.cloudStreamRepositoryId(entry).takeIf { it.isNotBlank() } to repo.name
            }?.takeIf { !it.first.isNullOrBlank() }?.let { it.first!! to it.second }
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val isServerUnlocked by viewModel.isServerUnlocked.collectAsState()

    // 2-stage interaction: Normal compact mode vs Floating Curtain Overlay
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var cloudStreamSearchRequest by rememberSaveable { mutableStateOf(0) }
    var showServerPinDialog by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var showQuickActions by rememberSaveable { mutableStateOf(false) }
    var showDdtvPrototypeDialog by rememberSaveable { mutableStateOf(false) }
    var ddtvSourceFilter by rememberSaveable { mutableStateOf("ALL") }
    var ddtvLaunchError by remember { mutableStateOf<String?>(null) }
    var ddtvServerStatus by rememberSaveable { mutableStateOf("Belum terhubung") }
    var ddtvServerLoading by remember { mutableStateOf(false) }
    var ddtvServerCategories by remember { mutableStateOf<List<DdtvCategory>>(emptyList()) }
    var ddtvServerVideos by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var ddtvSelectedCategory by remember { mutableStateOf<DdtvCategory?>(null) }
    val appContext = LocalContext.current
    val ddtvScope = androidx.compose.runtime.rememberCoroutineScope()
    val ddtvMediaBridge = remember(appContext, viewModel, drivePlaylistIds) {
        ModuleMediaBridgeRegistry(
            listOf(
                CatalogMediaAdapter(ModuleMediaSource.DRIVE, { viewModel.mediaList.value }, viewModel::playMedia) { drivePlaylistIds },
                CatalogMediaAdapter(ModuleMediaSource.CLOUD_STREAM, { viewModel.mediaList.value }, viewModel::playMedia),
                CatalogMediaAdapter(ModuleMediaSource.FILE_MANAGER, { viewModel.mediaList.value }, viewModel::playMedia)
            )
        )
    }
    val embeddedModuleManager = remember(appContext, ddtvMediaBridge) {
        EmbeddedModuleManager(appContext, ddtvMediaBridge)
    }
    val embeddedModuleStates by embeddedModuleManager.states.collectAsState()
    val ddtvModuleState = embeddedModuleStates.firstOrNull { it.metadata.id == "dev.andikune.nsio.ddtv" }
    val ddtvHostSources = remember(embeddedModuleManager) { embeddedModuleManager.availableMediaSources() }

    val ddtvLocalFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Video lokal"
            val profile = activeProfile
            if (profile == null) {
                ddtvLaunchError = "Pilih profil NSiO terlebih dahulu."
            } else {
                viewModel.playMedia(
                    MediaItem(
                        id = "ddtv-local:${uri}",
                        profileId = profile.id,
                        title = displayName,
                        url = uri.toString(),
                        type = "VIDEO",
                        sourceType = "FILE",
                        category = "DDTV • File lokal",
                        createdAt = System.currentTimeMillis()
                    )
                )
                showDdtvPrototypeDialog = false
                embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
            }
        }
    }
    var expandedPlaylistCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCloudStreamOrderDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistFilter by remember { mutableStateOf<String?>(null) }
    var expandedSourceType by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedSearchFocusRequester = remember { FocusRequester() }

    // Intercept back button to close the floating curtain if open
    BackHandler(enabled = isSearchExpanded || showQuickActions || showPlaylistPicker || showCloudStreamOrderDialog || expandedSourceType != null || expandedPlaylistCategory != null) {
        when {
            showCloudStreamOrderDialog -> showCloudStreamOrderDialog = false
            showQuickActions -> showQuickActions = false
            expandedPlaylistCategory != null -> expandedPlaylistCategory = null
            showPlaylistPicker -> showPlaylistPicker = false
            isSearchExpanded -> isSearchExpanded = false
            else -> expandedSourceType = null
        }
    }

    LaunchedEffect(activeProfile?.id, selectedFilter) {
        val selectedCloudStreamPluginId = selectedFilter
            .takeIf { it.startsWith("CLOUDSTREAM_PLUGIN:") }
            ?.removePrefix("CLOUDSTREAM_PLUGIN:")
        if (activeProfile != null && !selectedCloudStreamPluginId.isNullOrBlank()) {
            viewModel.refreshCloudStreamCatalogOnHome(selectedCloudStreamPluginId)
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            try {
                expandedSearchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
    val headerReflection = rememberInfiniteTransition(label = "header_glass_reflection")
    val headerReflectionPosition by headerReflection.animateFloat(
        initialValue = -0.85f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "header_glass_reflection_position"
    )
    val accentColor = MaterialTheme.colorScheme.primary

    fun matchesVideoCategory(category: String, metadata: String): Boolean {
        val value = metadata.lowercase()
        return when (category) {
            // Mirror CloudStream TvType groupings: AnimeMovie and OVA belong to Anime,
            // while TvSeries, AsianDrama, Cartoon and NSFW remain distinct types.
            "Movie" -> (value.contains("movie") || value.contains("film")) &&
                !value.contains("anime movie") && !value.contains("animemovie")
            "Seri TV" -> value.contains("tvseries") || value.contains("tv series") ||
                value.contains("series") || value.contains("serial")
            "Anime" -> value.contains("anime") || value.contains("ova")
            "Drama Asia" -> value.contains("asiandrama") || value.contains("asian drama") ||
                value.contains("k-drama") || value.contains("cdrama") || value.contains("j-drama")
            "Dubb Indo" -> value.contains("dubb indo") || value.contains("dub indo") ||
                value.contains("indonesian dub") || value.contains("bahasa indonesia") ||
                value.contains("audio indonesia") || value.contains("language:id")
            "Kartun" -> value.contains("cartoon") || value.contains("animation") || value.contains("kartun")
            "NSFW" -> value.contains("nsfw")
            else -> true
        }
    }

    val filteredItems = mediaList.filter { item ->
        val matchesServerAccess = item.sourceType != "SERVER" || isServerUnlocked
        val matchesSearch = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
        val matchesExpandedSource = expandedSourceType == null || item.sourceType.equals(expandedSourceType, ignoreCase = true)
        val matchesVideoCategories = selectedVideoFilters.isEmpty() || selectedVideoFilters.any { category -> matchesVideoCategory(category, "${item.type} ${item.category} ${item.title}") }
        val isDriveMedia = MediaSourceClassifier.isDriveMedia(item, drivePlaylistIds)
        val matchesFilter = when (selectedFilter) {
            "ALL_SOURCES" -> isDriveMedia || item.sourceType == "CLOUDSTREAM"
            "DRIVE_ALL" -> isDriveMedia
            "CLOUDSTREAM_ALL" -> item.sourceType == "CLOUDSTREAM"
            "LINK" -> item.sourceType == "LINK"
            "STREAM" -> item.sourceType == "STREAM"
            "REPO" -> item.sourceType == "REPO"
            "CLOUDSTREAM" -> item.sourceType == "CLOUDSTREAM"
            "FILE" -> item.sourceType == "FILE"
            "SERVER" -> item.sourceType == "SERVER"
            "FAV" -> item.isFavorite
            else -> if (selectedFilter.startsWith("CATEGORY:")) {
                val selectedCategory = selectedFilter.removePrefix("CATEGORY:")
                item.category.split("•", ",", "|", ";")
                    .any { it.trim().equals(selectedCategory, ignoreCase = true) }
            } else if (selectedFilter.startsWith("DRIVE_PLAYLIST:")) {
                isDriveMedia && item.playlistId == selectedFilter.removePrefix("DRIVE_PLAYLIST:")
            } else if (selectedFilter.startsWith("PLAYLIST:")) {
                item.playlistId == selectedFilter.removePrefix("PLAYLIST:")
            } else if (selectedFilter.startsWith("CLOUDSTREAM_PLUGIN:")) {
                item.sourceType == "CLOUDSTREAM" && item.pluginId == selectedFilter.removePrefix("CLOUDSTREAM_PLUGIN:")
            } else if (selectedFilter.startsWith("CLOUDSTREAM_REPO:")) {
                item.sourceType == "CLOUDSTREAM" && item.repositoryId == selectedFilter.removePrefix("CLOUDSTREAM_REPO:")
            } else true
        }
        matchesServerAccess && matchesSearch && matchesFilter && matchesExpandedSource && matchesVideoCategories
    }

    val filteredList = when (selectedSort) {
        "TITLE" -> filteredItems.sortedBy { it.title.trim().lowercase() }
"CONTINUE" -> WatchHistoryPolicy.continueWatching(filteredItems)
        "HISTORY" -> WatchHistoryPolicy.recentlyWatched(filteredItems)
        "FAVORITES" -> filteredItems.filter { it.isFavorite }.sortedByDescending { it.createdAt }
        else -> filteredItems.sortedByDescending { it.createdAt }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Keep the home content clear of the floating global navigation controls.
            .padding(
                start = if (settings.displayMode == "TV") 84.dp else 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = if (settings.displayMode == "HP") 0.dp else 8.dp
            )
            .testTag("home_screen")
    ) {
        val containerWidth = maxWidth
        val isNarrow = containerWidth < 600.dp
        val isVeryNarrow = containerWidth < 420.dp
        val showHeaderLabels = containerWidth >= 460.dp
        // Separate phone/TV grid settings; AUTO follows available width.
        val isPhoneLayout = when (settings.displayMode) {
            "HP" -> true
            "TV" -> false
            else -> containerWidth < 600.dp
        }
        val columns = (if (isPhoneLayout) settings.gridColumnsPhone else settings.gridColumnsTv).coerceIn(1, 8)

        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // BAGIAN PERTAMA: HEADER PERMANEN RAMPING (Compact Permanent Top Bar)
            // Nama Aplikasi + Search Bar di Tengah + Tombol TV & Cloud di Kanan
            // Filter disembunyikan agar hemat tempat & grid video jauh lebih luas
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = if (isDark) 0.18f else 0.48f))
                    .drawBehind {
                        val sweepX = size.width * headerReflectionPosition
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.28f to Color(0xFF72D8FF).copy(alpha = 0.13f),
                                    0.48f to Color(0xFFB9A0FF).copy(alpha = 0.17f),
                                    0.66f to Color(0xFFFFC6E8).copy(alpha = 0.12f),
                                    1.00f to Color.Transparent
                                ),
                                start = Offset(sweepX - size.width * 0.58f, 0f),
                                end = Offset(sweepX + size.width * 0.58f, size.height)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                        )
                    }
                    .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Kiri: Nama Aplikasi & Badge Jumlah Media
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = if (isVeryNarrow) "NSiO" else "Net Stream iO",
                        fontSize = if (isNarrow) 16.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Box(
                        modifier = Modifier
                            
                            .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${filteredList.size}",
                            fontSize = 10.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Tengah: Search Bar Ringkas di Tengah-Tengah Header
                // Memakai space yang tersisa tanpa terpotong
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 48.dp, max = 360.dp)
                        .height(36.dp)
                        .padding(horizontal = 6.dp)
                        
                        .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { isSearchExpanded = true }
                        .liquidGlassTvFocusable(shape = RoundedCornerShape(20.dp))
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                isSearchExpanded = true
                            }
                        }
                        .testTag("home_compact_search_button"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buka Pencarian & Filter",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = searchQuery.ifEmpty { "Cari video & filter..." },
                            fontSize = 11.sp,
                            color = if (searchQuery.isNotEmpty()) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Hapus teks",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Kanan: Tombol TV/Display & Cloud Sync Status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Mode Display (TV / HP)
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            
                            .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.toggleDisplayMode() }
                            .liquidGlassTvFocusable(shape = RoundedCornerShape(18.dp))
                            .padding(horizontal = 9.dp)
                            .testTag("home_rotate_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Ubah Orientasi",
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = settings.displayMode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                        }
                    }

                    // Tombol Cloud Sync Status (Firebase)
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            
                            .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                if (authState is AuthState.Authenticated) {
                                    viewModel.syncToCloud()
                                } else {
                                    viewModel.signInWithGoogle()
                                }
                            }
                            .liquidGlassTvFocusable(shape = RoundedCornerShape(18.dp))
                            .padding(horizontal = 9.dp)
                            .testTag("home_cloud_status_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor
                                )
                            } else {
                                Icon(
                                    imageVector = if (authState is AuthState.Authenticated) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = "Cloud Status",
                                    tint = if (authState is AuthState.Authenticated) accentColor else Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            val cloudLabel = when (authState) {
                                is AuthState.Authenticated -> (authState as AuthState.Authenticated).user.displayName?.split(" ")?.firstOrNull() ?: "Cloud Aktif"
                                is AuthState.Loading -> "Menghubungkan..."
                                else -> "Login Cloud"
                            }
                            if (showHeaderLabels) {
                                Text(
                                    text = cloudLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            }

            // Filter kategori selalu terlihat di Extension Home; geser horizontal untuk semua pilihan.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Movie", "Seri TV", "Anime", "Drama Asia", "Dubb Indo", "Kartun", "NSFW").forEach { category ->
                    val active = category in selectedVideoFilters
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                            .border(1.dp, if (active) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                            .clickable { selectedVideoFilters = if (active) selectedVideoFilters - category else selectedVideoFilters + category }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                            .testTag("home_video_filter_$category")
                    ) {
                        Text(category, fontSize = 10.sp, color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            // ==========================================
            // KONTEN UTAMA: Grid Video & Overlay Tirai Mengambang
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // LAPISAN 1: GRID VIDEO / EMPTY STATE (Permanen di bawah Header)
                val showCloudStreamHome = cloudStreamPluginFilters.isNotEmpty() &&
                    (selectedFilter in setOf("ALL", "ALL_SOURCES", "CLOUDSTREAM_ALL", "CLOUDSTREAM") ||
                        selectedFilter.startsWith("CLOUDSTREAM_PLUGIN:"))
                if (filteredList.isEmpty() && !showCloudStreamHome && cloudStreamPluginFilters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .liquidGlass(
                                    shape = RoundedCornerShape(26.dp),
                                    alpha = if (isDark) 0.15f else 0.88f,
                                    borderAlpha = 0.35f,
                                    elevation = 12.dp
                                )
                                .padding(horizontal = 26.dp, vertical = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(50.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Tidak ada hasil untuk '$searchQuery'" else "Belum Ada Konten",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Buka Daftar Playlist untuk mengelola playlist, sumber konten, dan plugin.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(320.dp),
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onNavigateToExtension,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(20.dp))
                                        .testTag("btn_go_to_extension")
                                ) {
                                    Text(
                                        "Buka Daftar Playlist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(top = 4.dp, bottom = if (isPhoneLayout) 96.dp else 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (cloudStreamPluginFilters.isNotEmpty()) {
                            item(
                                key = "cloudstream_home_catalog_browser",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                CloudStreamSearchPanel(
                                    viewModel, cloudStreamRepositories, searchQuery, cloudStreamSearchRequest,
                                    selectedPluginFilter = selectedFilter,
                                    selectedVideoFilters = selectedVideoFilters,
                                    handlePendingDetail = false
                                )
                            }
                        }
                        val sourceGroups = filteredList
                            .groupBy { it.sourceType.uppercase() }
                            .toSortedMap(compareBy<String> {
                                when (it) {
                                    "LINK" -> 0
                                    "DRIVE" -> 1
                                    "STREAM" -> 2
                                    "REPO" -> 3
                                    "CLOUDSTREAM" -> 4
                                    "FILE" -> 5
                                    "SERVER" -> 6
                                    else -> 6
                                }
                            }.thenBy { it })
                        sourceGroups.forEach { (sourceType, sourceItems) ->
                            item(
                                key = "source_header_$sourceType",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSourceType = if (expandedSourceType == sourceType) null else sourceType }
                                        .padding(top = 10.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (if (expandedSourceType == sourceType) "‹  " else "›  ") + when (sourceType) {
                                            "LINK" -> "Playlist / Link"
                                            "DRIVE" -> "Google Drive"
                                            "STREAM" -> "Stream"
                                            "REPO" -> "Repository"
                                            "CLOUDSTREAM" -> "CloudStream"
                                            "FILE" -> "File Lokal"
                                            "SERVER" -> "Server"
                                            else -> sourceType.ifBlank { "Sumber Lain" }
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (expandedSourceType == sourceType) "Tutup katalog" else "Lihat semua • ${sourceItems.size} item",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val selectedCloudStreamPluginId = selectedFilter
                                            .takeIf { it.startsWith("CLOUDSTREAM_PLUGIN:") }
                                            ?.removePrefix("CLOUDSTREAM_PLUGIN:")
                                            ?.takeIf { it.isNotBlank() }
                                        if (sourceType == "CLOUDSTREAM" && selectedCloudStreamPluginId != null) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.loadMoreCloudStreamCatalogOnHome(selectedCloudStreamPluginId)
                                                },
                                                enabled = !isLoadingMoreCloudStreamHome,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                if (isLoadingMoreCloudStreamHome) {
                                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudSync,
                                                        contentDescription = "Muat halaman berikutnya dari plugin terpilih",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (sourceType == "CLOUDSTREAM") {
                                val pluginOrder = orderedCloudStreamPluginFilters.mapIndexed { index, pair ->
                                    pair.first to index
                                }.toMap()
                                val pluginNames = orderedCloudStreamPluginFilters.associate { it.first to it.second }
                                val sectionGroups = sourceItems
                                    .flatMap { mediaItem ->
                                        mediaItem.category.split(" • ")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                            .map { sectionName -> sectionName to mediaItem }
                                    }
                                    .groupBy { (sectionName, mediaItem) -> mediaItem.pluginId to sectionName }
                                    .toSortedMap(
                                        compareBy<Pair<String, String>> { pluginOrder[it.first] ?: Int.MAX_VALUE }
                                            .thenBy { it.second.lowercase() }
                                    )

                                sectionGroups.forEach { (sectionKey, memberships) ->
                                    val pluginId = sectionKey.first
                                    val sectionName = sectionKey.second
                                    val sectionItems = memberships.map { it.second }.distinctBy { it.id }
                                    val pluginName = pluginNames[pluginId] ?: sectionItems.firstOrNull()?.extraInfo.orEmpty()
                                    item(
                                        key = "cloudstream_section_header_${pluginId}_${sectionName}",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, top = 5.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sectionName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = pluginName,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${sectionItems.size}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (expandedSourceType == sourceType) {
                                        items(
                                            items = sectionItems,
                                            key = { "${pluginId}_${sectionName}_${it.id}" },
                                            span = { GridItemSpan(1) }
                                        ) { mediaItem ->
                                            MediaGridCard(
                                                item = mediaItem,
                                                onClick = { viewModel.requestCloudStreamDetail(mediaItem) },
                                                onLongClick = { viewModel.toggleFavorite(mediaItem) },
                                                onDownload = { viewModel.downloadMedia(mediaItem) }
                                            )
                                        }
                                    } else {
                                        item(
                                            key = "horizontal_cloudstream_${pluginId}_${sectionName}",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp)
                                            ) {
                                                lazyRowItems(
                                                    items = sectionItems,
                                                    key = { pluginId + "_" + sectionName + "_" + it.id }
                                                ) { mediaItem ->
                                                    MediaGridCard(
                                                        item = mediaItem,
                                                        modifier = Modifier.width(148.dp),
                                                        onClick = { viewModel.requestCloudStreamDetail(mediaItem) },
                                                        onLongClick = { viewModel.toggleFavorite(mediaItem) },
                                                        onDownload = { viewModel.downloadMedia(mediaItem) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val playlistNames = sourcePlaylists.associate { it.id to it.name }
                                val playlistGroups = sourceItems
                                    .groupBy { it.playlistId.ifBlank { "__no_playlist__" } }
                                    .toSortedMap(compareBy<String> { it == "__no_playlist__" }
                                        .thenBy { playlistId ->
                                            playlistNames[playlistId]?.lowercase().orEmpty().ifBlank { playlistId.lowercase() }
                                        })
                                playlistGroups.forEach { (playlistId, playlistItems) ->
                                    val playlistTitle = if (playlistId == "__no_playlist__") {
                                        "Item tanpa playlist"
                                    } else {
                                        playlistNames[playlistId] ?: "Playlist tidak dikenal"
                                    }
                                    item(
                                        key = "playlist_header_${sourceType}_${playlistId}",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = playlistTitle,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${playlistItems.size}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (expandedSourceType == sourceType) {
                                        items(
                                            items = playlistItems,
                                            key = { it.id },
                                            span = { GridItemSpan(1) }
                                        ) { mediaItem ->
                                            MediaGridCard(
                                                item = mediaItem,
                                                onClick = { viewModel.playMedia(mediaItem) },
                                                onLongClick = { viewModel.toggleFavorite(mediaItem) },
                                                onDownload = { viewModel.downloadMedia(mediaItem) }
                                            )
                                        }
                                    } else {
                                        item(
                                            key = "horizontal_catalog_" + sourceType + "_" + playlistId,
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                playlistItems.forEach { mediaItem ->
                                                    MediaGridCard(
                                                        item = mediaItem,
                                                        modifier = Modifier.width(148.dp),
                                                        onClick = { viewModel.playMedia(mediaItem) },
                                                        onLongClick = { viewModel.toggleFavorite(mediaItem) },
                                                        onDownload = { viewModel.downloadMedia(mediaItem) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Persistent request host: remains composed even when the grid scrolls or
                // the visible CloudStream search panel is not available.
                CloudStreamSearchPanel(
                    viewModel = viewModel,
                    repos = cloudStreamRepositories,
                    query = searchQuery,
                    searchRequest = cloudStreamSearchRequest,
                    selectedPluginFilter = selectedFilter,
                    selectedVideoFilters = selectedVideoFilters,
                    renderContent = false,
                    handlePendingDetail = true,
                    enableSearch = false
                )

                // LAPISAN 2: BACKDROP REDUP KETIKA TIRAI TERBUKA (Tap di luar untuk menutup)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = fadeIn(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isDark) 0.55f else 0.28f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isSearchExpanded = false
                            }
                    )
                }

                // ==========================================
                // BAGIAN TAMPILAN KE 2: ANIMASI TIRAI TURUN MENGAMBANG (Curtain Dropdown Overlay)
                // Search bar yang diperluas ke bawah + Filter Chips lengkap
                // ==========================================
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = expandVertically(
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Top
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(
                        animationSpec = spring(dampingRatio = 0.90f, stiffness = Spring.StiffnessMedium),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(animationSpec = tween(150)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    // Wadah Kaca Tirai Mengambang (iOS 26 Liquid Glass)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, start = 4.dp, end = 4.dp, bottom = 12.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(24.dp),
                                alpha = if (isDark) 0.28f else 0.94f,
                                borderAlpha = if (isDark) 0.55f else 0.45f,
                                elevation = 16.dp
                            )
                            .border(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFFCBD5E1),
                                RoundedCornerShape(24.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Cegah klik tembus ke luar */ }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Search Bar Luas & Tombol Tutup Tirai
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            "Cari judul video, film, atau kategori...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Cari",
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                    Icon(
                                                        Icons.Default.Clear,
                                                        contentDescription = "Hapus teks",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                enabled = searchQuery.isNotBlank() && selectedFilter.startsWith("CLOUDSTREAM_PLUGIN:"),
                                                onClick = {
                                                    cloudStreamSearchRequest++
                                                    isSearchExpanded = false
                                                },
                                                modifier = Modifier.testTag("home_cloudstream_search_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "Cari di plugin CloudStream terpilih",
                                                    tint = if (searchQuery.isNotBlank() && selectedFilter.startsWith("CLOUDSTREAM_PLUGIN:")) accentColor
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentColor,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White,
                                        unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.85f),
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(expandedSearchFocusRequester)
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(20.dp))
                                        .testTag("home_expanded_search_field")
                                )

                                // Tombol Lipat Tirai (Tutup ke atas)
                                IconButton(
                                    onClick = { isSearchExpanded = false },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .liquidGlass(
                                            shape = CircleShape,
                                            alpha = if (isDark) 0.15f else 0.85f,
                                            borderAlpha = 0.4f
                                        )
                                        .liquidGlassTvFocusable(shape = CircleShape)
                                        .testTag("close_curtain_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Tutup Tirai",
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Filter kategori yang sama juga berlaku untuk hasil pencarian CloudStream.
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Movie", "Seri TV", "Anime", "Drama Asia", "Dubb Indo", "Kartun", "NSFW").forEach { category ->
                                    val active = category in selectedVideoFilters
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(if (active) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                            .border(1.dp, if (active) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                                            .clickable { selectedVideoFilters = if (active) selectedVideoFilters - category else selectedVideoFilters + category }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(category, fontSize = 11.sp, color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }

                            // Pengurutan katalog: hanya opsi yang didukung data lokal saat ini.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sortOptions = listOf(
                                    "RECENT" to "Terbaru",
                                    "TITLE" to "A–Z",
                                    "CONTINUE" to "Lanjutkan",
                                    "HISTORY" to "Riwayat",
                                    "FAVORITES" to "Favorit"
                                )
                                sortOptions.forEach { (key, label) ->
                                    val selected = selectedSort == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                            .border(1.dp, if (selected) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                            .clickable { viewModel.selectSort(key) }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }

                            // 2. Baris Filter Chips Mengambang (Semua, Link Ekstensi, File Lokal, Server, Favorit)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sourceFilters = listOf(
                                    "ALL_SOURCES" to "Semua Sumber",
                                    "ALL" to "Semua",
                                    "LINK" to "Link Ekstensi",
                                    "STREAM" to "Streaming M3U/M3U8",
                                    "REPO" to "Repo CloudStream",
                                    "CLOUDSTREAM" to "Katalog CloudStream",
                                    "FILE" to "File Lokal",
                                    "SERVER" to "Server Upload",
                                    "FAV" to "Favorit"
                                )
                                val categoryFilters = mediaList
                                    .flatMap { item -> item.category.split("•", ",", "|", ";") }
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && !it.equals("Tanpa Kategori", ignoreCase = true) }
                                    .distinctBy { it.lowercase() }
                                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                                    .map { "CATEGORY:$it" to it }
                                val playlistFilters = sourcePlaylists.map { playlist ->
                                    "PLAYLIST:${playlist.id}" to "Playlist: ${playlist.name}"
                                }
                                val cloudStreamPluginChips = orderedCloudStreamPluginFilters.map { (pluginId, name) ->
                                    val health = cloudStreamSourceHealth[pluginId]
                                    val cachedCount = mediaList.count { it.sourceType == "CLOUDSTREAM" && it.pluginId == pluginId }
                                    val healthLabel = when {
                                        health?.lastError != null -> " • Gagal ${health.consecutiveFailures}×"
                                        health?.hasNext == false -> " • Selesai"
                                        (health?.lastSuccessAt ?: 0L) > 0L -> " • Aktif"
                                        cachedCount > 0 -> " • Cache $cachedCount"
                                        else -> ""
                                    }
                                    val responseLabel = health?.lastResponseTimeMs?.takeIf { it > 0L }?.let { " · ${it}ms" } ?: ""
                                    "CLOUDSTREAM_PLUGIN:$pluginId" to "Plugin: $name$healthLabel$responseLabel"
                                }
                                val cloudStreamRepositoryChips = cloudStreamRepositoryFilters.map { (repositoryId, name) ->
                                    "CLOUDSTREAM_REPO:$repositoryId" to "Repo: $name"
                                }
                                val filters = sourceFilters + cloudStreamPluginChips + cloudStreamRepositoryChips + playlistFilters + categoryFilters

                                if (cloudStreamPluginFilters.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showCloudStreamOrderDialog = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Reorder,
                                            contentDescription = "Atur urutan plugin CloudStream",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                filters.forEach { (key, label) ->
                                    val isSelected = selectedFilter == key
                                    Box(
                                        modifier = Modifier
                                            .liquidGlass(
                                                shape = RoundedCornerShape(18.dp),
                                                alpha = if (isSelected) (if (isDark) 0.28f else 0.95f) else (if (isDark) 0.08f else 0.65f),
                                                borderAlpha = if (isSelected) 0.70f else 0.25f,
                                                elevation = if (isSelected) 6.dp else 2.dp
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentColor else Color.Transparent,
                                                RoundedCornerShape(18.dp)
                                            )
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable {
                                                if (key == "SERVER" && !isServerUnlocked) {
                                                    showServerPinDialog = true
                                                } else {
                                                    viewModel.selectFilter(key)
                                                }
                                            }
                                            .liquidGlassTvFocusable(shape = RoundedCornerShape(18.dp))
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                            .testTag("filter_chip_$key")
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 3. Status Bar Kecil Penjelas Hasil & Petunjuk
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Menampilkan ${filteredList.size} media" + if (searchQuery.isNotEmpty()) " untuk '$searchQuery'" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Tekan kembali atau area luar untuk menutup tirai",
                                    fontSize = 10.sp,
                                    color = accentColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                // Playlist picker remains available from the existing source-filter interaction.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showPlaylistPicker,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 230.dp, max = 290.dp)
                                    .heightIn(max = 340.dp)
                                    .liquidGlass(
                                        shape = RoundedCornerShape(22.dp),
                                        alpha = if (isDark) 0.94f else 0.98f,
                                        borderAlpha = if (isDark) 0.55f else 0.75f,
                                        elevation = 18.dp
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(22.dp)
                                    )
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (expandedPlaylistCategory == null) "Pilih Sumber Playlist" else
                                        if (expandedPlaylistCategory == "DRIVE") "Pilih Drive" else "Pilih Plugin CloudStream",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Pilih satu kategori atau satu sumber untuk memfilter katalog.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (expandedPlaylistCategory != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .clickable { expandedPlaylistCategory = null }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("‹  Kembali ke semua sumber", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (expandedPlaylistCategory == null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(if (selectedFilter == "ALL_SOURCES") accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable {
                                                viewModel.selectFilter("ALL_SOURCES")
                                                showPlaylistPicker = false
                                                expandedPlaylistCategory = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 11.dp)
                                            .testTag("playlist_filter_all_sources")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text("Semua Sumber", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                "${drivePlaylists.sumOf { it.itemCount } + mediaList.count { it.sourceType == "CLOUDSTREAM" }}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .clickable { expandedPlaylistCategory = "DRIVE" }
                                            .padding(horizontal = 10.dp, vertical = 11.dp)
                                            .testTag("playlist_category_drive")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text("Drive", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.weight(1f))
                                            Text("${drivePlaylists.size}  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .clickable { expandedPlaylistCategory = "CLOUDSTREAM" }
                                            .padding(horizontal = 10.dp, vertical = 11.dp)
                                            .testTag("playlist_category_cloudstream")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Extension, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text("CloudStream", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.weight(1f))
                                            Text("${orderedCloudStreamPluginFilters.size}  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(if (selectedFilter == "SERVER") accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable {
                                                if (isServerUnlocked) {
                                                    viewModel.selectFilter("SERVER")
                                                } else {
                                                    pendingPlaylistFilter = "SERVER"
                                                    showServerPinDialog = true
                                                }
                                                showPlaylistPicker = false
                                                expandedPlaylistCategory = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 11.dp)
                                            .testTag("playlist_filter_all_servers")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(9.dp))
                                            Text("Semua Server", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.weight(1f))
                                            Text(if (isServerUnlocked) "Terbuka" else "PIN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                } else if (expandedPlaylistCategory == "DRIVE") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(if (selectedFilter == "DRIVE_ALL") accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable {
                                                viewModel.selectFilter("DRIVE_ALL")
                                                showPlaylistPicker = false
                                                expandedPlaylistCategory = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp)
                                            .testTag("playlist_filter_all_drive")
                                    ) {
                                        Text("Semua Drive", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (drivePlaylists.isEmpty()) {
                                        Text("Belum ada playlist Google Drive yang tersedia.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                                    } else {
                                        drivePlaylists.forEach { playlist ->
                                            val filter = "DRIVE_PLAYLIST:${playlist.id}"
                                            val isSelected = selectedFilter == filter
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                                    .clickable {
                                                        viewModel.selectFilter(filter)
                                                        showPlaylistPicker = false
                                                        expandedPlaylistCategory = null
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                                    .testTag("playlist_drive_${playlist.id}")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(9.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(playlist.name, color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${playlist.itemCount} media", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(if (selectedFilter == "CLOUDSTREAM_ALL") accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable {
                                                viewModel.selectFilter("CLOUDSTREAM_ALL")
                                                showPlaylistPicker = false
                                                expandedPlaylistCategory = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp)
                                            .testTag("playlist_filter_all_plugins")
                                    ) {
                                        Text("Semua Plugin", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (orderedCloudStreamPluginFilters.isEmpty()) {
                                        Text("Belum ada plugin CloudStream yang terpasang dan aktif.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                                    } else {
                                        orderedCloudStreamPluginFilters.forEach { (pluginId, pluginName) ->
                                            val filter = "CLOUDSTREAM_PLUGIN:$pluginId"
                                            val isSelected = selectedFilter == filter
                                            val itemCount = mediaList.count { it.sourceType == "CLOUDSTREAM" && it.pluginId == pluginId }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                                    .clickable {
                                                        viewModel.selectFilter(filter)
                                                        showPlaylistPicker = false
                                                        expandedPlaylistCategory = null
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                                    .testTag("playlist_cloudstream_plugin_$pluginId")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Extension, contentDescription = null, tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(9.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(pluginName, color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("$itemCount media", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "Ubah nama playlist melalui Manajemen Sumber → Plugin & Repo → Riwayat Playlist Sumber.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showQuickActions,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 220.dp, max = 250.dp)
                                    .liquidGlass(
                                        shape = RoundedCornerShape(20.dp),
                                        alpha = if (isDark) 0.96f else 0.98f,
                                        borderAlpha = if (isDark) 0.62f else 0.78f,
                                        elevation = 16.dp
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Daftar Playlist is separate from the source-management tabs inside the screen.
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                                        .clickable {
                                            showQuickActions = false
                                            onNavigateToExtension()
                                        }
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp))
                                        .padding(vertical = 12.dp)
                                        .testTag("quick_action_extension"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = "Daftar Playlist", tint = accentColor, modifier = Modifier.size(25.dp))
                                        Text("Daftar Playlist", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                                        .clickable {
                                            showQuickActions = false
                                            runCatching {
                                                embeddedModuleManager.start("dev.andikune.nsio.ddtv")
                                                showDdtvPrototypeDialog = true
                                            }.onFailure {
                                                ddtvLaunchError = it.localizedMessage ?: "DDTV Prototype tidak dapat dibuka."
                                            }
                                        }
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp))
                                        .padding(vertical = 12.dp)
                                        .testTag("quick_action_ddtv"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = accentColor, modifier = Modifier.size(25.dp))
                                        Text("DDTV", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                                        .clickable {
                                            showQuickActions = false
                                            onNavigateToMultiview()
                                        }
                                        .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp))
                                        .padding(vertical = 12.dp)
                                        .testTag("quick_action_multiview"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(Icons.Default.GridView, contentDescription = null, tint = accentColor, modifier = Modifier.size(25.dp))
                                        Text("Multiview", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF17364A).copy(alpha = 0.94f) else Color(0xFFE4F7FF))
                                .border(1.2.dp, Color(0xFF66D9FF).copy(alpha = 0.78f), CircleShape)
                                .clickable {
                                    showPlaylistPicker = false
                                    showQuickActions = !showQuickActions
                                }
                                .liquidGlassTvFocusable(shape = CircleShape)
                                .testTag("floating_quick_actions_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (showQuickActions) "Tutup pintasan" else "Buka pintasan",
                                tint = if (isDark) Color(0xFF9CEBFF) else Color(0xFF087EAD),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

    if (showCloudStreamOrderDialog) {
        val orderedIds = orderedCloudStreamPluginFilters.map { it.first }
        AlertDialog(
            onDismissRequest = { showCloudStreamOrderDialog = false },
            title = { Material3Text("Urutan plugin CloudStream") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    orderedCloudStreamPluginFilters.forEachIndexed { index, (pluginId, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Material3Text(
                                    text = name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val health = cloudStreamSourceHealth[pluginId]
                                val latestEvents = health?.history.orEmpty().takeLast(3).asReversed()
                                if (latestEvents.isNotEmpty()) {
                                    Material3Text(
                                        text = latestEvents.joinToString("  ·  ") { event ->
                                            val state = if (event.succeeded) "OK" else "Gagal"
                                            val duration = if (event.responseTimeMs > 0L) " ${event.responseTimeMs}ms" else ""
                                            "$state$duration"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    latestEvents.firstOrNull()?.takeIf { !it.succeeded && !it.message.isNullOrBlank() }?.let { event ->
                                        Material3Text(
                                            text = event.message.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else if (health?.lastError != null) {
                                    Material3Text(
                                        text = health.lastError.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.refreshCloudStreamPluginOnHome(pluginId) },
                                enabled = refreshingCloudStreamPluginId == null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (refreshingCloudStreamPluginId == pluginId) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Coba pulihkan $name")
                                }
                            }
                            IconButton(
                                onClick = { viewModel.moveCloudStreamPluginOrder(pluginId, -1, orderedIds) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Naikkan $name")
                            }
                            IconButton(
                                onClick = { viewModel.moveCloudStreamPluginOrder(pluginId, 1, orderedIds) },
                                enabled = index < orderedCloudStreamPluginFilters.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Turunkan $name")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCloudStreamOrderDialog = false }) {
                    Material3Text("Selesai")
                }
            }
        )
    }

    if (showDdtvPrototypeDialog) {
        val ddtvVisibleMedia = remember(mediaList, ddtvSourceFilter, drivePlaylistIds) {
            mediaList.filter { item ->
                when (ddtvSourceFilter) {
                    "DRIVE" -> MediaSourceClassifier.isDriveMedia(item, drivePlaylistIds)
                    "CLOUDSTREAM" -> item.sourceType.equals("CLOUDSTREAM", ignoreCase = true)
                    "FILE" -> item.sourceType.contains("LOCAL", ignoreCase = true) ||
                        item.sourceType.contains("FILE", ignoreCase = true)
                    "SERVER" -> false
                    else -> item.sourceType != "SERVER"
                }
            }.take(40)
        }
        val connectDdtvServer = {
            if (!ddtvServerLoading) {
                ddtvServerLoading = true
                ddtvServerStatus = "Menghubungkan ke server DDTV..."
                ddtvScope.launch {
                    try {
                        ddtvServerCategories = embeddedModuleManager.connectDdtvServer()
                        ddtvSelectedCategory = null
                        ddtvServerVideos = emptyList()
                        ddtvServerStatus = "Terhubung • ${ddtvServerCategories.size} kategori tersedia"
                    } catch (failure: Exception) {
                        ddtvServerStatus = "Gagal terhubung: ${failure.localizedMessage ?: failure.javaClass.simpleName}"
                    } finally {
                        ddtvServerLoading = false
                    }
                }
            }
        }
        val loadDdtvCategory: (DdtvCategory) -> Unit = { category ->
            if (!ddtvServerLoading) {
                ddtvServerLoading = true
                ddtvSelectedCategory = category
                ddtvServerVideos = emptyList()
                ddtvServerStatus = "Memuat kategori ${category.name}..."
                ddtvScope.launch {
                    try {
                        ddtvServerVideos = embeddedModuleManager.fetchDdtvPage(category, 1)
                        ddtvServerStatus = "${category.name} • ${ddtvServerVideos.size} video dimuat"
                    } catch (failure: Exception) {
                        ddtvServerStatus = "Gagal memuat kategori: ${failure.localizedMessage ?: failure.javaClass.simpleName}"
                    } finally {
                        ddtvServerLoading = false
                    }
                }
            }
        }
        val playDdtvVideo: (JSONObject) -> Unit = { video ->
            val category = ddtvSelectedCategory
            val videoId = video.optLong("id", video.optLong("video_id", 0L))
            if (category == null || videoId <= 0L) {
                ddtvLaunchError = "Identitas video DDTV tidak valid."
            } else if (!ddtvServerLoading) {
                ddtvServerLoading = true
                ddtvServerStatus = "Mengambil detail dan link streaming..."
                ddtvScope.launch {
                    try {
                        val videoType = video.optInt("video_type", category.videoType)
                        val detail = embeddedModuleManager.fetchDdtvDetail(videoId, videoType)
                        val stream = embeddedModuleManager.resolveDdtvStreams(video, detail).firstOrNull()
                            ?: throw IllegalStateException("Link streaming DDTV belum tersedia.")
                        val title = sequenceOf("title", "name", "video_name")
                            .map { video.optString(it, "").trim() }
                            .firstOrNull { it.isNotBlank() }
                            ?: "DDTV • Video $videoId"
                        val thumbnail = sequenceOf("cover_url", "cover", "thumbnail", "image")
                            .map { video.optString(it, "").trim() }
                            .firstOrNull { it.isNotBlank() }
                            .orEmpty()
                        viewModel.playDdtvMedia(
                            item = MediaItem(
                                id = "ddtv:$videoId:${System.currentTimeMillis()}",
                                profileId = activeProfile?.id ?: "ddtv",
                                title = title,
                                url = stream.url,
                                thumbnailUrl = thumbnail,
                                type = "VIDEO",
                                sourceType = "DDTV",
                                category = category.name
                            ),
                            headers = stream.headers,
                            streamType = stream.manifestType
                        )
                        showDdtvPrototypeDialog = false
                        embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
                    } catch (failure: Exception) {
                        ddtvLaunchError = failure.localizedMessage ?: "Gagal membuka video dari server DDTV."
                        ddtvServerStatus = "Gagal menyiapkan streaming."
                    } finally {
                        ddtvServerLoading = false
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = {
                showDdtvPrototypeDialog = false
                embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
            },
            title = { Material3Text("DDTV") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Material3Text("Embedded Module • v${ddtvModuleState?.metadata?.version ?: "0.2.0"}")
                    Material3Text(
                        "Adapter host: " + ddtvHostSources.joinToString(" • ") { it.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL" to "Semua", "DRIVE" to "Drive", "CLOUDSTREAM" to "CloudStream").forEach { (key, label) ->
                            TextButton(onClick = { ddtvSourceFilter = key }) {
                                Material3Text(if (ddtvSourceFilter == key) "• $label" else label)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { ddtvSourceFilter = "FILE" }) {
                            Material3Text(if (ddtvSourceFilter == "FILE") "• File" else "File")
                        }
                        TextButton(onClick = {
                            ddtvSourceFilter = "SERVER"
                            if (ddtvServerCategories.isEmpty()) connectDdtvServer()
                        }) {
                            Material3Text(if (ddtvSourceFilter == "SERVER") "• DDTV Server" else "DDTV Server")
                        }
                    }

                    if (ddtvSourceFilter == "SERVER") {
                        Material3Text(
                            "Server bawaan DDTV • protokol v1 • AES-CBC • verifikasi RSA-SHA256",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(enabled = !ddtvServerLoading, onClick = connectDdtvServer) {
                            Material3Text(if (ddtvServerLoading) "Menghubungkan..." else "Hubungkan / Muat Ulang Server DDTV")
                        }
                        Material3Text(
                            ddtvServerStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ddtvServerStatus.startsWith("Gagal")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ddtvServerCategories.isNotEmpty()) {
                            Material3Text("Kategori DDTV", style = MaterialTheme.typography.titleSmall)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ddtvServerCategories.forEach { category ->
                                    TextButton(
                                        enabled = !ddtvServerLoading,
                                        onClick = { loadDdtvCategory(category) }
                                    ) {
                                        Material3Text(
                                            if (ddtvSelectedCategory == category) "• ${category.name}" else category.name
                                        )
                                    }
                                }
                            }
                        }
                        ddtvSelectedCategory?.let { category ->
                            Material3Text("Video • ${category.name}", style = MaterialTheme.typography.titleSmall)
                            if (ddtvServerVideos.isEmpty() && !ddtvServerLoading) {
                                Material3Text("Pilih kategori atau muat ulang untuk mengambil daftar video.")
                            }
                            ddtvServerVideos.forEach { video ->
                                val videoId = video.optLong("id", video.optLong("video_id", 0L))
                                val title = sequenceOf("title", "name", "video_name")
                                    .map { video.optString(it, "").trim() }
                                    .firstOrNull { it.isNotBlank() }
                                    ?: "Video $videoId"
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .clickable(enabled = !ddtvServerLoading) { playDdtvVideo(video) }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Material3Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Material3Text("ID $videoId • Video Type ${video.optInt("video_type", category.videoType)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        TextButton(onClick = {
                            ddtvLocalFilePicker.launch(arrayOf("video/*", "audio/*"))
                        }) {
                            Material3Text("Pilih file dari perangkat…")
                        }
                        if (ddtvVisibleMedia.isEmpty()) {
                            Material3Text(
                                when (ddtvSourceFilter) {
                                    "DRIVE" -> "Belum ada media Google Drive di katalog. Tambahkan tautan Drive melalui Daftar Playlist → Sumber Link."
                                    "CLOUDSTREAM" -> "Belum ada konten CloudStream yang tersedia di katalog."
                                    "FILE" -> "Belum ada file lokal yang terdaftar di katalog NSiO."
                                    else -> "Katalog media masih kosong. Tambahkan sumber media terlebih dahulu."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ddtvVisibleMedia.forEach { mediaItem ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .clickable {
                                            val source = when {
                                                MediaSourceClassifier.isDriveMedia(mediaItem, drivePlaylistIds) -> ModuleMediaSource.DRIVE
                                                mediaItem.sourceType.equals("CLOUDSTREAM", true) -> ModuleMediaSource.CLOUD_STREAM
                                                mediaItem.sourceType.contains("FILE", true) ||
                                                    mediaItem.sourceType.contains("LOCAL", true) ||
                                                    mediaItem.url.startsWith("content://", true) -> ModuleMediaSource.FILE_MANAGER
                                                else -> null
                                            }
                                            if (source == null) {
                                                viewModel.playMedia(mediaItem)
                                                showDdtvPrototypeDialog = false
                                                embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
                                            } else {
                                                ddtvScope.launch {
                                                    when (val result = embeddedModuleManager.openMedia(
                                                        ModuleMediaRequest(source, mediaItem.url, mediaItem.title)
                                                    )) {
                                                        is ModuleMediaResult.Opened -> {
                                                            showDdtvPrototypeDialog = false
                                                            embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
                                                        }
                                                        is ModuleMediaResult.Unavailable -> ddtvLaunchError = result.reason
                                                        is ModuleMediaResult.Rejected -> ddtvLaunchError = result.reason
                                                    }
                                                }
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Material3Text(mediaItem.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Material3Text(
                                        mediaItem.sourceType.ifBlank { "Sumber media" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Material3Text(
                        "Status host: ${if (ddtvModuleState?.running == true) "Aktif" else "Berhenti"} • API ${ddtvModuleState?.metadata?.apiVersion ?: 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ddtvModuleState?.lastError?.let {
                        Material3Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        runCatching {
                            embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
                            embeddedModuleManager.start("dev.andikune.nsio.ddtv")
                        }.onFailure {
                            ddtvLaunchError = it.localizedMessage ?: "DDTV gagal dimulai ulang."
                            showDdtvPrototypeDialog = false
                        }
                    }) { Material3Text("Mulai Ulang") }
                    TextButton(onClick = {
                        showDdtvPrototypeDialog = false
                        embeddedModuleManager.stop("dev.andikune.nsio.ddtv")
                    }) { Material3Text("Tutup") }
                }
            }
        )
    }

    ddtvLaunchError?.let { message ->
        AlertDialog(
            onDismissRequest = { ddtvLaunchError = null },
            title = { Material3Text("DDTV Prototype") },
            text = { Material3Text(message) },
            confirmButton = { TextButton(onClick = { ddtvLaunchError = null }) { Material3Text("Tutup") } }
        )
    }

    if (showServerPinDialog) {
        PinDialog(
            title = "Akses Server",
            subtitle = "Masukkan PIN server untuk menampilkan media server",
            expectedPin = settings.serverPin,
            onSuccess = {
                viewModel.unlockServerAccess()
                viewModel.selectFilter(pendingPlaylistFilter ?: "SERVER")
                pendingPlaylistFilter = null
                showServerPinDialog = false
            },
            onDismiss = { pendingPlaylistFilter = null; showServerPinDialog = false }
        )
    }
}
