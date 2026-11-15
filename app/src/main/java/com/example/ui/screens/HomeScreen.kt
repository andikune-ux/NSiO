package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.AuthState
import com.example.data.model.MediaSourceClassifier
import com.example.data.model.WatchHistoryPolicy
import com.example.ui.MainViewModel
import com.example.ui.components.MediaGridCard
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToExtension: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
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
    var expandedPlaylistCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCloudStreamOrderDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistFilter by remember { mutableStateOf<String?>(null) }
    var expandedSourceType by rememberSaveable { mutableStateOf<String?>(null) }
    val expandedSearchFocusRequester = remember { FocusRequester() }

    // Intercept back button to close the floating curtain if open
    BackHandler(enabled = isSearchExpanded || showPlaylistPicker || showCloudStreamOrderDialog || expandedSourceType != null || expandedPlaylistCategory != null) {
        when {
            showCloudStreamOrderDialog -> showCloudStreamOrderDialog = false
            expandedPlaylistCategory != null -> expandedPlaylistCategory = null
            showPlaylistPicker -> showPlaylistPicker = false
            isSearchExpanded -> isSearchExpanded = false
            else -> expandedSourceType = null
        }
    }

    LaunchedEffect(activeProfile?.id) {
        if (activeProfile != null) {
            viewModel.refreshCloudStreamCatalogOnHome()
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

    val filteredItems = mediaList.filter { item ->
        val matchesServerAccess = item.sourceType != "SERVER" || isServerUnlocked
        val matchesSearch = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
        val matchesExpandedSource = expandedSourceType == null || item.sourceType.equals(expandedSourceType, ignoreCase = true)
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
        matchesServerAccess && matchesSearch && matchesFilter && matchesExpandedSource
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
                                    text = "Buka menu Extension untuk menambahkan link streaming, file lokal, atau upload server.",
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
                                        "Buka Menu Extension",
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
                                        if (sourceType == "CLOUDSTREAM") {
                                            IconButton(
                                                onClick = { viewModel.loadMoreCloudStreamCatalogOnHome() },
                                                enabled = !isLoadingMoreCloudStreamHome,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                if (isLoadingMoreCloudStreamHome) {
                                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudSync,
                                                        contentDescription = "Muat halaman CloudStream berikutnya",
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
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                sectionItems.forEach { mediaItem ->
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
                    renderContent = false,
                    handlePendingDetail = true
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
                                                enabled = searchQuery.isNotBlank() && cloudStreamPluginFilters.isNotEmpty(),
                                                onClick = {
                                                    cloudStreamSearchRequest++
                                                    isSearchExpanded = false
                                                },
                                                modifier = Modifier.testTag("home_cloudstream_search_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "Cari juga di CloudStream",
                                                    tint = if (searchQuery.isNotBlank() && cloudStreamPluginFilters.isNotEmpty()) accentColor
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

                // Floating playlist selector, kept above the grid and away from player controls.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSearchExpanded,
                    enter = fadeIn(animationSpec = tween(160)),
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(end = 8.dp, bottom = if (isPhoneLayout) 72.dp else 12.dp)
                    ) {
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
                                    "Ubah nama playlist melalui Extension → Plugin & Repo → Riwayat Playlist Sumber.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { showPlaylistPicker = !showPlaylistPicker }
                                .liquidGlassTvFocusable(shape = RoundedCornerShape(16.dp))
                                .drawBehind {
                                    // Trace the Material Extension glyph itself so the purple
                                    // outline follows its puzzle-piece silhouette, not a circle.
                                    val glyph = Path().apply {
                                        moveTo(20.5f, 11f)
                                        lineTo(19f, 11f)
                                        lineTo(19f, 7f)
                                        cubicTo(19f, 5.9f, 18.1f, 5f, 17f, 5f)
                                        lineTo(13f, 5f)
                                        lineTo(13f, 3.5f)
                                        cubicTo(13f, 2.12f, 11.88f, 1f, 10.5f, 1f)
                                        cubicTo(9.12f, 1f, 8f, 2.12f, 8f, 3.5f)
                                        lineTo(8f, 5f)
                                        lineTo(4f, 5f)
                                        cubicTo(2.9f, 5f, 2f, 5.9f, 2f, 7f)
                                        lineTo(2f, 10.8f)
                                        lineTo(3.5f, 10.8f)
                                        cubicTo(4.88f, 10.8f, 6f, 11.92f, 6f, 13.3f)
                                        cubicTo(6f, 14.68f, 4.88f, 15.8f, 3.5f, 15.8f)
                                        lineTo(2f, 15.8f)
                                        lineTo(2f, 20f)
                                        cubicTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
                                        lineTo(8f, 22f)
                                        lineTo(8f, 20.5f)
                                        cubicTo(8f, 19.12f, 9.12f, 18f, 10.5f, 18f)
                                        cubicTo(11.88f, 18f, 13f, 19.12f, 13f, 20.5f)
                                        lineTo(13f, 22f)
                                        lineTo(17f, 22f)
                                        cubicTo(18.1f, 22f, 19f, 21.1f, 19f, 20f)
                                        lineTo(19f, 16f)
                                        lineTo(20.5f, 16f)
                                        cubicTo(21.88f, 16f, 23f, 14.88f, 23f, 13.5f)
                                        cubicTo(23f, 12.12f, 21.88f, 11f, 20.5f, 11f)
                                        close()
                                    }
                                    val glyphSize = 28.dp.toPx()
                                    val scaleFactor = glyphSize / 24f
                                    val left = (size.width - glyphSize) / 2f
                                    val top = (size.height - glyphSize) / 2f
                                    withTransform({
                                        translate(left, top)
                                        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
                                    }) {
                                        drawPath(
                                            path = glyph,
                                            color = accentColor,
                                            style = Stroke(width = 1.35.dp.toPx())
                                        )
                                    }
                                }
                                .testTag("floating_playlist_extension_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = "Buka pilihan playlist",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
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
