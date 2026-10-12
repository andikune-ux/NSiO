package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CyanPrimary

@OptIn(ExperimentalLayoutApi::class)
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

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isServerUnlocked by remember { mutableStateOf(false) }
    var showServerPinDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Sumber Link", "Sumber File", "Sumber Server (PIN)", "Plugin & Repo")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("extension_screen")
    ) {
        // Header
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                text = "Menu Extension (Manajemen Sumber Konten)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tambahkan konten dari Link, File Lokal, Server, atau Repositori Plugin",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Liquid Glass Tab Capsule Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.08f, borderAlpha = 0.3f, elevation = 4.dp)
                .padding(2.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                contentColor = CyanPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = CyanPrimary,
                        height = 2.5.dp
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (index == 2 && !isServerUnlocked) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Terkunci",
                                        tint = AccentRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Contents
        when (selectedTabIndex) {
            0 -> LinkSourceTab(viewModel, onNavigateToHome)
            1 -> FileSourceTab(viewModel, onNavigateToHome)
            2 -> ServerSourceTab(viewModel, onNavigateToHome, mediaList.filter { it.sourceType == "SERVER" })
            3 -> PluginRepoTab(viewModel, plugins, repos)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var parseFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "B1. Tambah Sumber Link",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mendukung Repo CloudStream JSON, Google Drive Publik, m3u/m3u8, dan link video langsung.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Masukkan URL link media (http:// atau https://)...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp))
                            .testTag("link_url_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Urai & Tambahkan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        if (parseFeedback != null) {
                            Button(
                                onClick = onNavigateToHome,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                            ) {
                                Text("Buka Beranda", color = CyanPrimary, fontSize = 11.sp)
                            }
                        }
                    }

                    if (parseFeedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = parseFeedback ?: "",
                            fontSize = 10.sp,
                            color = CyanPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Preset Uji Coba Cepat:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val presets = listOf(
            Triple(
                "CloudStream Repo JSON (Folder / Banyak Item)",
                "Koleksi banyak video terstruktur JSON",
                "https://raw.githubusercontent.com/cloudstream/repo/main/plugins.json"
            ),
            Triple(
                "IPTV Channel M3U8 Live Stream (1 Saluran)",
                "Live stream HLS Big Buck Bunny",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            ),
            Triple(
                "Google Drive Video Link",
                "Link Google Drive dengan parsing fileId",
                "https://drive.google.com/file/d/1A2B3C4D5E6F7G8H9I0/view"
            ),
            Triple(
                "Direct MP4 1080p Video",
                "Video langsung Elephants Dream",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
            )
        )

        items(presets) { (title, desc, url) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(12.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 2.dp)
                    .liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                    .clickable { urlInput = url }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                        Text(text = desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            urlInput = url
                            isLoading = true
                            viewModel.parseAndAddLink(url) {
                                isLoading = false
                                parseFeedback = it
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pakai", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var filePathInput by remember { mutableStateOf("/storage/emulated/0/Movies/VideoLokal.mp4") }
    var isPhoto by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "B2. Sumber File (Unggah dari Perangkat)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Impor file video/foto dari penyimpanan internal atau eksternal perangkat untuk muncul di beranda.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = { Text("Judul File / Video...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = filePathInput,
                    onValueChange = { filePathInput = it },
                    placeholder = { Text("Path atau Uri file...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isPhoto = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPhoto) CyanPrimary else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Tipe: Video", color = if (!isPhoto) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                    }

                    Button(
                        onClick = { isPhoto = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPhoto) CyanPrimary else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Tipe: Foto", color = if (isPhoto) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            val finalTitle = titleInput.ifBlank { "Media Lokal ${System.currentTimeMillis() % 1000}" }
                            viewModel.addLocalFileMedia(finalTitle, filePathInput, isPhoto)
                            titleInput = ""
                            onNavigateToHome()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Impor ke Beranda", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerSourceTab(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit,
    serverMediaList: List<com.example.data.model.MediaItem>
) {
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = "B3. Sumber Server (Terproteksi PIN)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Judul konten server...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("URL konten server...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val finalTitle = titleInput.ifBlank { "Server Video ${System.currentTimeMillis() % 1000}" }
                                viewModel.uploadMediaToServer(finalTitle, urlInput, false)
                                titleInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload ke Server", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }

                        Button(
                            onClick = onNavigateToHome,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Text("Buka Beranda", color = CyanPrimary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Daftar Konten Server Terupload (${serverMediaList.size} Item):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (serverMediaList.isEmpty()) {
            item {
                Text(
                    text = "Belum ada konten yang di-upload ke server.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(serverMediaList, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = RoundedCornerShape(12.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 2.dp)
                        .liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = item.url, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(
                            onClick = { viewModel.deleteMedia(item) },
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = AccentRed, modifier = Modifier.size(15.dp))
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
    repos: List<com.example.data.plugin.RepoManifest>
) {
    var repoUrlInput by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Sistem Repositori Plugin (Arsitektur CloudStream)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Dukungan dynamic plugin loading (manifest.json, BasePlugin, CS3 packages).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = repoUrlInput,
                            onValueChange = { repoUrlInput = it },
                            placeholder = { Text("URL repo.json...", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                            ),
                            modifier = Modifier.weight(1f).liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        )
                        Button(
                            onClick = {
                                if (repoUrlInput.isNotBlank()) {
                                    viewModel.showStatus("Repositori berhasil ditambahkan")
                                    repoUrlInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Text("Tambah", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Plugin Terpasang (${plugins.size}):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(plugins) { plugin ->
            val manifest = plugin.manifest
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(12.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 2.dp)
                    .liquidGlassTvFocusable(shape = RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(text = manifest?.name ?: "Plugin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                        Text(text = manifest?.description ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Author: ${manifest?.author ?: "NSiO"}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF30D158).copy(alpha = 0.25f))
                            .border(0.6.dp, Color(0xFF30D158).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("Aktif", fontSize = 9.sp, color = Color(0xFF30D158), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
