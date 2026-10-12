package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.CyanSecondary

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
            .padding(14.dp)
            .testTag("home_screen")
    ) {
        val containerWidth = maxWidth
        val isNarrow = containerWidth < 620.dp
        val columns = when {
            containerWidth < 460.dp -> 2
            containerWidth < 750.dp -> 3
            containerWidth < 1050.dp -> 4
            else -> settings.gridColumns.coerceIn(3, 7)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar with Liquid Glass search & action pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Net Stream iO",
                            fontSize = if (isNarrow) 18.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .liquidGlass(
                                    shape = RoundedCornerShape(12.dp),
                                    alpha = 0.15f,
                                    borderAlpha = 0.35f,
                                    elevation = 2.dp
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${filteredList.size} media",
                                fontSize = 11.sp,
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Smart Multi-Source Streaming Hub",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Header Action Pills: Quick Rotate & Cloud Sync
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Orientation Toggle Button
                    Box(
                        modifier = Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = 0.12f,
                                borderAlpha = 0.3f,
                                elevation = 4.dp
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.toggleDisplayMode() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("home_rotate_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Ubah Orientasi",
                                tint = CyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = settings.displayMode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Cloud Sync Pill (Firebase status)
                    Box(
                        modifier = Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = 0.14f,
                                borderAlpha = 0.35f,
                                elevation = 4.dp
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (authState is AuthState.Authenticated) {
                                    viewModel.syncToCloud()
                                } else {
                                    viewModel.signInWithGoogle()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("home_cloud_status_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (authState is AuthState.Authenticated) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = "Cloud Status",
                                    tint = if (authState is AuthState.Authenticated) CyanPrimary else Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
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

            // Liquid Glass Search Bar Pill (spacious and clean)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Cari judul video, foto, atau kategori...",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Hapus teks",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(24.dp),
                            alpha = 0.08f,
                            borderAlpha = 0.28f,
                            elevation = 4.dp
                        )
                        .testTag("home_search_field")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontally Scrollable Liquid Glass Filter Chips
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
                                shape = RoundedCornerShape(20.dp),
                                alpha = if (isSelected) 0.25f else 0.08f,
                                borderAlpha = if (isSelected) 0.65f else 0.25f,
                                elevation = if (isSelected) 6.dp else 2.dp
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.selectFilter(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("filter_chip_$key")
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content Grid
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
                                shape = RoundedCornerShape(28.dp),
                                alpha = 0.15f,
                                borderAlpha = 0.35f,
                                elevation = 12.dp
                            )
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Tidak ada hasil untuk '$searchQuery'" else "Belum Ada Konten",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Buka menu Extension untuk menambahkan link m3u8, video CloudStream, file lokal, atau upload server.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(320.dp),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onNavigateToExtension,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .liquidGlass(
                                        shape = RoundedCornerShape(20.dp),
                                        alpha = 0.2f,
                                        elevation = 8.dp
                                    )
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
                    contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
        }
    }
}
