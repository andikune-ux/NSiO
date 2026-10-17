package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.data.model.SourcePlaylist
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CyanPrimary

@Composable
fun ExtensionScreen(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val plugins by viewModel.pluginManager.installedPlugins.collectAsState()
    val repos by viewModel.pluginManager.repositories.collectAsState()
    val sourcePlaylists by viewModel.sourcePlaylists.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isServerUnlocked by remember { mutableStateOf(false) }
    var showServerPinDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Sumber Link", "Sumber File", "Sumber Server (PIN)", "Plugin & Repo")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("extension_screen")
    ) {
        // Header Title
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                text = "Menu Extension",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pusat Manajemen Sumber Konten Video, Foto & Ekstensi CloudStream",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // iOS 26 Liquid Glass Segmented Pill Tab Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    alpha = 0.12f,
                    borderAlpha = 0.35f,
                    elevation = 6.dp
                )
                .padding(4.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 6.dp,
                containerColor = Color.Transparent,
                contentColor = CyanPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = CyanPrimary,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            if (index == 2 && !isServerUnlocked) {
                                showServerPinDialog = true
                            } else {
                                selectedTabIndex = index
                            }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (index == 2) {
                                    Icon(
                                        imageVector = if (isServerUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = "Status Kunci",
                                        tint = if (isServerUnlocked) CyanPrimary else AccentRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Contents
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> LinkSourceTab(viewModel, onNavigateToHome)
                1 -> FileSourceTab(viewModel, onNavigateToHome)
                2 -> ServerSourceTab(viewModel, onNavigateToHome, mediaList.filter { it.sourceType == "SERVER" })
                3 -> PluginRepoTab(viewModel, plugins, repos, sourcePlaylists)
            }
        }
    }

    if (showServerPinDialog) {
        PinDialog(
            title = "Keamanan Sumber Server",
            subtitle = "Masukkan PIN untuk mengakses menu manajemen server",
            expectedPin = settings.serverPin,
            onSuccess = {
                isServerUnlocked = true
                showServerPinDialog = false
                selectedTabIndex = 2
            },
            onDismiss = {
                showServerPinDialog = false
            }
        )
    }
}

