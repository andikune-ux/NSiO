package com.example.ui.screens

import androidx.compose.animation.core.animateFloat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.Headers
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
    val pendingCloudStreamDetail by viewModel.pendingCloudStreamDetail.collectAsState()
    var showServerPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingCloudStreamDetail?.id) {
        if (pendingCloudStreamDetail != null) selectedTabIndex = 3
    }

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
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRepositoryDialog by remember { mutableStateOf(false) }
    var showPluginDialog by remember { mutableStateOf(false) }
    var selectedPluginCategory by rememberSaveable { mutableStateOf("Semua") }
    var expandedPluginDialog by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val selectedPluginId = selectedPluginFilter
        .takeIf { it.startsWith("CLOUDSTREAM_PLUGIN:") }
        ?.removePrefix("CLOUDSTREAM_PLUGIN:")
    val installedEntries = repos.flatMap { it.plugins }
        .filter { it.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(it) }
        .filter { selectedPluginId != null && viewModel.pluginManager.cloudStreamPluginId(it) == selectedPluginId }
        .distinctBy { it.internalName to it.version }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Semua", "Movie", "Seri TV", "Anime", "Drama Asia", "Dubb Indo", "Kartun", "NSFW").forEach { category ->
                    val active = selectedPluginCategory == category
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                            .clickable { selectedPluginCategory = category }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(category, fontSize = 10.sp, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showPlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Buka daftar playlist (" + sourcePlaylists.size + ")", fontSize = 11.sp) }
                Button(onClick = { showRepositoryDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Buka daftar repository (" + repos.size + ")", fontSize = 11.sp) }
                Button(onClick = { showPluginDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Buka daftar plugin (" + (repos.sumOf { it.plugins.size } + plugins.size) + ")", fontSize = 11.sp) }
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

    }
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Daftar Playlist (" + sourcePlaylists.size + ")") },
            text = {
                if (sourcePlaylists.isEmpty()) Text("Belum ada playlist tersimpan.")
                else LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sourcePlaylists, key = { it.id }) { playlist ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f).clickable { viewModel.selectFilter("PLAYLIST:" + playlist.id); showPlaylistDialog = false }) {
                                Text(playlist.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(playlist.itemCount.toString() + " item • " + playlist.url, fontSize = 9.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingPlaylist = playlist; editedName = playlist.name }) { Icon(Icons.Default.Edit, contentDescription = "Ubah nama") }
                            IconButton(onClick = { viewModel.deleteSourcePlaylist(playlist) }) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = AccentRed) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Tutup") } }
        )
    }
    if (showRepositoryDialog) {
        fun matchesRepositoryPlugin(entry: com.example.data.plugin.CloudStreamPluginEntry): Boolean {
            val types = entry.tvTypes.joinToString(" ").lowercase()
            val language = entry.language.lowercase()
            return when (selectedPluginCategory) {
                "Semua" -> true
                "Movie" -> types.contains("movie") || types.contains("film")
                "Seri TV" -> types.contains("tv") || types.contains("series")
                "Anime" -> types.contains("anime")
                "Drama Asia" -> types.contains("asian") || types.contains("asia") || types.contains("drama")
                "Dubb Indo" -> language == "id" || language.contains("indonesia") || language.contains("indonesian")
                "Kartun" -> types.contains("cartoon") || types.contains("animation")
                "NSFW" -> types.contains("nsfw") || types.contains("adult") || types.contains("18+")
                else -> true
            }
        }
        val filteredRepos = repos.filter { repo -> selectedPluginCategory == "Semua" || repo.plugins.any(::matchesRepositoryPlugin) }
        AlertDialog(
            onDismissRequest = { showRepositoryDialog = false },
            title = { Text("Daftar Repository (${filteredRepos.size}/${repos.size})") },
            text = {
                if (filteredRepos.isEmpty()) Text("Tidak ada repository dengan kategori ${selectedPluginCategory}.")
                else LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredRepos, key = { it.sourceUrl.ifBlank { it.name } }) { repo ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(repo.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(repo.plugins.size.toString() + " plugin • " + repo.sourceUrl, fontSize = 9.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.refreshRepository(repo) }) { Text("Refresh", fontSize = 10.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRepositoryDialog = false }) { Text("Tutup") } }
        )
    }
    if (showPluginDialog) {
        val allEntries = repos.flatMap { it.plugins }.distinctBy { it.internalName to it.version }
        fun matchesPluginCategory(entry: com.example.data.plugin.CloudStreamPluginEntry): Boolean {
            val types = entry.tvTypes.joinToString(" ").lowercase()
            val language = entry.language.lowercase()
            return when (selectedPluginCategory) {
                "Semua" -> true
                "Movie" -> types.contains("movie") || types.contains("film")
                "Seri TV" -> types.contains("tv") || types.contains("series")
                "Anime" -> types.contains("anime")
                "Drama Asia" -> types.contains("asian") || types.contains("asia") || types.contains("drama")
                "Dubb Indo" -> language == "id" || language.contains("indonesia") || language.contains("indonesian")
                "Kartun" -> types.contains("cartoon") || types.contains("animation")
                "NSFW" -> types.contains("nsfw") || types.contains("adult") || types.contains("18+")
                else -> true
            }
        }
        val entries = allEntries.filter(::matchesPluginCategory)
        Dialog(
            onDismissRequest = { showPluginDialog = false; expandedPluginDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = if (expandedPluginDialog) Modifier.fillMaxSize() else Modifier.fillMaxWidth(0.94f).height(590.dp),
                shape = if (expandedPluginDialog) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daftar Plugin (${entries.size}/${allEntries.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Filter berdasarkan jenis konten dan bahasa plugin.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { expandedPluginDialog = !expandedPluginDialog }) {
                            Text(if (expandedPluginDialog) "Perkecil" else "Perbesar", fontSize = 11.sp)
                        }
                        TextButton(onClick = { showPluginDialog = false; expandedPluginDialog = false }) { Text("Tutup") }
                    }
                    if (entries.isEmpty() && plugins.isEmpty()) {
                        Text("Belum ada plugin.")
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(entries, key = { it.internalName + "-" + it.version }) { entry ->
                                var installed by remember(entry.internalName, entry.version) { mutableStateOf(viewModel.pluginManager.isCloudStreamPluginInstalled(entry)) }
                                var busy by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                                var confirmUninstall by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                                Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                                    Text(entry.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(entry.internalName + " • v" + entry.version + " • API " + entry.apiVersion + " • " + viewModel.pluginManager.cloudStreamRepositoryName(entry) + " • " + if (installed) "terpasang" else "belum dipasang", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                if (!busy && entry.status != 0) {
                                                    busy = true
                                                    scope.launch {
                                                        runCatching {
                                                            if (!installed) viewModel.pluginManager.installCloudStreamPlugin(entry)
                                                            viewModel.pluginManager.loadCloudStreamPlugin(entry)
                                                        }.onSuccess {
                                                            installed = true
                                                            viewModel.selectFilter("CLOUDSTREAM_PLUGIN:" + viewModel.pluginManager.cloudStreamPluginId(entry))
                                                            repoFeedback = "Plugin ${entry.name} aktif dan dipilih untuk Extension Home."
                                                        }.onFailure { repoFeedback = it.message ?: "Gagal memasang plugin." }
                                                        busy = false
                                                    }
                                                }
                                            },
                                            enabled = !busy && entry.status != 0,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            else Text(if (installed) "Aktifkan & pilih" else "Pasang & aktifkan", fontSize = 10.sp)
                                        }
                                        if (installed) IconButton(onClick = { confirmUninstall = true }, enabled = !busy) {
                                            Icon(Icons.Default.Delete, contentDescription = "Copot plugin", tint = AccentRed)
                                        }
                                    }
                                    if (confirmUninstall) {
                                        AlertDialog(
                                            onDismissRequest = { confirmUninstall = false },
                                            title = { Text("Copot plugin?") },
                                            text = { Text("File plugin ${entry.name} versi ${entry.version} dan cache runtime-nya akan dihapus dari perangkat.") },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    confirmUninstall = false
                                                    busy = true
                                                    scope.launch {
                                                        runCatching { viewModel.pluginManager.uninstallCloudStreamPlugin(entry) }
                                                            .onSuccess { installed = false; repoFeedback = "Plugin ${entry.name} berhasil dicopot." }
                                                            .onFailure { repoFeedback = it.message ?: "Gagal mencopot plugin." }
                                                        busy = false
                                                    }
                                                }) { Text("Copot") }
                                            },
                                            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Batal") } }
                                        )
                                    }
                                }
                            }
                            items(plugins, key = { it.manifest?.name ?: it.filename }) { plugin ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(plugin.manifest?.name ?: plugin.filename.ifBlank { "Plugin internal NSiO" }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(plugin.manifest?.description ?: "Plugin internal NSiO", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.pluginManager.unloadPlugin(plugin) }) { Icon(Icons.Default.Delete, contentDescription = "Copot", tint = AccentRed) }
                                }
                            }
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
private fun CloudStreamDetailSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cloudstream_detail_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.28f, targetValue = 0.62f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "skeleton_alpha"
    )
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(72.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
            }
            Box(Modifier.fillMaxWidth().height(138.dp).clip(RoundedCornerShape(14.dp)).background(placeholder))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(92.dp).height(132.dp).clip(RoundedCornerShape(10.dp)).background(placeholder))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(0.9f).height(17.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
                    Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
                    Box(Modifier.fillMaxWidth(0.75f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
                    Box(Modifier.fillMaxWidth(0.48f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
                }
            }
            repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(placeholder))
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(placeholder))
            } }
            Box(Modifier.fillMaxWidth(0.35f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
            repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) 0.68f else 1f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(placeholder)) }
            Box(Modifier.fillMaxWidth(0.28f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(placeholder))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(4) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(9.dp)).background(placeholder))
                    Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(5.dp)).background(placeholder))
                } }
            }
        }
    }
}

