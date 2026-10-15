package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.AuthState
import com.example.ui.MainViewModel
import com.example.ui.components.MediaGridCard
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
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isSyncing by viewModel.isCloudSyncing.collectAsState()

    // 2-stage interaction: Normal compact mode vs Floating Curtain Overlay
    var isSearchExpanded by remember { mutableStateOf(false) }
    val expandedSearchFocusRequester = remember { FocusRequester() }

    // Intercept back button to close the floating curtain if open
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            try {
                expandedSearchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
    val accentColor = if (isDark) CyanPrimary else Color(0xFF0284C7)

    val filteredList = mediaList.filter { item ->
        val matchesSearch = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "LINK" -> item.sourceType == "LINK"
            "FILE" -> item.sourceType == "FILE"
            "SERVER" -> item.sourceType == "SERVER"
            "FAV" -> item.isFavorite
            else -> true
        }
        matchesSearch && matchesFilter
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("home_screen")
    ) {
        val containerWidth = maxWidth
        val isNarrow = containerWidth < 600.dp
        val columns = when {
            containerWidth < 440.dp -> 2
            containerWidth < 720.dp -> 3
            containerWidth < 1020.dp -> 4
            else -> settings.gridColumns.coerceIn(3, 7)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // BAGIAN PERTAMA: HEADER PERMANEN RAMPING (Compact Permanent Top Bar)
            // Nama Aplikasi + Search Bar di Tengah + Tombol TV & Cloud di Kanan
            // Filter disembunyikan agar hemat tempat & grid video jauh lebih luas
            // ==========================================
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
                        text = "Net Stream iO",
                        fontSize = if (isNarrow) 16.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Box(
                        modifier = Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(10.dp),
                                alpha = if (isDark) 0.15f else 0.70f,
                                borderAlpha = 0.35f,
                                elevation = 2.dp
                            )
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
                        .weight(1f, fill = false)
                        .widthIn(min = 120.dp, max = 360.dp)
                        .height(36.dp)
                        .padding(horizontal = 6.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            alpha = if (searchQuery.isNotEmpty()) (if (isDark) 0.22f else 0.95f) else (if (isDark) 0.10f else 0.85f),
                            borderAlpha = if (searchQuery.isNotEmpty()) 0.65f else 0.30f,
                            elevation = 3.dp
                        )
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
                            .liquidGlass(
                                shape = RoundedCornerShape(18.dp),
                                alpha = if (isDark) 0.12f else 0.85f,
                                borderAlpha = 0.3f,
                                elevation = 3.dp
                            )
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
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Tombol Cloud Sync Status (Firebase)
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(18.dp),
                                alpha = if (isDark) 0.14f else 0.85f,
                                borderAlpha = 0.35f,
                                elevation = 3.dp
                            )
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

            // ==========================================
            // KONTEN UTAMA: Grid Video & Overlay Tirai Mengambang
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // LAPISAN 1: GRID VIDEO / EMPTY STATE (Permanen di bawah Header)
                if (filteredList.isEmpty()) {
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
                                        contentColor = if (isDark) Color.Black else Color.White
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
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            MediaGridCard(
                                item = item,
                                onClick = { viewModel.playMedia(item) },
                                onLongClick = { viewModel.toggleFavorite(item) }
                            )
                        }
                    }
                }

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

                            // 2. Baris Filter Chips Mengambang (Semua, Link Ekstensi, File Lokal, Server, Favorit)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filters = listOf(
                                    "ALL" to "Semua",
                                    "LINK" to "Link Ekstensi",
                                    "FILE" to "File Lokal",
                                    "SERVER" to "Server Upload",
                                    "FAV" to "Favorit"
                                )

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
                                                viewModel.selectFilter(key)
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
            }
        }
    }
}