@Composable
private fun LinkSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var parseFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(22.dp),
                        alpha = 0.12f,
                        borderAlpha = 0.35f,
                        elevation = 8.dp
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "B1. Tambah Sumber Link",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mendukung Repo CloudStream JSON, Google Drive Publik, m3u/m3u8, dan direct video/audio URL.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = {
                            Text(
                                "Tempelkan link URL media (http:// atau https://)...",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_url_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    isLoading = true
                                    viewModel.parseAndAddLink(urlInput) { message ->
                                        isLoading = false
                                        parseFeedback = message
                                        urlInput = ""
                                    }
                                }
                            },
                            enabled = !isLoading && urlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_parse_link")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                "Urai & Tambahkan Link",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (parseFeedback != null) {
                            Button(
                                onClick = onNavigateToHome,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Beranda", color = CyanPrimary, fontSize = 13.sp)
                            }
                        }
                    }

                    if (parseFeedback != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = parseFeedback ?: "",
                            fontSize = 11.sp,
                            color = CyanPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun FileSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var filePathInput by remember { mutableStateOf("") }
    var isPhoto by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(22.dp),
                        alpha = 0.12f,
                        borderAlpha = 0.35f,
                        elevation = 8.dp
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "B2. Sumber File (Unggah dari Perangkat)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Impor file video/foto dari penyimpanan internal atau eksternal perangkat untuk muncul di beranda.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Judul File / Video...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = filePathInput,
                        onValueChange = { filePathInput = it },
                        placeholder = { Text("Path atau Uri file perangkat...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Type selection pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { isPhoto = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPhoto) CyanPrimary else Color.White.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(
                                "Tipe: Video",
                                color = if (!isPhoto) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (!isPhoto) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { isPhoto = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPhoto) CyanPrimary else Color.White.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(
                                "Tipe: Foto",
                                color = if (isPhoto) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isPhoto) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val finalTitle = titleInput.ifBlank { "Media Lokal ${System.currentTimeMillis() % 1000}" }
                            viewModel.addLocalFileMedia(finalTitle, filePathInput, isPhoto)
                            titleInput = ""
                            onNavigateToHome()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Impor ke Beranda",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit,
    serverMediaList: List<com.example.data.model.MediaItem>
) {
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(22.dp),
                        alpha = 0.12f,
                        borderAlpha = 0.35f,
                        elevation = 8.dp
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "B3. Sumber Server (Terproteksi PIN)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Judul konten server...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("URL konten server...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val finalTitle = titleInput.ifBlank { "Server Video ${System.currentTimeMillis() % 1000}" }
                                viewModel.uploadMediaToServer(finalTitle, urlInput, false)
                                titleInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Tambahkan URL Server",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = onNavigateToHome,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Buka Beranda", color = CyanPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Daftar Konten Server Terupload (${serverMediaList.size} Item):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (serverMediaList.isEmpty()) {
            item {
                Text(
                    text = "Belum ada konten yang di-upload ke server.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(serverMediaList, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(18.dp),
                            alpha = 0.08f,
                            borderAlpha = 0.25f,
                            elevation = 3.dp
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.url,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteMedia(item) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus",
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRepoTab(
    viewModel: MainViewModel,
    plugins: List<com.example.data.plugin.Plugin>,
    repos: List<com.example.data.plugin.RepoManifest>,
    sourcePlaylists: List<SourcePlaylist>
) {
    var repoUrlInput by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<SourcePlaylist?>(null) }
    var editedName by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Riwayat Playlist Sumber (${sourcePlaylists.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (sourcePlaylists.isEmpty()) {
            item { Text("Belum ada link sumber yang dimasukkan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(sourcePlaylists, key = { it.id }) { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().liquidGlass(
                        shape = RoundedCornerShape(16.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 3.dp
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                        Text("${playlist.itemCount} item • ${playlist.url}", fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editingPlaylist = playlist; editedName = playlist.name }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename playlist", tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.deleteSourcePlaylist(playlist) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus playlist", tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(22.dp),
                        alpha = 0.12f,
                        borderAlpha = 0.35f,
                        elevation = 8.dp
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Arsitektur Plugin CloudStream",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tambah URL repository JSON untuk memuat plugin scraper streaming eksternal.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        placeholder = { Text("https://repo.domain.com/plugins.json", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (repoUrlInput.isNotBlank()) {
                                val url = repoUrlInput
                                repoUrlInput = ""
                                scope.launch {
                                    viewModel.pluginManager.addRepository(name = "Koleksi Eksternal", url = url)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            "Tambahkan Repositori",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Repository Aktif (${repos.size}):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(repos, key = { it.pluginLists.joinToString("|") }) { repo ->
            Row(
                modifier = Modifier.fillMaxWidth().liquidGlass(
                    shape = RoundedCornerShape(16.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 3.dp
                ).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(repo.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                    Text(repo.pluginLists.firstOrNull().orEmpty(), fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { viewModel.refreshRepository(repo) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                    Text("Refresh", fontSize = 11.sp)
                }
            }
        }

        item {
            Text(
                text = "Plugin Terpasang (${plugins.size}):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(plugins) { plugin ->
            val pName = plugin.manifest?.name ?: plugin.filename ?: "Plugin Ekstensi"
            val pDesc = plugin.manifest?.description ?: "Plugin streaming terpasang"
            val pVersion = plugin.manifest?.version ?: 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        alpha = 0.08f,
                        borderAlpha = 0.25f,
                        elevation = 3.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$pDesc • v$pVersion",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.pluginManager.unloadPlugin(plugin) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Copot",
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
    editingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(value = editedName, onValueChange = { editedName = it }, singleLine = true, label = { Text("Nama playlist") })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameSourcePlaylist(playlist, editedName); editingPlaylist = null }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editingPlaylist = null }) { Text("Batal") } }
        )
    }
}
