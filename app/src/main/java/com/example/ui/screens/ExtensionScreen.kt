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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material3.OutlinedButton
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
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
        AlertDialog(
            onDismissRequest = { showRepositoryDialog = false },
            title = { Text("Daftar Repository (" + repos.size + ")") },
            text = {
                if (repos.isEmpty()) Text("Belum ada repository.")
                else LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(repos, key = { it.sourceUrl.ifBlank { it.name } }) { repo ->
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
        val entries = repos.flatMap { it.plugins }.distinctBy { it.internalName to it.version }
        AlertDialog(
            onDismissRequest = { showPluginDialog = false },
            title = { Text("Daftar Plugin (" + (entries.size + plugins.size) + ")") },
            text = {
                if (entries.isEmpty() && plugins.isEmpty()) Text("Belum ada plugin.")
                else LazyColumn(modifier = Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.internalName + "-" + it.version }) { entry ->
                        var installed by remember(entry.internalName, entry.version) { mutableStateOf(viewModel.pluginManager.isCloudStreamPluginInstalled(entry)) }
                        var busy by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                        var confirmUninstall by remember(entry.internalName, entry.version) { mutableStateOf(false) }
                        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                            Text(entry.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                entry.internalName + " • v" + entry.version + " • API " + entry.apiVersion + " • " +
                                    viewModel.pluginManager.cloudStreamRepositoryName(entry) + " • " +
                                    if (installed) "terpasang" else "belum dipasang",
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        if (!busy && entry.status != 0) {
                                            busy = true
                                            scope.launch {
                                                runCatching {
                                                    if (!installed) viewModel.pluginManager.installCloudStreamPlugin(entry)
                                                    viewModel.pluginManager.loadCloudStreamPlugin(entry)
                                                }.onSuccess { installed = true }
                                                 .onFailure { repoFeedback = it.message ?: "Gagal memasang plugin." }
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy && entry.status != 0,
                                    modifier = Modifier.weight(1f)
                                ) { if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) else Text(if (installed) "Aktifkan" else "Pasang & aktifkan", fontSize = 10.sp) }
                                if (installed) {
                                    IconButton(onClick = { confirmUninstall = true }, enabled = !busy) {
                                        Icon(Icons.Default.Delete, contentDescription = "Copot plugin", tint = AccentRed)
                                    }
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
                                    dismissButton = {
                                        TextButton(onClick = { confirmUninstall = false }) { Text("Batal") }
                                    }
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
            },
            confirmButton = { TextButton(onClick = { showPluginDialog = false }) { Text("Tutup") } }
        )
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
fun CloudStreamSearchPanel(
    viewModel: MainViewModel,
    repos: List<com.example.data.plugin.RepoManifest>,
    query: String,
    searchRequest: Int
) {
    val trailerContext = LocalContext.current
    var loadingHomeCatalog by remember { mutableStateOf(false) }
    var savingHomeCatalog by remember { mutableStateOf(false) }
    var homeCatalogMessage by remember { mutableStateOf("") }
    var homeCatalog by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamHomeSection>>>(emptyList())
    }
    var homeCatalogPages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loadingMoreCatalog by remember { mutableStateOf<Set<String>>(emptySet()) }
    var autoLoadAttempts by remember { mutableStateOf<Set<String>>(emptySet()) }
    var catalogSectionErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
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
    var streams by remember {
        mutableStateOf<List<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamStream>>>(emptyList())
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun catalogSectionKey(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        section: com.example.data.plugin.CloudStreamHomeSection
    ): String = "${viewModel.pluginManager.cloudStreamPluginId(entry)}|${section.providerName}|${section.sectionName}"

    fun loadNextCatalogPage(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        section: com.example.data.plugin.CloudStreamHomeSection,
        automatic: Boolean = false
    ) {
        val sectionKey = catalogSectionKey(entry, section)
        val currentPage = homeCatalogPages[sectionKey] ?: 1
        val attemptKey = "$sectionKey|${currentPage + 1}"
        if (!section.hasNext || sectionKey in loadingMoreCatalog) return
        if (automatic && attemptKey in autoLoadAttempts) return
        if (automatic) autoLoadAttempts = autoLoadAttempts + attemptKey
        loadingMoreCatalog = loadingMoreCatalog + sectionKey
        scope.launch {
            runCatching {
                viewModel.pluginManager.loadCloudStreamHomePage(entry, currentPage + 1)
            }.onSuccess { nextSections ->
                val matching = nextSections.firstOrNull {
                    it.providerName == section.providerName &&
                        it.sectionName == section.sectionName
                }
                if (matching != null) {
                    catalogSectionErrors = catalogSectionErrors - sectionKey
                    homeCatalog = homeCatalog.map { pair ->
                        if (pair.first == entry && catalogSectionKey(pair.first, pair.second) == sectionKey) {
                            pair.first to pair.second.copy(
                                items = (pair.second.items + matching.items).distinctBy { it.url },
                                hasNext = matching.hasNext
                            )
                        } else pair
                    }
                    homeCatalogPages = homeCatalogPages + (sectionKey to (currentPage + 1))
                } else {
                    catalogSectionErrors = catalogSectionErrors +
                        (sectionKey to "Halaman berikutnya tidak mengembalikan kategori ini.")
                }
            }.onFailure { error ->
                catalogSectionErrors = catalogSectionErrors +
                    (sectionKey to "Gagal memuat halaman berikutnya: ${error.message ?: "error"}")
            }
            loadingMoreCatalog = loadingMoreCatalog - sectionKey
        }
    }

    val installedEntries = repos.flatMap { it.plugins }
        .filter { it.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(it) }
        .distinctBy { it.internalName to it.version }

    val pendingCloudStreamDetail by viewModel.pendingCloudStreamDetail.collectAsState()
    LaunchedEffect(pendingCloudStreamDetail?.id, installedEntries) {
        val pendingItem = pendingCloudStreamDetail ?: return@LaunchedEffect
        loadingDetail = true
        runCatching {
            val entry = viewModel.pluginManager.findCloudStreamPlugin(pendingItem.pluginId)
                ?: throw IllegalStateException("Plugin asal konten tidak ditemukan atau belum terpasang.")
            val metadata = runCatching { org.json.JSONObject(pendingItem.extraInfo) }.getOrNull()
            val providerName = metadata?.optString("providerName")
                ?.takeIf { it.isNotBlank() } ?: pendingItem.extraInfo
            val content = viewModel.pluginManager.loadCloudStreamContent(entry, providerName, pendingItem.url)
            entry to content.copy(
                posterUrl = content.posterUrl?.takeIf { it.isNotBlank() } ?: pendingItem.thumbnailUrl,
                title = content.title.ifBlank { pendingItem.title }
            )
        }.onSuccess { (entry, content) ->
            selectedContent = entry to content
            streams = emptyList()
            linkMessage = ""
            searchMessage = "Detail CloudStream siap. Pilih film atau episode untuk mengambil sumber pemutaran."
        }.onFailure { error ->
            searchMessage = "Gagal membuka detail CloudStream: ${error.localizedMessage ?: "provider tidak merespons"}"
        }
        viewModel.clearPendingCloudStreamDetail(pendingItem.id)
        loadingDetail = false
    }

    fun resolveLinks(
        entry: com.example.data.plugin.CloudStreamPluginEntry,
        providerName: String,
        data: String,
        target: String,
        contentUrl: String = selectedContent?.second?.url.orEmpty(),
        posterUrl: String? = selectedContent?.second?.posterUrl,
        durationMs: Long = if (target == "Film") (selectedContent?.second?.durationMinutes?.toLong() ?: 0L) * 60_000L else 0L,
        autoPlay: Boolean = false
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
        resolvedPlaybackContentUrl = contentUrl
        linkMessage = ""
        streams = emptyList()
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
                            category = "CloudStream • Film"
                        )
                        linkMessage = "Memutar film melalui ${selectedStream.name.ifBlank { selectedStream.source }}."
                    }
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
            text = "Katalog & hasil CloudStream",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Pencarian CloudStream menggunakan kolom pencarian utama di bagian atas Home dan hanya memakai plugin .cs3 yang sudah terpasang. Plugin akan dimuat otomatis saat pencarian dijalankan. Plugin pihak ketiga berjalan dengan izin aplikasi—gunakan repository yang kamu percaya.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                loadingHomeCatalog = true
                homeCatalog = emptyList()
                homeCatalogPages = emptyMap()
                loadingMoreCatalog = emptySet()
                autoLoadAttempts = emptySet()
                catalogSectionErrors = emptyMap()
                homeCatalogMessage = ""
                scope.launch {
                    val sections = mutableListOf<Pair<com.example.data.plugin.CloudStreamPluginEntry, com.example.data.plugin.CloudStreamHomeSection>>()
                    val failures = mutableListOf<String>()
                    var supportedProviders = 0
                    installedEntries.forEach { entry ->
                        runCatching {
                            viewModel.pluginManager.loadCloudStreamHomePage(entry, 1)
                        }.onSuccess { loaded ->
                            if (loaded.isNotEmpty()) supportedProviders++
                            loaded.forEach { section -> sections.add(entry to section) }
                        }.onFailure { error ->
                            failures.add("${entry.name}: ${error.message ?: "gagal memuat"}")
                        }
                    }
                    homeCatalog = sections
                    homeCatalogPages = sections.associate { (entry, section) -> catalogSectionKey(entry, section) to 1 }
                    homeCatalogMessage = when {
                        sections.isNotEmpty() && failures.isNotEmpty() ->
                            "${sections.sumOf { it.second.items.size }} item dari ${sections.size} kategori; ${failures.size} plugin bermasalah."
                        sections.isNotEmpty() ->
                            "${sections.sumOf { it.second.items.size }} item dari ${sections.size} kategori."
                        installedEntries.isEmpty() -> "Belum ada plugin .cs3 terpasang."
                        failures.isNotEmpty() -> failures.take(3).joinToString(" • ")
                        else -> "Plugin terpasang tidak menyediakan katalog utama (Main Page). Gunakan pencarian."
                    }
                    loadingHomeCatalog = false
                }
            },
            enabled = !loadingHomeCatalog && installedEntries.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loadingHomeCatalog) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            else Text("Muat katalog utama dari ${installedEntries.size} plugin", fontSize = 11.sp)
        }
        if (homeCatalogMessage.isNotBlank()) {
            Text(homeCatalogMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (homeCatalog.isNotEmpty()) {
            Button(
                onClick = {
                    savingHomeCatalog = true
                    scope.launch {
                        var saved = 0
                        val failures = mutableListOf<String>()
                        homeCatalog.groupBy { it.first }.forEach { (entry, rows) ->
                            runCatching {
                                viewModel.persistCloudStreamCatalog(entry, rows.map { it.second })
                            }.onSuccess { saved += it }
                                .onFailure { failures += "${entry.name}: ${it.message ?: "gagal menyimpan"}" }
                        }
                        homeCatalogMessage = if (failures.isEmpty()) {
                            "Berhasil menyimpan $saved item katalog ke Home."
                        } else {
                            "$saved item tersimpan; " + failures.take(3).joinToString(" • ")
                        }
                        savingHomeCatalog = false
                    }
                },
                enabled = !savingHomeCatalog,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                if (savingHomeCatalog) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                else Text("Simpan katalog ke Home", fontSize = 11.sp)
            }
        }
        homeCatalog.forEach { (entry, section) ->
            Text(
                "${section.sectionName} • ${entry.name} • ${section.items.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            val sectionError = catalogSectionErrors[catalogSectionKey(entry, section)]
            if (!sectionError.isNullOrBlank()) {
                Text(sectionError, fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
            }
            if (section.hasNext) {
                val sectionKey = catalogSectionKey(entry, section)
                val currentPage = homeCatalogPages[sectionKey] ?: 1
                TextButton(
                    enabled = sectionKey !in loadingMoreCatalog,
                    onClick = { loadNextCatalogPage(entry, section) }
                ) {
                    if (sectionKey in loadingMoreCatalog) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Muat lainnya • halaman ${currentPage + 1}", fontSize = 9.sp)
                    }
                }
            }
            section.items.forEachIndexed { itemIndex, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).then(
                        if (itemIndex == section.items.lastIndex && section.hasNext) {
                            Modifier.onGloballyPositioned { coordinates ->
                                val y = coordinates.positionInRoot().y
                                if (y >= 0f && y <= viewportHeightPx + 240f) {
                                    loadNextCatalogPage(entry, section, automatic = true)
                                }
                            }
                        } else Modifier
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = "Poster ${item.title}",
                            modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(7.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.providerName}${item.year?.let { " • $it" } ?: ""}",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        enabled = !loadingDetail,
                        onClick = {
                            loadingDetail = true
                            scope.launch {
                                runCatching {
                                    viewModel.pluginManager.loadCloudStreamContent(entry, item.providerName, item.url)
                                }.onSuccess { content ->
                                    selectedContent = entry to content.copy(
                                        posterUrl = content.posterUrl?.takeIf { it.isNotBlank() } ?: item.posterUrl,
                                        year = content.year ?: item.year,
                                        title = content.title.ifBlank { item.title }
                                    )
                                    streams = emptyList()
                                    linkMessage = ""
                                }.onFailure { error ->
                                    homeCatalogMessage = error.message ?: "Gagal membuka detail katalog."
                                }
                                loadingDetail = false
                            }
                        }
                    ) { Text("Detail", fontSize = 9.sp) }
                }
            }
        }
        LaunchedEffect(searchRequest) {
            if (searchRequest <= 0 || query.isBlank()) return@LaunchedEffect
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
                installedEntries.isEmpty() -> "Belum ada plugin .cs3 terpasang."
                failures.isNotEmpty() -> failures.take(2).joinToString(" • ")
                else -> "Tidak ada hasil CloudStream untuk kata kunci ini."
            }
            searching = false
        }
        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Mencari di ${installedEntries.size} plugin .cs3...", fontSize = 10.sp)
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
        results.forEach { (entry, result) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!result.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.posterUrl,
                        contentDescription = "Poster ${result.title}",
                        modifier = Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(7.dp))
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
                    onClick = {
                        loadingDetail = true
                        streams = emptyList()
                        scope.launch {
                            runCatching {
                                viewModel.pluginManager.loadCloudStreamContent(entry, result.providerName, result.url)
                            }.onSuccess { content ->
                                // Search cards often carry poster/year even when a provider's
                                // detail response omits one of those fields.
                                selectedContent = entry to content.copy(
                                    posterUrl = content.posterUrl?.takeIf { it.isNotBlank() } ?: result.posterUrl,
                                    year = content.year ?: result.year,
                                    title = content.title.ifBlank { result.title }
                                )
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
            content.plot?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content.cast.takeIf { it.isNotEmpty() }?.let { cast ->
                Text("Pemeran / Artis", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cast.take(10).forEach { person ->
                        Column(modifier = Modifier.width(76.dp)) {
                            if (!person.imageUrl.isNullOrBlank()) AsyncImage(model = person.imageUrl, contentDescription = person.name, modifier = Modifier.size(62.dp).clip(CircleShape))
                            Text(person.name, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            person.role?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            person.voiceActorImageUrl?.takeIf { it.isNotBlank() }?.let { voiceImage ->
                                AsyncImage(model = voiceImage, contentDescription = "Pengisi suara", modifier = Modifier.size(42.dp).clip(CircleShape))
                            }
                            person.voiceActorName?.takeIf { it.isNotBlank() }?.let {
                                Text("Suara: $it", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            content.trailers.forEachIndexed { index, trailer ->
                TextButton(
                    enabled = !resolvingLinks && (trailer.url.startsWith("https://", true) || trailer.url.startsWith("http://", true)),
                    onClick = {
                        resolvingLinks = true
                        linkMessage = "Menyiapkan trailer di pemutar NSiO..."
                        scope.launch {
                            runCatching {
                                viewModel.pluginManager.resolveCloudStreamTrailer(entry, content.providerName, trailer)
                                    .sortedWith(compareByDescending<com.example.data.plugin.CloudStreamStream> { it.quality })
                                    .firstOrNull()
                                    ?: throw IllegalStateException("Extractor tidak menghasilkan stream trailer.")
                            }.onSuccess { trailerStream ->
                                linkMessage = ""
                                viewModel.playCloudStreamTrailer(content.title, content.posterUrl, trailerStream)
                            }.onFailure { error ->
                                linkMessage = "Trailer gagal disiapkan: ${error.localizedMessage ?: "stream tidak tersedia"}"
                            }
                            resolvingLinks = false
                        }
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
                val visibleEpisodes = episodesBySeason[seasonNumbers.getOrNull(selectedSeasonIndex)].orEmpty()

                Text("Episode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!episode.posterUrl.isNullOrBlank()) AsyncImage(model = episode.posterUrl, contentDescription = episode.name, modifier = Modifier.width(62.dp).height(42.dp).clip(RoundedCornerShape(7.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(listOfNotNull(episode.episode?.let { "E$it" }, episode.name.ifBlank { "Episode ${index + 1}" }).joinToString(" • "), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            episode.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            listOfNotNull(episode.dubStatus?.let { if (it.equals("Subbed", true)) "Subbed" else if (it.equals("Dubbed", true)) "Dubbed" else it }, episode.runtimeSeconds?.takeIf { it > 0 }?.let { "${it / 60} menit" }, episode.rating?.takeIf { it > 0 }?.let { "Rating " + String.format("%.1f", it / 1000f) }, episode.date?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                    (episode.runtimeSeconds?.toLong() ?: 0L) * 1_000L
                                )
                            }
                        ) { Text("Link", fontSize = 10.sp) }
                    }
                }
            }
            content.recommendations.takeIf { it.isNotEmpty() }?.let { recommendations ->
                Text("Rekomendasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                recommendations.take(12).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!item.posterUrl.isNullOrBlank()) AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(item.contentType, item.year?.toString()).joinToString(" • "), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            enabled = !loadingDetail && item.url.isNotBlank(),
                            onClick = {
                                loadingDetail = true
                                scope.launch {
                                    runCatching {
                                        viewModel.pluginManager.loadCloudStreamContent(entry, content.providerName, item.url)
                                    }.onSuccess { recommendedContent ->
                                        selectedContent = entry to recommendedContent
                                        streams = emptyList()
                                        linkMessage = ""
                                    }.onFailure { error ->
                                        linkMessage = "Gagal membuka rekomendasi: ${error.localizedMessage ?: "provider tidak merespons"}"
                                    }
                                    loadingDetail = false
                                }
                            }
                        ) { Text("Detail", fontSize = 9.sp) }
                    }
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
                                availablePluginStreams = streams.map { it.second }
                            )
                        }
                    }
                ) { Text("Putar", fontSize = 10.sp) }
            }
        }
    }
}