@Composable
fun CloudStreamSearchPanel(
    viewModel: MainViewModel,
    repos: List<com.example.data.plugin.RepoManifest>,
    query: String,
    searchRequest: Int,
    selectedPluginFilter: String = "ALL",
    selectedVideoFilters: Set<String> = emptySet(),
    renderContent: Boolean = true,
    handlePendingDetail: Boolean = true,
    enableSearch: Boolean = true
) {
    val trailerContext = LocalContext.current
    val cloudStreamHistoryItems by viewModel.mediaList.collectAsState()
    val activeCloudStreamProfile by viewModel.activeProfile.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("") }
    var searchNextPages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loadingMoreSearch by remember { mutableStateOf(false) }
    var results by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamSearchItem>>>(emptyList())
    }
    var selectedContent by remember {
        mutableStateOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamContent>?>(null)
    }
    var loadingDetail by remember { mutableStateOf(false) }
    var detailLoadError by remember { mutableStateOf<String?>(null) }
    var detailRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resolvingLinks by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf("") }
    var linkTarget by remember { mutableStateOf("") }
    var resolvedPlaybackData by remember { mutableStateOf("") }
    var resolvedPlaybackTitle by remember { mutableStateOf("") }
    var resolvedPlaybackPoster by remember { mutableStateOf<String?>(null) }
    var resolvedPlaybackDurationMs by remember { mutableStateOf(0L) }
    var resolvedPlaybackContentUrl by remember { mutableStateOf("") }
    var resolvedPlaybackProvider by remember { mutableStateOf("") }
    var resolvedPlaybackEntry by remember { mutableStateOf<com.example.data.plugin.CloudStreamPluginEntry?>(null) }
    var resolvedPlaybackSkipMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var streams by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamStream>>>(emptyList())
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pendingCloudStreamDetail by viewModel.pendingCloudStreamDetail.collectAsState()
    val cloudStreamDetailRequestId by viewModel.cloudStreamDetailRequestId.collectAsState()
    LaunchedEffect(cloudStreamDetailRequestId, handlePendingDetail) {
        if (!handlePendingDetail) return@LaunchedEffect
        val pendingItem = pendingCloudStreamDetail ?: return@LaunchedEffect
        loadingDetail = true
        selectedContent = null
        detailLoadError = null
        streams = emptyList()
        linkMessage = ""
        android.util.Log.i("NSiO.CloudStreamDetail", "Handling request=" + cloudStreamDetailRequestId + " item=" + pendingItem.id + " plugin=" + pendingItem.pluginId)
        detailRetry = { viewModel.requestCloudStreamDetail(pendingItem) }
        runCatching {
            val entry = viewModel.pluginManager.findCloudStreamPlugin(pendingItem.pluginId)
                ?: throw IllegalStateException("Plugin asal konten tidak ditemukan/terpasang. ID tersimpan: '" + pendingItem.pluginId + "'.")
            val metadata = runCatching { org.json.JSONObject(pendingItem.extraInfo) }.getOrNull()
            val providerName = metadata?.optString("providerName")
                ?.takeIf { it.isNotBlank() } ?: pendingItem.extraInfo
            val content = viewModel.pluginManager.loadCloudStreamContent(entry, providerName, pendingItem.url)
            entry to content.copy(
                posterUrl = content.posterUrl?.takeIf { it.isNotBlank() } ?: pendingItem.thumbnailUrl,
                title = content.title.ifBlank { pendingItem.title }
            )
        }.onSuccess { (entry, content) ->
            android.util.Log.i("NSiO.CloudStreamDetail", "Detail loaded: " + content.title + " via " + entry.name)
            selectedContent = entry to content
            detailLoadError = null
            streams = emptyList()
            linkMessage = ""
            searchMessage = "Detail CloudStream siap. Pilih film atau episode untuk mengambil sumber pemutaran."
        }.onFailure { error ->
            android.util.Log.e("NSiO.CloudStreamDetail", "Detail request failed for " + pendingItem.id, error)
            val message = "Gagal membuka detail CloudStream: ${error.localizedMessage ?: "provider tidak merespons"}"
            detailLoadError = message
            searchMessage = message
        }
        viewModel.clearPendingCloudStreamDetail(pendingItem.id)
        loadingDetail = false
    }

    fun openCloudStreamDetail(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        providerName: String,
        contentUrl: String,
        fallbackPoster: String? = null,
        fallbackYear: Int? = null,
        fallbackTitle: String? = null
    ) {
        if (loadingDetail) return
        loadingDetail = true
        selectedContent = null
        detailLoadError = null
        detailRetry = { openCloudStreamDetail(entry, providerName, contentUrl, fallbackPoster, fallbackYear, fallbackTitle) }
        streams = emptyList()
        scope.launch {
            runCatching {
                viewModel.pluginManager.loadCloudStreamContent(entry, providerName, contentUrl)
            }.onSuccess { loaded ->
                selectedContent = entry to loaded.copy(
                    posterUrl = loaded.posterUrl?.takeIf { it.isNotBlank() } ?: fallbackPoster,
                    year = loaded.year ?: fallbackYear,
                    title = loaded.title.ifBlank { fallbackTitle.orEmpty() }
                )
                detailLoadError = null
                linkMessage = ""
            }.onFailure { error ->
                val message = "Gagal membuka detail konten: ${error.localizedMessage ?: "provider tidak merespons"}"
                detailLoadError = message
                searchMessage = message
            }
            loadingDetail = false
        }
    }

    fun resolveLinks(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        providerName: String,
        data: String,
        target: String,
        contentUrl: String = selectedContent?.second?.url.orEmpty(),
        posterUrl: String? = selectedContent?.second?.posterUrl,
        durationMs: Long = if (target == "Film") (selectedContent?.second?.durationMinutes?.toLong() ?: 0L) * 60_000L else 0L,
        autoPlay: Boolean = false,
        skipMetadata: Map<String, String> = selectedContent?.second?.externalIds.orEmpty()
    ) {
        resolvingLinks = true
        linkTarget = target
        resolvedPlaybackEntry = entry
        resolvedPlaybackProvider = providerName
        resolvedPlaybackData = data
        resolvedPlaybackTitle = if (target == "Film") selectedContent?.second?.title ?: target
            else listOf(selectedContent?.second?.title, target).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
        resolvedPlaybackPoster = posterUrl
        resolvedPlaybackDurationMs = durationMs.coerceAtLeast(0L)
        resolvedPlaybackSkipMetadata = skipMetadata.filterValues { it.isNotBlank() }
        resolvedPlaybackContentUrl = contentUrl
        linkMessage = ""
        streams = emptyList()
        if (autoPlay) {
            viewModel.playCloudStreamFilmImmediately(
                entry = entry,
                contentUrl = contentUrl,
                providerName = providerName,
                playbackData = data,
                title = resolvedPlaybackTitle,
                posterUrl = posterUrl,
                durationMs = durationMs,
                skipMetadata = resolvedPlaybackSkipMetadata
            )
            resolvingLinks = false
            return
        }
        scope.launch {
            runCatching {
                viewModel.pluginManager.loadCloudStreamLinks(entry, providerName, data)
            }.onSuccess { found ->
                val playable = found.filter { it.url.isNotBlank() }
                streams = playable.map { entry to it }
                linkMessage = if (playable.isEmpty()) {
                    "Provider belum menemukan link yang dapat diputar."
                } else {
                    "Ditemukan ${playable.size} link."
                }
                if (autoPlay) {
                    val selectedStream = playable.maxByOrNull { it.quality }
                    if (selectedStream == null) {
                        linkMessage = "Tidak ada sumber film yang bisa diputar."
                        viewModel.cancelCloudStreamPlaybackPreparation(linkMessage)
                    } else {
                        val headers = selectedStream.headers.toMutableMap().apply {
                            if (selectedStream.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) {
                                put("Referer", selectedStream.referer)
                            }
                        }
                        val streamType = selectedStream.streamType.substringAfterLast('.').uppercase()
                        val extension = runCatching {
                            java.net.URI(selectedStream.url).path.substringAfterLast('.', "").uppercase()
                        }.getOrDefault("")
                        val mimeType = when {
                            streamType.contains("M3U8") || streamType.contains("HLS") || extension == "M3U8" -> "application/x-mpegURL"
                            streamType.contains("DASH") || streamType == "MPD" || extension == "MPD" -> "application/dash+xml"
                            streamType.contains("WEBM") || extension == "WEBM" -> "video/webm"
                            streamType.contains("MKV") || streamType.contains("MATROSKA") || extension == "MKV" -> "video/x-matroska"
                            streamType.contains("MP4") || streamType.contains("VIDEO") || extension == "MP4" -> "video/mp4"
                            else -> null
                        }
                        viewModel.playCloudStreamEpisode(
                            entry = entry,
                            contentUrl = contentUrl,
                            providerName = providerName,
                            playbackData = data,
                            title = resolvedPlaybackTitle,
                            posterUrl = posterUrl,
                            durationMs = durationMs,
                            streamUrl = selectedStream.url,
                            requestHeaders = headers,
                            mimeType = mimeType,
                            externalSubtitles = selectedStream.subtitles,
                            extractorData = selectedStream.extractorData,
                            videoInterceptor = selectedStream.videoInterceptor,
                            availablePluginStreams = playable,
                            category = "CloudStream • Film",
                            skipMetadata = resolvedPlaybackSkipMetadata
                        )
                        linkMessage = "Memutar film melalui ${selectedStream.name.ifBlank { selectedStream.source }}."
                    }
                }
            }.onFailure { error ->
                linkMessage = error.message ?: "Gagal mengambil link streaming."
                if (autoPlay) viewModel.cancelCloudStreamPlaybackPreparation(linkMessage)
            }
            resolvingLinks = false
        }
    }

    if (renderContent) {
        LaunchedEffect(selectedPluginFilter) {
            results = emptyList()
            searchNextPages = emptyMap()
            searchMessage = if (selectedPluginFilter.startsWith("CLOUDSTREAM_PLUGIN:")) "" else
                "Pilih satu plugin CloudStream pada filter Home untuk mencari."
        }
        LaunchedEffect(searchRequest, enableSearch, selectedPluginFilter) {
            if (!enableSearch || searchRequest <= 0 || query.isBlank()) return@LaunchedEffect
            searching = true
            results = emptyList()
            searchNextPages = emptyMap()
            selectedContent = null
            streams = emptyList()
            searchMessage = ""
            val found = mutableListOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamSearchItem>>()
            val nextPages = mutableMapOf<String, Int>()
            val failures = mutableListOf<String>()
            installedEntries.forEach { entry ->
                runCatching {
                    // searchCloudStreamPlugin obtains the installed artifact and the runtime
                    // loads/activates it before invoking the provider search method.
                    viewModel.pluginManager.searchCloudStreamPlugin(entry, query.trim(), 1)
                }.onSuccess { page ->
                    page.items.forEach { found.add(entry to it) }
                    if (page.hasNext) nextPages[viewModel.pluginManager.cloudStreamPluginId(entry)] = 2
                }.onFailure { error ->
                    failures.add("${entry.name}: ${error.message ?: "gagal"}")
                }
            }
            results = found
            searchNextPages = nextPages
            searchMessage = when {
                found.isNotEmpty() && failures.isNotEmpty() ->
                    "Ditemukan ${found.size} hasil. ${failures.size} plugin gagal merespons."
                found.isNotEmpty() -> "Ditemukan ${found.size} hasil dari pencarian CloudStream."
                installedEntries.isEmpty() -> "Pilih satu plugin CloudStream pada filter Home untuk mencari hanya dari plugin tersebut."
                failures.isNotEmpty() -> failures.take(2).joinToString(" • ")
                else -> "Tidak ada hasil CloudStream untuk kata kunci ini."
            }
            searching = false
        }
        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Mencari di plugin terpilih...", fontSize = 10.sp)
            }
        }
        if (searchMessage.isNotBlank()) {
            Text(searchMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (searchNextPages.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    loadingMoreSearch = true
                    scope.launch {
                        val appended = mutableListOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamSearchItem>>()
                        val updatedPages = searchNextPages.toMutableMap()
                        val failures = mutableListOf<String>()
                        installedEntries.filter { viewModel.pluginManager.cloudStreamPluginId(it) in searchNextPages }.forEach { entry ->
                            val key = viewModel.pluginManager.cloudStreamPluginId(entry)
                            val pageNumber = searchNextPages[key] ?: return@forEach
                            runCatching {
                                viewModel.pluginManager.searchCloudStreamPlugin(entry, query.trim(), pageNumber)
                            }.onSuccess { page ->
                                page.items.forEach { appended.add(entry to it) }
                                if (page.hasNext) updatedPages[key] = pageNumber + 1 else updatedPages.remove(key)
                            }.onFailure { error ->
                                failures.add("${entry.name}: ${error.message ?: "gagal"}")
                            }
                        }
                        results = results + appended
                        searchNextPages = updatedPages
                        searchMessage = "Ditemukan ${results.size} hasil." + if (failures.isNotEmpty()) " Gagal memuat: ${failures.take(2).joinToString(" • ")}" else ""
                        loadingMoreSearch = false
                    }
                },
                enabled = !loadingMoreSearch && !searching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loadingMoreSearch) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                else Text("Muat hasil pencarian lainnya", fontSize = 11.sp)
            }
        }
        results.filter { (entry, result) ->
            selectedVideoFilters.all { category ->
                val metadata = "${result.contentType.orEmpty()} ${result.providerName} ${result.pluginName} ${result.title} ${entry.language}".lowercase()
                when (category) {
                    "Movie" -> metadata.contains("movie") || metadata.contains("film")
                    "Seri TV" -> metadata.contains("tv") || metadata.contains("series") || metadata.contains("serial")
                    "Anime" -> metadata.contains("anime")
                    "Drama Asia" -> metadata.contains("asian") || metadata.contains("asia") || metadata.contains("k-drama") || metadata.contains("cdrama") || metadata.contains("j-drama")
                    "Dubb Indo" -> metadata.contains("dubb indo") || metadata.contains("dub indo") || metadata.contains("indonesia") || metadata.contains("indonesian")
                    "Kartun" -> metadata.contains("cartoon") || metadata.contains("animation") || metadata.contains("kartun")
                    "NSFW" -> metadata.contains("nsfw") || metadata.contains("adult") || metadata.contains("18+")
                    else -> true
                }
            }
        }.forEach { (entry, result) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!result.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.posterUrl,
                        contentDescription = "Buka detail ${result.title}",
                        modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(7.dp)).clickable {
                            openCloudStreamDetail(entry, result.providerName, result.url, result.posterUrl, result.year, result.title)
                        }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${result.providerName} • ${result.contentType ?: entry.name}${result.year?.let { " • $it" } ?: ""}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        buildString {
                            append("Plugin: ")
                            append(entry.name)
                            val repositoryName = viewModel.pluginManager.cloudStreamRepositoryName(entry)
                            if (repositoryName.isNotBlank()) {
                                append(" • Repo: ")
                                append(repositoryName)
                            }
                        },
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    enabled = !loadingDetail,
                    onClick = { openCloudStreamDetail(entry, result.providerName, result.url, result.posterUrl, result.year, result.title) }
                ) { Text("Detail", fontSize = 10.sp) }
            }
        }
        if (loadingDetail) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

        if ((loadingDetail && selectedContent == null) || detailLoadError != null) {
            Dialog(onDismissRequest = {
                loadingDetail = false
                detailLoadError = null
                selectedContent = null
                streams = emptyList()
            }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                if (detailLoadError == null) {
                    CloudStreamDetailSkeleton()
                } else {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Konten belum dapat dimuat", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(detailLoadError.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { detailRetry?.invoke() }, enabled = !loadingDetail, modifier = Modifier.fillMaxWidth()) {
                                if (loadingDetail) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Coba Lagi")
                            }
                            TextButton(onClick = { detailLoadError = null; selectedContent = null }) { Text("Kembali") }
                        }
                    }
                }
            }
        }

        selectedContent?.let { (entry, content) ->
            Dialog(
                onDismissRequest = {
                    selectedContent = null
                    streams = emptyList()
                    linkMessage = ""
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    selectedContent = null
                                    streams = emptyList()
                                    linkMessage = ""
                                }
                            ) { Text("‹ Kembali", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Detail konten", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(entry.name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val contentImageHeaders = remember(content.url, content.posterHeaders) {
                Headers.Builder().apply {
                    content.posterHeaders.forEach { (key, value) -> add(key, value) }
                }.build()
            }
            fun contentImageRequest(url: String?): ImageRequest? = url?.takeIf { it.isNotBlank() }?.let {
                ImageRequest.Builder(trailerContext).data(it).headers(contentImageHeaders).build()
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (!content.backgroundPosterUrl.isNullOrBlank()) AsyncImage(model = contentImageRequest(content.backgroundPosterUrl), contentDescription = "Backdrop", modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(14.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (!content.posterUrl.isNullOrBlank()) AsyncImage(model = contentImageRequest(content.posterUrl), contentDescription = "Poster", modifier = Modifier.width(84.dp).height(122.dp).clip(RoundedCornerShape(9.dp)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(content.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(listOfNotNull(content.year?.toString(), content.contentType, content.durationMinutes?.let { "$it menit" }, content.contentRating).joinToString(" • "), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    content.rating?.takeIf { it > 0 }?.let { Text("Rating " + String.format("%.1f", it / 1000f) + "/10", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                    content.showStatus?.takeIf { it.isNotBlank() }?.let { Text("Status: $it", fontSize = 9.sp) }
                    if (content.comingSoon) Text("Segera tayang", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            content.tags.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), fontSize = 9.sp, color = MaterialTheme.colorScheme.primary) }
            listOfNotNull(
                content.englishTitle?.takeIf { it.isNotBlank() }?.let { "English: $it" },
                content.japaneseTitle?.takeIf { it.isNotBlank() }?.let { "Japanese: $it" },
                content.synonyms.takeIf { it.isNotEmpty() }?.let { "Judul lain: ${it.joinToString(", ")}" }
            ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content.nextAiringEpisode?.let { episode ->
                val airingText = content.nextAiringUnixTime?.let { timestamp ->
                    runCatching {
                        java.time.Instant.ofEpochSecond(timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    }.getOrNull()
                }
                Text(
                    "Episode berikutnya${content.nextAiringSeason?.let { " • Season $it" } ?: ""}: $episode${airingText?.let { " • $it" } ?: ""}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content.externalIds.takeIf { it.isNotEmpty() }?.let { ids ->
                Text("ID eksternal", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                ids.entries.forEach { (idKey, idValue) ->
                    val syncName = when (idKey.lowercase().replace("id", "").replace("_", "").replace("-", "")) {
                        "imdb" -> "imdb"
                        "anilist", "ani" -> "anilist"
                        "mal", "myanimelist" -> "mal"
                        "trakt" -> "trakt"
                        "simkl" -> "simkl"
                        else -> null
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$idKey: $idValue", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (syncName != null) {
                            TextButton(
                                enabled = !loadingDetail,
                                onClick = {
                                    loadingDetail = true
                                    scope.launch {
                                        runCatching {
                                            viewModel.pluginManager.resolveCloudStreamExternalId(entry, syncName, idValue)
                                        }.mapCatching { matches ->
                                            val match = matches.firstOrNull()
                                                ?: throw IllegalStateException("Tidak ada provider plugin yang mendukung ID $idKey.")
                                            viewModel.pluginManager.loadCloudStreamContent(entry, match.providerName, match.contentUrl)
                                        }.onSuccess { resolvedContent ->
                                            selectedContent = entry to resolvedContent
                                            streams = emptyList()
                                            linkMessage = ""
                                            searchMessage = "Konten berhasil dibuka melalui ID $idKey."
                                        }.onFailure { error ->
                                            searchMessage = error.message ?: "Gagal membuka konten melalui ID eksternal."
                                        }
                                        loadingDetail = false
                                    }
                                }
                            ) { Text("Buka", fontSize = 9.sp) }
                        }
                    }
                }
            }
            content.trailers.forEachIndexed { index, trailer ->
                TextButton(
                    enabled = !resolvingLinks && (trailer.url.startsWith("https://", true) || trailer.url.startsWith("http://", true)),
                    onClick = {
                        viewModel.playCloudStreamTrailerFromPlugin(
                            entry = entry,
                            providerName = content.providerName,
                            trailer = trailer,
                            title = content.title,
                            posterUrl = content.posterUrl
                        )
                    }
                ) { Text("Putar trailer ${index + 1} di NSiO", fontSize = 9.sp) }
                TextButton(
                    onClick = {
                        runCatching {
                            trailerContext.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(trailer.url)),
                                    "Buka trailer"
                                )
                            )
                        }.onFailure { linkMessage = "Tidak dapat membuka tautan trailer." }
                    }
                ) { Text("Buka trailer", fontSize = 9.sp) }
            }
            content.magnetUrl?.takeIf { it.startsWith("magnet:", true) }?.let { magnet ->
                OutlinedButton(
                    onClick = {
                        runCatching {
                            trailerContext.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(magnet)))
                        }.onFailure { linkMessage = "Tidak ada aplikasi yang dapat membuka magnet link." }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                ) { Text("Buka Magnet", fontSize = 10.sp) }
            }
            content.torrentUrl?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }?.let { torrent ->
                OutlinedButton(
                    onClick = {
                        runCatching {
                            trailerContext.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(torrent)),
                                "Buka file torrent"
                            ))
                        }.onFailure { linkMessage = "Tidak dapat membuka tautan torrent." }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                ) { Text("Buka Torrent", fontSize = 10.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                content.dataUrl?.takeIf { it.isNotBlank() }?.let { data ->
                    Button(
                        onClick = { resolveLinks(entry, content.providerName, data, "Film", content.url, content.posterUrl, autoPlay = true) },
                        enabled = !resolvingLinks,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = Color.White,
                            disabledContainerColor = AccentRed.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.75f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                    ) { Text("▶ Putar Film", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { resolveLinks(entry, content.providerName, data, "Film", content.url, content.posterUrl) },
                        enabled = !resolvingLinks,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentRed,
                            disabledContentColor = AccentRed.copy(alpha = 0.45f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                    ) { Text("Pilih sumber", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                }
                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            append(content.title)
                            content.year?.let { append(" • ").append(it) }
                            content.plot?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
                            append("\n\n").append(content.url)
                        }
                        runCatching {
                            trailerContext.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    },
                                    "Bagikan konten"
                                )
                            )
                        }.onFailure { linkMessage = "Tidak dapat membuka menu berbagi." }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                ) { Text("Bagikan", fontSize = 10.sp) }
            }
            content.plot?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content.cast.takeIf { it.isNotEmpty() }?.let { cast ->
                Text("Pemeran / Artis", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cast.take(20).forEach { person ->
                        Column(modifier = Modifier.width(76.dp)) {
                            if (!person.imageUrl.isNullOrBlank()) AsyncImage(model = contentImageRequest(person.imageUrl), contentDescription = person.name, modifier = Modifier.size(62.dp).clip(CircleShape))
                            Text(person.name, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            person.role?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            person.voiceActorImageUrl?.takeIf { it.isNotBlank() }?.let { voiceImage ->
                                AsyncImage(model = contentImageRequest(voiceImage), contentDescription = "Pengisi suara", modifier = Modifier.size(42.dp).clip(CircleShape))
                            }
                            person.voiceActorName?.takeIf { it.isNotBlank() }?.let {
                                Text("Suara: $it", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (content.episodes.isNotEmpty()) {
                val availableDubStatuses = content.episodes.mapNotNull { it.dubStatus }
                    .distinct()
                    .sortedBy { if (it.equals("Subbed", true)) 0 else 1 }
                var selectedDubStatus by remember(content.url) {
                    mutableStateOf<String?>(availableDubStatuses.firstOrNull())
                }
                val dubFilteredEpisodes = if (selectedDubStatus == null) content.episodes
                    else content.episodes.filter { it.dubStatus == selectedDubStatus }
                val episodesBySeason = dubFilteredEpisodes.groupBy { it.season ?: 0 }.toSortedMap()
                val seasonNumbers = episodesBySeason.keys.toList()
                var selectedSeason by remember(content.url, selectedDubStatus) {
                    mutableIntStateOf(seasonNumbers.firstOrNull() ?: 0)
                }
                val selectedSeasonIndex = seasonNumbers.indexOf(selectedSeason).takeIf { it >= 0 } ?: 0
                // Provider order is not guaranteed; sort numbered episodes and keep specials last.
                val visibleEpisodes = episodesBySeason[seasonNumbers.getOrNull(selectedSeasonIndex)]
                    .orEmpty()
                    .sortedWith(compareBy<com.example.data.plugin.CloudStreamEpisodeItem> { it.episode ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })

                Text("Episode • ${visibleEpisodes.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (availableDubStatuses.size > 1) {
                    Text("Audio / Versi", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    ScrollableTabRow(
                        selectedTabIndex = availableDubStatuses.indexOf(selectedDubStatus).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        availableDubStatuses.forEachIndexed { index, status ->
                            Tab(
                                selected = status == selectedDubStatus,
                                onClick = { selectedDubStatus = status },
                                text = { Text(if (status.equals("Subbed", true)) "Subbed" else if (status.equals("Dubbed", true)) "Dubbed" else status, fontSize = 10.sp) }
                            )
                        }
                    }
                } else {
                    availableDubStatuses.firstOrNull()?.let { status ->
                        Text(
                            "Versi audio: ${if (status.equals("Subbed", true)) "Subbed" else if (status.equals("Dubbed", true)) "Dubbed" else status}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (seasonNumbers.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSeasonIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        seasonNumbers.forEachIndexed { index, seasonNumber ->
                            val seasonLabel = content.seasonNames[seasonNumber]
                                ?: if (seasonNumber > 0) "Season $seasonNumber" else "Lainnya"
                            Tab(
                                selected = index == selectedSeasonIndex,
                                onClick = { selectedSeason = seasonNumber },
                                text = {
                                    Text(
                                        "$seasonLabel • ${episodesBySeason[seasonNumber].orEmpty().size}",
                                        fontSize = 10.sp,
                                        fontWeight = if (index == selectedSeasonIndex) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                } else {
                    val seasonNumber = seasonNumbers.first()
                    Text(
                        content.seasonNames[seasonNumber]
                            ?: if (seasonNumber > 0) "Season $seasonNumber" else "Episode lainnya",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                visibleEpisodes.forEachIndexed { index, episode ->
                    val episodeHistoryId = activeCloudStreamProfile?.id?.let { profileId ->
                        val pluginId = viewModel.pluginManager.cloudStreamPluginId(entry)
                        val stableKey = "$profileId|$pluginId|${content.providerName}|${content.url}|${episode.data}"
                        java.util.UUID.nameUUIDFromBytes(stableKey.toByteArray(Charsets.UTF_8)).toString()
                    }
                    val episodeHistory = episodeHistoryId?.let { id -> cloudStreamHistoryItems.firstOrNull { it.id == id } }
                    val watchedProgress = episodeHistory?.progressMs?.coerceAtLeast(0L) ?: 0L
                    val watchedDuration = maxOf(episodeHistory?.durationMs ?: 0L, (episode.runtimeSeconds?.toLong() ?: 0L) * 1_000L)
                    val isEpisodeComplete = watchedDuration > 0L && watchedProgress >= (watchedDuration * 0.95).toLong()
                    val progressPercent = if (watchedDuration > 0L) ((watchedProgress * 100L) / watchedDuration).toInt().coerceIn(0, 100) else 0
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!episode.posterUrl.isNullOrBlank()) AsyncImage(model = contentImageRequest(episode.posterUrl), contentDescription = episode.name, modifier = Modifier.width(62.dp).height(42.dp).clip(RoundedCornerShape(7.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(listOfNotNull(episode.episode?.let { "E$it" }, episode.name.ifBlank { "Episode ${index + 1}" }).joinToString(" • "), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            episode.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            listOfNotNull(episode.dubStatus?.let { if (it.equals("Subbed", true)) "Subbed" else if (it.equals("Dubbed", true)) "Dubbed" else it }, episode.runtimeSeconds?.takeIf { it > 0 }?.let { "${it / 60} menit" }, episode.rating?.takeIf { it > 0 }?.let { "Rating " + String.format("%.1f", it / 1000f) }, episode.date?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (watchedProgress > 0L) {
                                Text(
                                    if (isEpisodeComplete) "✓ Selesai ditonton" else "Sedang ditonton • $progressPercent%",
                                    fontSize = 8.sp,
                                    color = if (isEpisodeComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressPercent / 100f)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                        TextButton(
                            enabled = !resolvingLinks,
                            onClick = {
                                val episodeLabel = listOfNotNull(
                                    episode.season?.let { "S$it" },
                                    episode.episode?.let { "E$it" },
                                    episode.name.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifBlank { "Episode ${index + 1}" }
                                resolveLinks(
                                    entry,
                                    content.providerName,
                                    episode.data,
                                    episodeLabel,
                                    content.url,
                                    episode.posterUrl ?: content.posterUrl,
                                    (episode.runtimeSeconds?.toLong() ?: 0L) * 1_000L,
                                    skipMetadata = content.externalIds + buildMap {
                                        episode.season?.let { put("season", it.toString()) }
                                        episode.episode?.let { put("episode", it.toString()) }
                                    }
                                )
                            }
                        ) { Text(if (isEpisodeComplete) "Putar ulang" else if (watchedProgress > 0L) "Lanjutkan" else "Link", fontSize = 10.sp) }
                    }
                }
            }
            content.recommendations.takeIf { it.isNotEmpty() }?.let { recommendations ->
                Text("Rekomendasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                recommendations.take(12).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!item.posterUrl.isNullOrBlank()) AsyncImage(model = contentImageRequest(item.posterUrl), contentDescription = item.title, modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(item.contentType, item.year?.toString()).joinToString(" • "), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            enabled = !loadingDetail && item.url.isNotBlank(),
                            onClick = {
                                openCloudStreamDetail(
                                    entry,
                                    content.providerName,
                                    item.url,
                                    item.posterUrl,
                                    item.year,
                                    item.title
                                )
                            }
                        ) { Text("Detail", fontSize = 9.sp) }
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
                        val streamType = stream.streamType.substringAfterLast('.').uppercase()
                        val streamExtension = runCatching {
                            java.net.URI(stream.url).path.substringAfterLast('.', "").uppercase()
                        }.getOrDefault("")
                        val mimeType = when {
                            streamType in setOf("M3U8", "HLS") || streamExtension == "M3U8" ->
                                "application/x-mpegURL"
                            streamType in setOf("DASH", "MPD") || streamExtension == "MPD" ->
                                "application/dash+xml"
                            streamType == "MP4" || streamExtension == "MP4" -> "video/mp4"
                            streamType == "WEBM" || streamExtension == "WEBM" -> "video/webm"
                            streamType in setOf("MKV", "MATROSKA") || streamExtension == "MKV" ->
                                "video/x-matroska"
                            else -> null
                        }
                        val playbackEntry = resolvedPlaybackEntry
                        if (playbackEntry == null || resolvedPlaybackData.isBlank() || resolvedPlaybackContentUrl.isBlank()) {
                            linkMessage = "Konteks episode tidak tersedia. Ambil link episode kembali."
                        } else {
                            viewModel.playCloudStreamEpisode(
                                entry = playbackEntry,
                                contentUrl = resolvedPlaybackContentUrl,
                                providerName = resolvedPlaybackProvider,
                                playbackData = resolvedPlaybackData,
                                title = resolvedPlaybackTitle.ifBlank { stream.name.ifBlank { "CloudStream" } },
                                posterUrl = resolvedPlaybackPoster,
                                durationMs = resolvedPlaybackDurationMs,
                                streamUrl = stream.url,
                                requestHeaders = headers,
                                mimeType = mimeType,
                                externalSubtitles = stream.subtitles,
                                extractorData = stream.extractorData,
                                videoInterceptor = stream.videoInterceptor,
                                availablePluginStreams = streams.map { it.second },
                                skipMetadata = resolvedPlaybackSkipMetadata
                            )
                        }
                    }
                ) { Text("Putar", fontSize = 10.sp) }
            }
                    }
                }
            }
        }
    }
}
