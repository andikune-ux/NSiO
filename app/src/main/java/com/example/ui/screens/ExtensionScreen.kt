package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.data.model.SourcePlaylist
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentRed

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

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val isServerUnlocked by viewModel.isServerUnlocked.collectAsState()
    var showServerPinDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Sumber Link", "Sumber File", "Sumber Server (PIN)", "Plugin & Repo")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("extension_screen")
    ) {
        // Header Title
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "Menu Extension",
                fontSize = 18.sp,
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
                    shape = RoundedCornerShape(18.dp),
                    alpha = 0.12f,
                    borderAlpha = 0.22f,
                    elevation = 3.dp
                )
                .padding(3.dp)
        ) {
            ScrollableTabRow(
                modifier = Modifier.focusGroup(),
                selectedTabIndex = selectedTabIndex,
                edgePadding = 3.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 2.dp
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                if (index == 2) {
                                    Icon(
                                        imageVector = if (isServerUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = "Status Kunci",
                                        tint = if (isServerUnlocked) MaterialTheme.colorScheme.primary else AccentRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                viewModel.unlockServerAccess()
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
    var playlistNameInput by remember { mutableStateOf("") }
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_url_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it.take(48) },
                        label = { Text("Nama playlist (opsional)") },
                        placeholder = { Text("Kosongkan untuk memakai nama otomatis", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("link_playlist_name_input")
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
                                    viewModel.parseAndAddLink(urlInput, playlistNameInput) { message ->
                                        isLoading = false
                                        parseFeedback = message
                                        if (!message.startsWith("Error:")) {
                                            urlInput = ""
                                            playlistNameInput = ""
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading && urlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_parse_link")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                "Urai & Tambahkan Link",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (parseFeedback != null) {
                            Button(
                                onClick = onNavigateToHome,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Beranda", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            }
                        }
                    }

                    if (parseFeedback != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = parseFeedback ?: "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
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
    val isImportingLocalFile by viewModel.isImportingLocalFile.collectAsState()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        filePathInput = uri?.toString().orEmpty()
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            isPhoto = context.contentResolver.getType(uri)?.startsWith("image/") == true
        }
    }

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
                        placeholder = { Text("Judul File / Video...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = filePathInput,
                        onValueChange = { filePathInput = it },
                        placeholder = { Text("Path atau Uri file perangkat...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { filePicker.launch(arrayOf(if (isPhoto) "image/*" else "video/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (filePathInput.isBlank()) "Pilih file dari perangkat"
                            else "File dipilih: ${filePathInput.substringAfterLast('/').take(28)}",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Type selection pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { isPhoto = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(
                                "Tipe: Video",
                                color = if (!isPhoto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (!isPhoto) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { isPhoto = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(
                                "Tipe: Foto",
                                color = if (isPhoto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isPhoto) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (filePathInput.isBlank()) {
                                viewModel.showStatus("Pilih file terlebih dahulu")
                            } else {
                                val finalTitle = titleInput.ifBlank { "Media Lokal ${System.currentTimeMillis() % 1000}" }
                                viewModel.addLocalFileMedia(finalTitle, filePathInput, isPhoto) { success ->
                                    if (success) {
                                        titleInput = ""
                                        filePathInput = ""
                                        onNavigateToHome()
                                    }
                                }
                            }
                        },
                        enabled = filePathInput.isNotBlank() && !isImportingLocalFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (isImportingLocalFile) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mengimpor...", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text("Impor ke Beranda", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileIsPhoto by remember { mutableStateOf(false) }
    val isUploadingServerMedia by viewModel.isUploadingServerMedia.collectAsState()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFileUri = uri
        selectedFileIsPhoto = uri?.let { context.contentResolver.getType(it)?.startsWith("image/") == true } == true
    }

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
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
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

                    Text(
                        text = "Pilih file dari perangkat. File disimpan privat ke Firebase Storage milik aplikasi dan otomatis dicatat di beranda. Login Google tidak diperlukan; aplikasi memakai sesi anonim khusus perangkat.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Judul konten server...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { filePicker.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        enabled = !isUploadingServerMedia,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedFileUri?.lastPathSegment ?: "Pilih file video dari perangkat", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { filePicker.launch("image/*") },
                        enabled = !isUploadingServerMedia,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih file foto dari perangkat", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }

                    if (selectedFileUri != null) {
                        Button(
                            onClick = {
                                viewModel.uploadMediaFileToServer(titleInput, selectedFileUri.toString(), selectedFileIsPhoto) { success ->
                                    if (success) {
                                        titleInput = ""
                                        selectedFileUri = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(18.dp),
                            enabled = !isUploadingServerMedia,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isUploadingServerMedia) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mengunggah...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Text("Upload File ke Server", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Button(
                        onClick = onNavigateToHome,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Buka Beranda", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
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
    var repoFeedback by remember { mutableStateOf<String?>(null) }
    var isAddingRepository by remember { mutableStateOf(false) }
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
                    ).clickable { viewModel.selectFilter("PLAYLIST:${playlist.id}") }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${playlist.itemCount} item • ${playlist.url}", fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editingPlaylist = playlist; editedName = playlist.name }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename playlist", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                        text = "Repository Eksternal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kelola repository CloudStream, pasang plugin .cs3, lalu jalankan provider untuk mencari konten dan mengambil link streaming.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it.take(2048) },
                        placeholder = { Text("https://repo.domain.com/plugins.json", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    repoFeedback?.let { message ->
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            color = if (message.startsWith("Repository berhasil")) MaterialTheme.colorScheme.primary else AccentRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val url = repoUrlInput.trim()
                            if (url.isBlank()) {
                                repoFeedback = "Masukkan URL repository terlebih dahulu."
                            } else if (!isAddingRepository) {
                                isAddingRepository = true
                                scope.launch {
                                    runCatching {
                                        viewModel.pluginManager.addRepository(name = "Koleksi Eksternal", url = url)
                                    }.onSuccess {
                                        repoUrlInput = ""
                                        repoFeedback = "Repository berhasil disimpan."
                                    }.onFailure { error ->
                                        repoFeedback = error.message?.takeIf { it.isNotBlank() }
                                            ?: "Repository gagal disimpan. Periksa URL dan koneksi internet."
                                    }
                                    isAddingRepository = false
                                }
                            }
                        },
                        enabled = !isAddingRepository,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isAddingRepository) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (isAddingRepository) "Menyimpan Repository..." else "Tambahkan Repositori",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Repository Tersimpan (${repos.size}):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (repos.isEmpty()) {
            item {
                Text(
                    "Belum ada repository tersimpan. Tambahkan URL katalog yang kamu percaya untuk mulai mengelolanya.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(repos, key = { it.pluginLists.firstOrNull() ?: "${it.name}:${it.description}" }) { repo ->
            Row(
                modifier = Modifier.fillMaxWidth().liquidGlass(
                    shape = RoundedCornerShape(16.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 3.dp
                ).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(repo.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${repo.plugins.size} plugin • ${repo.sourceUrl.ifBlank { repo.pluginLists.firstOrNull().orEmpty() }}", fontSize = 10.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { viewModel.refreshRepository(repo) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                    Text("Refresh", fontSize = 11.sp)
                }
            }
        }
        }

        repos.filter { it.plugins.isNotEmpty() }.forEach { repo ->
            item(key = "cloudstream-title-${repo.sourceUrl}") {
                Text(
                    text = "Katalog ${repo.name} • ${repo.plugins.size} plugin",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(repo.plugins, key = { "cloudstream-${repo.sourceUrl}-${it.internalName}" }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().liquidGlass(
                        shape = RoundedCornerShape(14.dp), alpha = 0.07f, borderAlpha = 0.2f, elevation = 2.dp
                    ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var artifactInstalled by remember(entry.internalName, entry.version) {
                        mutableStateOf(viewModel.pluginManager.isCloudStreamPluginInstalled(entry))
                    }
                    var artifactBusy by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                    var runtimeActive by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                    var runtimeProviderCount by remember(entry.internalName, entry.version) { mutableIntStateOf(0) }
                    var artifactMessage by remember(entry.internalName, entry.version) { mutableStateOf("") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "${entry.internalName} • v${entry.version} • ${entry.language.ifBlank { "bahasa tidak ditentukan" }}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.description.isNotBlank()) {
                            Text(entry.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text(
                            when {
                                entry.status == 0 -> "Status: nonaktif"
                                runtimeActive -> "Runtime aktif • $runtimeProviderCount provider"
                                artifactInstalled -> "File .cs3 tersimpan • belum dijalankan"
                                else -> "Metadata tersedia • belum dipasang"
                            },
                            fontSize = 9.sp,
                            color = if (entry.status == 0) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (artifactMessage.isNotBlank()) {
                            Text(artifactMessage, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = {
                            if (!artifactBusy && !runtimeActive && entry.status != 0) {
                                artifactBusy = true
                                scope.launch {
                                    try {
                                        if (!artifactInstalled) {
                                            viewModel.pluginManager.installCloudStreamPlugin(entry)
                                            artifactInstalled = true
                                        }
                                        val providers = viewModel.pluginManager.loadCloudStreamPlugin(entry)
                                        runtimeProviderCount = providers.size
                                        runtimeActive = true
                                        artifactMessage = if (providers.isEmpty()) {
                                            "Runtime aktif. Plugin tidak mendaftarkan provider MainAPI."
                                        } else {
                                            "Runtime aktif • ${providers.size} provider siap dicari."
                                        }
                                    } catch (error: Throwable) {
                                        artifactMessage = if (artifactInstalled) {
                                            "File tersimpan, aktivasi gagal: ${error.message ?: "kesalahan runtime"}"
                                        } else {
                                            error.message ?: "Gagal memasang file plugin."
                                        }
                                    } finally {
                                        artifactBusy = false
                                    }
                                }
                            }
                        },
                        enabled = !artifactBusy && !runtimeActive && entry.status != 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (artifactBusy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Text(
                            when {
                                runtimeActive -> "Aktif"
                                artifactInstalled -> "Aktifkan"
                                else -> "Pasang & aktifkan"
                            },
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        item { CloudStreamSearchPanel(viewModel, repos) }


        item {
            Text(
                text = "Plugin Tercatat (${plugins.size}):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Bagian ini berisi metadata plugin internal NSiO. Provider CloudStream dikelola melalui katalog dan pencarian di atas.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (plugins.isEmpty()) {
            item {
                Text(
                    "Belum ada plugin internal NSiO yang tercatat.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(plugins, key = { plugin ->
            plugin.manifest?.let { "${it.name}|${it.url}|${it.version}" }
                ?: plugin.filename.ifBlank { "plugin-${plugins.indexOf(plugin)}" }
        }) { plugin ->
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
                            color = MaterialTheme.colorScheme.primary
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
    }
    editingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(value = editedName, onValueChange = { editedName = it.take(48) }, singleLine = true, label = { Text("Nama playlist") }, supportingText = { Text("${editedName.length}/48") })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameSourcePlaylist(playlist, editedName.trim()); editingPlaylist = null }, enabled = editedName.isNotBlank() && editedName.trim() != playlist.name) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editingPlaylist = null }) { Text("Batal") } }
        )
    }
}


@Composable
private fun CloudStreamSearchPanel(
    viewModel: MainViewModel,
    repos: List<com.example.data.plugin.RepoManifest>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("") }
    var results by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamSearchItem>>>(emptyList())
    }
    var selectedContent by remember {
        mutableStateOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamContent>?>(null)
    }
    var loadingDetail by remember { mutableStateOf(false) }
    var resolvingLinks by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf("") }
    var linkTarget by remember { mutableStateOf("") }
    var streams by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamStream>>>(emptyList())
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val installedEntries = repos.flatMap { it.plugins }
        .filter { it.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(it) }
        .distinctBy { it.internalName to it.version }

    fun resolveLinks(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        providerName: String,
        data: String,
        target: String
    ) {
        resolvingLinks = true
        linkTarget = target
        linkMessage = ""
        streams = emptyList()
        scope.launch {
            runCatching {
                viewModel.pluginManager.loadCloudStreamLinks(entry, providerName, data)
            }.onSuccess { found ->
                streams = found.map { entry to it }
                linkMessage = if (found.isEmpty()) {
                    "Provider belum menemukan link yang dapat diputar."
                } else {
                    "Ditemukan ${found.size} link."
                }
            }.onFailure { error ->
                linkMessage = error.message ?: "Gagal mengambil link streaming."
            }
            resolvingLinks = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().liquidGlass(
            shape = RoundedCornerShape(22.dp), alpha = 0.10f, borderAlpha = 0.28f, elevation = 5.dp
        ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = "Cari konten CloudStream",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Pencarian hanya memakai plugin .cs3 yang sudah terpasang. Menekan Cari akan memuat kode plugin bila belum aktif. Plugin pihak ketiga berjalan dengan izin aplikasi—gunakan repository yang kamu percaya.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(180) },
            placeholder = { Text("Judul film atau serial", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                searching = true
                results = emptyList()
                selectedContent = null
                streams = emptyList()
                searchMessage = ""
                scope.launch {
                    val found = mutableListOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamSearchItem>>()
                    val failures = mutableListOf<String>()
                    installedEntries.forEach { entry ->
                        runCatching {
                            viewModel.pluginManager.searchCloudStreamPlugin(entry, query.trim())
                        }.onSuccess { items ->
                            items.forEach { found.add(entry to it) }
                        }.onFailure { error ->
                            failures.add("${entry.name}: ${error.message ?: "gagal"}")
                        }
                    }
                    results = found
                    searchMessage = when {
                        found.isNotEmpty() && failures.isNotEmpty() ->
                            "Ditemukan ${found.size} hasil. ${failures.size} plugin gagal merespons."
                        found.isNotEmpty() -> "Ditemukan ${found.size} hasil."
                        installedEntries.isEmpty() -> "Belum ada plugin .cs3 terpasang."
                        failures.isNotEmpty() -> failures.take(2).joinToString(" • ")
                        else -> "Tidak ada hasil untuk kata kunci ini."
                    }
                    searching = false
                }
            },
            enabled = !searching && query.isNotBlank() && installedEntries.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (searching) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            else Text("Cari di ${installedEntries.size} plugin terpasang", fontSize = 11.sp)
        }
        if (searchMessage.isNotBlank()) {
            Text(searchMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.forEach { (entry, result) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${result.providerName} • ${result.contentType ?: entry.name}${result.year?.let { " • $it" } ?: ""}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    enabled = !loadingDetail,
                    onClick = {
                        loadingDetail = true
                        streams = emptyList()
                        scope.launch {
                            runCatching {
                                viewModel.pluginManager.loadCloudStreamContent(entry, result.providerName, result.url)
                            }.onSuccess { content ->
                                selectedContent = entry to content
                                linkMessage = ""
                            }.onFailure { error ->
                                searchMessage = error.message ?: "Gagal memuat detail konten."
                            }
                            loadingDetail = false
                        }
                    }
                ) { Text("Detail", fontSize = 10.sp) }
            }
        }
        if (loadingDetail) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        selectedContent?.let { (entry, content) ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(content.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                listOfNotNull(content.year?.toString(), content.contentType, content.durationMinutes?.let { "$it menit" }).joinToString(" • "),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content.plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5)
            }
            content.dataUrl?.takeIf { it.isNotBlank() }?.let { data ->
                Button(
                    onClick = { resolveLinks(entry, content.providerName, data, "Film") },
                    enabled = !resolvingLinks,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                ) { Text("Ambil link film", fontSize = 10.sp) }
            }
            content.episodes.forEachIndexed { index, episode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        episode.name.ifBlank { "Episode ${index + 1}" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = !resolvingLinks,
                        onClick = {
                            resolveLinks(
                                entry,
                                content.providerName,
                                episode.data,
                                episode.name.ifBlank { "Episode ${index + 1}" }
                            )
                        }
                    ) { Text("Link", fontSize = 10.sp) }
                }
            }
        }
        if (resolvingLinks) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (linkMessage.isNotBlank()) {
            Text("$linkTarget • $linkMessage", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        streams.forEach { (_, stream) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stream.name.ifBlank { stream.source }, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${stream.quality.takeIf { it > 0 }?.let { "${it}p • " } ?: ""}${stream.streamType}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        val headers = stream.headers.toMutableMap()
                        if (stream.referer.isNotBlank() && headers.keys.none { it.equals("Referer", ignoreCase = true) }) {
                            headers["Referer"] = stream.referer
                        }
                        val mimeType = when (stream.streamType.uppercase()) {
                            "M3U8" -> "application/x-mpegURL"
                            "DASH" -> "application/dash+xml"
                            else -> null
                        }
                        viewModel.playExternalStream(
                            url = stream.url,
                            title = stream.name.ifBlank { selectedContent?.second?.title ?: "CloudStream" },
                            requestHeaders = headers,
                            mimeType = mimeType,
                            sourceUrl = stream.referer
                        )
                    }
                ) { Text("Putar", fontSize = 10.sp) }
            }
        }
    }
}
