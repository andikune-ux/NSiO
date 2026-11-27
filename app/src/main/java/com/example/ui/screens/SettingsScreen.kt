package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import android.app.DownloadManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.firebase.AuthState
import com.example.ui.MainViewModel
import com.example.ui.components.liquidGlass
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AccentRed
import com.example.update.AppUpdateManager
import com.example.embedded.ui.EmbeddedModuleCenter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val devMode by viewModel.devMode.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isSyncing by viewModel.isCloudSyncing.collectAsState()
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val sourcePlaylists by viewModel.sourcePlaylists.collectAsState()

    var showServerPinDialog by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var updateRelease by remember { mutableStateOf<AppUpdateManager.Release?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateDownloadId by remember { mutableStateOf(AppUpdateManager.pendingDownloadId(context)) }
    var updateVersion by remember { mutableStateOf(AppUpdateManager.pendingVersion(context)) }
    var updateProgress by remember { mutableStateOf(0) }
    var readyToInstallVersion by remember { mutableStateOf<String?>(null) }
    var updateStorageAllowed by remember { mutableStateOf(AppUpdateManager.hasUpdateStorageAccess(context)) }
    var updateHistory by remember { mutableStateOf(AppUpdateManager.listDownloadedApks(context)) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            updateStorageAllowed = AppUpdateManager.hasUpdateStorageAccess(context)
            updateHistory = AppUpdateManager.listDownloadedApks(context)
            delay(5_000)
        }
    }

    LaunchedEffect(Unit) {
        updateChecking = true
        runCatching { AppUpdateManager.checkLatest() }
            .onSuccess { release ->
                updateRelease = release
                updateMessage = if (release.available) "Pembaruan v${release.latestVersion} tersedia."
                    else "NSiO sudah menggunakan versi terbaru."
            }
            .onFailure { error ->
                updateMessage = "Pemeriksaan otomatis gagal: ${error.localizedMessage ?: "periksa koneksi internet"}"
            }
        updateChecking = false
    }

    LaunchedEffect(updateDownloadId, updateVersion, readyToInstallVersion) {
        updateStorageAllowed = AppUpdateManager.hasUpdateStorageAccess(context)
        updateHistory = AppUpdateManager.listDownloadedApks(context)
        val id = updateDownloadId ?: return@LaunchedEffect
        val version = updateVersion ?: return@LaunchedEffect
        // After a successful installation Android restarts NSiO with the new
        // BuildConfig version. Clear the old completed download instead of
        // showing it as an invalid APK on every subsequent visit.
        if (AppUpdateManager.compareVersions(BuildConfig.VERSION_NAME, version) >= 0) {
            AppUpdateManager.clearPending(context)
            updateDownloadId = null
            updateVersion = null
            readyToInstallVersion = null
            updateProgress = 0
            updateMessage = "Pembaruan v$version sudah terpasang."
            return@LaunchedEffect
        }
        while (true) {
            val state = AppUpdateManager.queryDownload(context, id)
            if (state == null) {
                updateMessage = "Status unduhan tidak ditemukan. Silakan cek pembaruan kembali."
                AppUpdateManager.clearPending(context)
                updateDownloadId = null
                updateVersion = null
                break
            }
            updateProgress = state.progress
            when (state.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (AppUpdateManager.finalizeDownloadedApk(context, version) && AppUpdateManager.isDownloadedApkValid(context, version)) {
                        readyToInstallVersion = version
                        updateMessage = "APK v$version selesai diunduh dan lolos validasi."
                    } else {
                        updateMessage = "APK selesai diunduh tetapi gagal validasi. Hapus unduhan dan coba lagi."
                        AppUpdateManager.clearPending(context)
                    }
                    updateDownloadId = null
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    updateMessage = "Unduhan gagal (kode ${state.reason}). Silakan coba lagi."
                    AppUpdateManager.clearPending(context)
                    updateDownloadId = null
                    updateVersion = null
                    break
                }
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                    updateMessage = if (state.status == DownloadManager.STATUS_PAUSED) "Unduhan dijeda..." else "Mengunduh NSiO v$version..."
                }
            }
            delay(500)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "Menu Pengaturan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Konfigurasi tampilan UI, Cloud Sync Firebase, ukuran grid, dan preferensi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsGlassCard(
                icon = Icons.Default.GridOn,
                title = "Mode Developer",
                subtitle = "Aktifkan seleksi teks di layar agar tulisan dapat disalin dengan tekan-tahan."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Izinkan salin teks layar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (devMode) "Aktif • teks dapat diseleksi dan disalin"
                            else "Nonaktif • seleksi teks standar dimatikan",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = devMode, onCheckedChange = { viewModel.setDevModeEnabled(it) })
                }
            }
        }

        item {
            val telegramCredentialStore = remember(context) {
                com.example.data.security.TelegramApiCredentialStore(context)
            }
            var telegramApiId by remember { mutableStateOf("") }
            var telegramApiHash by remember { mutableStateOf("") }
            var telegramCredentialsSaved by remember { mutableStateOf(false) }
            var telegramCredentialMessage by remember { mutableStateOf<String?>(null) }
            var telegramPhone by remember { mutableStateOf("") }
            var telegramCode by remember { mutableStateOf("") }
            var telegramPassword by remember { mutableStateOf("") }
            var selectedTelegramChatId by remember { mutableStateOf<Long?>(null) }
            var selectedTelegramChatIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
            var telegramChatSearch by remember { mutableStateOf("") }
            val telegramState by com.example.data.telegram.TelegramClientManager.state.collectAsState()
            LaunchedEffect(telegramCredentialStore) {
                val saved = telegramCredentialStore.load()
                if (saved != null) {
                    telegramApiId = saved.apiId
                    telegramApiHash = saved.apiHash
                    telegramCredentialsSaved = true
                }
            }
            SettingsGlassCard(
                icon = Icons.Default.Language,
                title = "Integrasi Telegram",
                subtitle = "Simpan API ID dan API Hash Telegram secara terenkripsi di perangkat ini."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = telegramApiId,
                        onValueChange = {
                            telegramApiId = it.filter(Char::isDigit).take(20)
                            telegramCredentialsSaved = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("telegram_api_id_input"),
                        singleLine = true,
                        label = { Material3Text("API ID") },
                        placeholder = { Material3Text("Masukkan API ID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = telegramApiHash,
                        onValueChange = {
                            telegramApiHash = it.filter { ch -> ch.isLetterOrDigit() }.take(32)
                            telegramCredentialsSaved = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("telegram_api_hash_input"),
                        singleLine = true,
                        label = { Material3Text("API Hash") },
                        placeholder = { Material3Text("32 karakter API Hash") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                    Text(
                        "Kredensial disimpan memakai Android Keystore dan tidak disinkronkan ke Cloud atau GitHub.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (telegramCredentialsSaved) {
                        Text("API Telegram tersimpan di perangkat ini.", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    telegramCredentialMessage?.let {
                        Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                telegramCredentialMessage = runCatching {
                                    telegramCredentialStore.save(telegramApiId.trim(), telegramApiHash.trim())
                                }.exceptionOrNull()?.localizedMessage
                                if (telegramCredentialMessage == null) {
                                    telegramCredentialsSaved = true
                                    Toast.makeText(context, "Kredensial Telegram tersimpan.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = telegramApiId.isNotBlank() && telegramApiHash.length == 32,
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                        ) {
                            Text("Simpan API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                telegramCredentialStore.clear()
                                telegramApiId = ""
                                telegramApiHash = ""
                                telegramCredentialsSaved = false
                                telegramCredentialMessage = null
                                Toast.makeText(context, "Kredensial Telegram dihapus.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = telegramCredentialsSaved || telegramApiId.isNotBlank() || telegramApiHash.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                        ) {
                            Text("Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Status: ${telegramState.message}",
                        fontSize = 11.sp,
                        color = if (telegramState.stage == com.example.data.telegram.TelegramClientManager.AuthStage.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    when (telegramState.stage) {
                        com.example.data.telegram.TelegramClientManager.AuthStage.NOT_CONFIGURED,
                        com.example.data.telegram.TelegramClientManager.AuthStage.ERROR -> {
                            Button(
                                onClick = {
                                    runCatching { com.example.data.telegram.TelegramClientManager.initialize(context) }
                                        .onFailure { telegramCredentialMessage = it.localizedMessage }
                                },
                                enabled = telegramCredentialsSaved,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            ) { Text("Hubungkan Telegram", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        com.example.data.telegram.TelegramClientManager.AuthStage.INITIALIZING -> {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        com.example.data.telegram.TelegramClientManager.AuthStage.WAITING_PHONE -> {
                            OutlinedTextField(
                                value = telegramPhone, onValueChange = { telegramPhone = it.take(18) },
                                modifier = Modifier.fillMaxWidth().testTag("telegram_phone_input"),
                                singleLine = true, label = { Material3Text("Nomor telepon (+62…)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                            Button(onClick = {
                                runCatching { com.example.data.telegram.TelegramClientManager.submitPhoneNumber(telegramPhone) }
                                    .onFailure { telegramCredentialMessage = it.localizedMessage }
                            }, enabled = telegramPhone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                Text("Kirim kode Telegram")
                            }
                        }
                        com.example.data.telegram.TelegramClientManager.AuthStage.WAITING_CODE -> {
                            OutlinedTextField(
                                value = telegramCode, onValueChange = { telegramCode = it.filter(Char::isDigit).take(10) },
                                modifier = Modifier.fillMaxWidth().testTag("telegram_code_input"),
                                singleLine = true, label = { Material3Text("Kode verifikasi") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Button(onClick = {
                                runCatching { com.example.data.telegram.TelegramClientManager.submitCode(telegramCode) }
                                    .onFailure { telegramCredentialMessage = it.localizedMessage }
                            }, enabled = telegramCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                Text("Verifikasi kode")
                            }
                        }
                        com.example.data.telegram.TelegramClientManager.AuthStage.WAITING_PASSWORD -> {
                            OutlinedTextField(
                                value = telegramPassword, onValueChange = { telegramPassword = it },
                                modifier = Modifier.fillMaxWidth().testTag("telegram_2fa_input"),
                                singleLine = true, label = { Material3Text("Kata sandi 2 langkah") },
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Button(onClick = {
                                runCatching { com.example.data.telegram.TelegramClientManager.submitPassword(telegramPassword) }
                                    .onFailure { telegramCredentialMessage = it.localizedMessage }
                            }, enabled = telegramPassword.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                Text("Verifikasi 2 langkah")
                            }
                        }
                        com.example.data.telegram.TelegramClientManager.AuthStage.READY -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Channel & grup (${telegramState.chats.size} dimuat)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { com.example.data.telegram.TelegramClientManager.loadChats() }) { Text("Refresh") }
                            }
                            Text("Pilih satu atau beberapa channel/grup. Setiap sumber akan dibuat sebagai playlist terpisah.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = telegramChatSearch,
                                onValueChange = { telegramChatSearch = it },
                                modifier = Modifier.fillMaxWidth().testTag("telegram_chat_search"),
                                singleLine = true,
                                label = { Material3Text("Cari channel atau grup") }
                            )
                            val visibleTelegramChats = telegramState.chats.filter {
                                it.title.contains(telegramChatSearch.trim(), ignoreCase = true) ||
                                    it.kind.contains(telegramChatSearch.trim(), ignoreCase = true)
                            }
                            val mainTelegramChats = visibleTelegramChats.filterNot { it.isArchived }
                            val archivedTelegramChats = visibleTelegramChats.filter { it.isArchived }
                            listOf(
                                "Chat utama" to mainTelegramChats,
                                "Arsip Telegram" to archivedTelegramChats
                            ).forEach { (sectionTitle, sectionChats) ->
                                if (sectionChats.isNotEmpty()) {
                                    Text("$sectionTitle (${sectionChats.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    sectionChats.forEach { chat ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    selectedTelegramChatId = chat.id
                                                    com.example.data.telegram.TelegramClientManager.loadRecentMedia(chat.id)
                                                }
                                                .padding(horizontal = 5.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val telegramAvatar = sourcePlaylists
                                                .firstOrNull { it.url == "telegram://chat/${chat.id}" }
                                                ?.thumbnailUrl
                                                ?.takeIf { it.isNotBlank() }
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (telegramAvatar != null) {
                                                    AsyncImage(
                                                        model = telegramAvatar,
                                                        contentDescription = "Foto profil ${chat.title}",
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Language,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }
                                            Checkbox(
                                                checked = chat.id in selectedTelegramChatIds,
                                                onCheckedChange = { checked ->
                                                    selectedTelegramChatIds = if (checked) selectedTelegramChatIds + chat.id
                                                        else selectedTelegramChatIds - chat.id
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(chat.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                Text(chat.kind, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (selectedTelegramChatId == chat.id) {
                                                Text("Media dipilih", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                            } else if (chat.unreadCount > 0) Text(chat.unreadCount.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            if (visibleTelegramChats.isEmpty()) {
                                Text("Tidak ada channel/grup yang cocok dengan pencarian.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    val chosen = telegramState.chats.filter { it.id in selectedTelegramChatIds }
                                    chosen.forEach { viewModel.addTelegramChatPlaylist(it.id, it.title) }
                                    selectedTelegramChatIds = emptySet()
                                },
                                enabled = selectedTelegramChatIds.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text("Tambahkan ${selectedTelegramChatIds.size} sumber ke Daftar Playlist")
                            }
                            if (selectedTelegramChatId != null) {
                                val selectedChat = telegramState.chats.firstOrNull { it.id == selectedTelegramChatId }
                                Text("Media terbaru — ${selectedChat?.title.orEmpty()}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (telegramState.media.isEmpty()) {
                                    Text("Belum ada media yang didukung pada 50 pesan terbaru atau sumber sedang dimuat.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                telegramState.media.forEach { media ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(media.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(if (media.isPhoto) "Foto" else "Video", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = {
                                            val chatTitle = telegramState.chats.firstOrNull { it.id == media.chatId }?.title ?: "Telegram ${media.chatId}"
                                            viewModel.addTelegramMediaToPlaylist(
                                                media,
                                                "telegram://chat/${media.chatId}",
                                                chatTitle
                                            ) { added ->
                                                Toast.makeText(context, if (added) "Media ditambahkan ke playlist Telegram '$chatTitle'." else "Media gagal diimpor.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("Impor") }
                                    }
                                }
                            }
                            TextButton(onClick = { com.example.data.telegram.TelegramClientManager.logout() }) {
                                Text("Keluar dari Telegram", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    telegramCredentialMessage?.let {
                        Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            SettingsGlassCard(
                icon = Icons.Default.CloudDownload,
                title = "Download Manager",
                subtitle = "Antrean unduhan tersimpan dan dapat dilanjutkan setelah aplikasi dibuka kembali."
            ) {
                if (downloads.isEmpty()) {
                    Text("Belum ada unduhan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        downloads.take(12).forEach { download ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(download.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${download.status} • ${download.progressPercent.coerceIn(0, 100)}% • " +
                                        if (download.totalBytes > 0L) "${download.downloadedBytes / (1024 * 1024)} / ${download.totalBytes / (1024 * 1024)} MB"
                                        else "Ukuran belum diketahui",
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (download.status) {
                                        "DOWNLOADING", "PENDING" -> IconButton(onClick = { viewModel.pauseDownload(download.id) }) {
                                            Icon(Icons.Default.Pause, contentDescription = "Jeda unduhan")
                                        }
                                        "PAUSED" -> IconButton(onClick = { viewModel.resumeDownload(download.id) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan unduhan")
                                        }
                                        "FAILED" -> IconButton(onClick = { viewModel.retryDownload(download.id) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Coba lagi")
                                        }
                                    }
                                    IconButton(onClick = { viewModel.cancelDownload(download.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus unduhan")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            EmbeddedModuleCenter()
        }

        // 1. Firebase Cloud Firestore & Google Sign-In Card
        item {
            SettingsGlassCard(
                icon = Icons.Default.CloudSync,
                title = "Cloud Persistence & Google Sign-In (Firebase)",
                subtitle = "Sinkronkan daftar link, profil, dan bookmark ke Google Cloud Firestore"
            ) {
                when (val state = authState) {
                    is AuthState.Authenticated -> {
                        val user = state.user
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val compact = maxWidth < 380.dp
                                val avatar: @Composable () -> Unit = {
                                    if (!user.photoUrl?.toString().isNullOrEmpty()) {
                                        AsyncImage(
                                            model = user.photoUrl.toString(),
                                            contentDescription = "Avatar Pengguna",
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text((user.displayName ?: "U").take(1).uppercase(),
                                                fontWeight = FontWeight.Black, fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                                val identity: @Composable () -> Unit = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(user.displayName ?: "Pengguna Google",
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(user.email ?: user.uid, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                val cloudBadge: @Composable () -> Unit = {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Cloud Terhubung", fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (compact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            avatar()
                                            cloudBadge()
                                        }
                                        identity()
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        avatar()
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.displayName ?: "Pengguna Google",
                                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface)
                                            Text(user.email ?: user.uid, fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        cloudBadge()
                                    }
                                }
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val compact = maxWidth < 380.dp
                                val syncButton: @Composable (Modifier) -> Unit = { buttonSize ->
                                    Button(
                                        onClick = { viewModel.syncToCloud() },
                                        enabled = !isSyncing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = buttonSize
                                    ) {
                                        if (isSyncing) CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        ) else Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sinkronkan ke Cloud", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                                val restoreButton: @Composable (Modifier) -> Unit = { buttonSize ->
                                    Button(
                                        onClick = { viewModel.restoreFromCloud() },
                                        enabled = !isSyncing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = buttonSize
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pulihkan dari Cloud", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                                if (compact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        syncButton(Modifier.fillMaxWidth().heightIn(min = 48.dp))
                                        restoreButton(Modifier.fillMaxWidth().heightIn(min = 48.dp))
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        syncButton(Modifier.weight(1f).heightIn(min = 48.dp))
                                        restoreButton(Modifier.weight(1f).heightIn(min = 48.dp))
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.signOut() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = AccentRed
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Keluar Akun Google", fontSize = 11.sp)
                            }
                        }
                    }
                    is AuthState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Menghubungkan ke layanan Google Sign-In...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Masuk dengan akun Google untuk mengamankan data streaming, bookmark, dan pengaturan multi-profil Anda ke Cloud Firestore.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.signInWithGoogle() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_google_signin")
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Masuk dengan Google (Cloud Sync)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Language Setting
        item {
            SettingsGlassCard(
                icon = Icons.Default.Language,
                title = "Pengaturan Bahasa",
                subtitle = "Pilih bahasa antarmuka aplikasi Net Stream iO"
            ) {
                val languages = listOf("ID" to "Bahasa Indonesia", "EN" to "English")
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 380.dp
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            languages.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.language == code, Modifier.fillMaxWidth()) {
                                    viewModel.updateSettings(settings.copy(language = code))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            languages.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.language == code, Modifier.weight(1f)) {
                                    viewModel.updateSettings(settings.copy(language = code))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Theme Setting
        item {
            SettingsGlassCard(
                icon = Icons.Default.BrightnessMedium,
                title = "Pengaturan Tema",
                subtitle = "Pilih gaya visualisasi tema antarmuka"
            ) {
                val themes = listOf("AUTO" to "Otomatis", "DARK" to "Gelap Glass", "LIGHT" to "Terang")
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 380.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            themes.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.themeMode == code, Modifier.fillMaxWidth()) {
                                    viewModel.updateSettings(settings.copy(themeMode = code))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            themes.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.themeMode == code, Modifier.weight(1f)) {
                                    viewModel.updateSettings(settings.copy(themeMode = code))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Display Setting & Orientation
        item {
            SettingsGlassCard(
                icon = Icons.Default.Tv,
                title = "Pengaturan Tampilan & Orientasi",
                subtitle = "Optimasi tata letak remote TV D-pad (Landscape) atau sentuhan HP (Portrait)"
            ) {
                val modes = listOf("AUTO" to "Otomatis", "TV" to "TV Landscape", "HP" to "HP Portrait")
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 380.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modes.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.displayMode == code, Modifier.fillMaxWidth()) {
                                    viewModel.updateSettings(settings.copy(displayMode = code))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            modes.forEach { (code, label) ->
                                SettingsChoiceButton(label, settings.displayMode == code, Modifier.weight(1f)) {
                                    viewModel.updateSettings(settings.copy(displayMode = code))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Independent phone and TV grid sizes (1–8 columns)
        item {
            SettingsGlassCard(
                icon = Icons.Default.GridOn,
                title = "Pengaturan Ukuran Grid",
                subtitle = "Default HP 2 kolom • Default TV 4 kolom"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(
                        Triple("Grid HP", settings.gridColumnsPhone, true),
                        Triple("Grid TV", settings.gridColumnsTv, false)
                    ).forEach { (label, selected, isPhone) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$label • $selected kolom", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..8).forEach { cols ->
                                    val isSelected = selected == cols
                                    Box(
                                        modifier = Modifier.size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (isPhone) viewModel.updateSettings(settings.copy(gridColumnsPhone = cols))
                                                else viewModel.updateSettings(settings.copy(gridColumnsTv = cols))
                                            }
                                            .tvFocusable(shape = RoundedCornerShape(12.dp), borderWidth = 1.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$cols", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Server PIN Configuration
        item {
            SettingsGlassCard(
                icon = Icons.Default.Lock,
                title = "Keamanan PIN Sumber Server",
                subtitle = "PIN terenkripsi untuk membuka menu upload dan kelola server"
            ) {
                Button(
                    onClick = { showServerPinDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ubah PIN keamanan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 7. App updates
        item {
            SettingsGlassCard(
                icon = Icons.Default.SystemUpdate,
                title = "Pembaruan Aplikasi",
                subtitle = "Versi terpasang: v${BuildConfig.VERSION_NAME}"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = updateMessage ?: "Periksa rilis resmi NSiO. APK akan divalidasi sebelum installer Android dibuka.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Lokasi APK: Penyimpanan internal/NSiO/Pembaruan Aplikasi", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!updateStorageAllowed) {
                        Button(onClick = { runCatching { context.startActivity(AppUpdateManager.storagePermissionIntent(context)) }.onFailure { updateMessage = "Tidak dapat membuka pengaturan izin penyimpanan." } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Izinkan akses folder NSiO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Riwayat unduhan APK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${updateHistory.size} file", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (updateHistory.isEmpty()) {
                        Text(if (updateStorageAllowed) "Belum ada APK pembaruan yang tersimpan." else "Riwayat akan tampil setelah izin penyimpanan diberikan.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else updateHistory.forEach { apk ->
                        Row(modifier = Modifier.fillMaxWidth().liquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.08f, borderAlpha = 0.2f, elevation = 1.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("NSiO v${apk.version}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${android.text.format.Formatter.formatShortFileSize(context, apk.sizeBytes)} • ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(apk.lastModified))}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = {
                                when (AppUpdateManager.installDownloadedApk(context, apk.fileName)) {
                                    AppUpdateManager.InstallResult.OPENED -> updateMessage = "Installer Android dibuka untuk v${apk.version}."
                                    AppUpdateManager.InstallResult.PERMISSION_REQUIRED -> updateMessage = "Izinkan NSiO memasang aplikasi tidak dikenal, lalu tekan Instal lagi."
                                    AppUpdateManager.InstallResult.INVALID_APK -> updateMessage = "APK tidak valid atau tanda tangan aplikasi tidak cocok."
                                    AppUpdateManager.InstallResult.FAILED -> updateMessage = "Gagal membuka installer APK."
                                }
                            }, enabled = updateStorageAllowed, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = "Instal APK", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (apk.isInstallable) "Instal" else "Rusak", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                if (AppUpdateManager.deleteDownloadedApk(context, apk.fileName)) {
                                    updateHistory = AppUpdateManager.listDownloadedApks(context)
                                    updateMessage = "APK v${apk.version} dihapus dari riwayat."
                                } else updateMessage = "APK tidak dapat dihapus. Periksa izin penyimpanan."
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus APK v${apk.version}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (updateRelease?.available == true) {
                        val release = updateRelease!!
                        Text(
                            text = "Versi terbaru tersedia: v${release.latestVersion} • ${if (release.assetSize > 0L) android.text.format.Formatter.formatShortFileSize(context, release.assetSize) else "Ukuran tidak diketahui"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (updateRelease != null) {
                        Text(
                            text = "NSiO sudah menggunakan versi terbaru (v${BuildConfig.VERSION_NAME}).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (updateDownloadId != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = updateProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Progres unduhan: $updateProgress%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = {
                            updateScope.launch {
                                updateChecking = true
                                updateMessage = "Memeriksa rilis GitHub..."
                                val result = runCatching { AppUpdateManager.checkLatest() }
                                result.onSuccess { release ->
                                    updateRelease = release
                                    if (release.available) {
                                        updateMessage = "Pembaruan v${release.latestVersion} tersedia."
                                        showUpdateDialog = true
                                    } else {
                                        showUpdateDialog = false
                                        updateMessage = "NSiO sudah menggunakan versi terbaru (v${release.currentVersion})."
                                    }
                                }.onFailure { error ->
                                    updateRelease = null
                                    updateMessage = "Gagal memeriksa update: ${error.localizedMessage ?: "koneksi bermasalah"}. Coba lagi."
                                }
                                updateChecking = false
                            }
                        },
                        enabled = !updateChecking && updateDownloadId == null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (updateChecking) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (updateChecking) "Memeriksa..." else "Cek Update", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 8. Developer Social Links
        item {
            SettingsGlassCard(
                icon = Icons.Default.Share,
                title = "Media Sosial & Komunitas",
                subtitle = "Ikuti kanal resmi pengembang"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val links = listOf(
                        Triple("GitHub", "https://github.com/andikune-ux", R.drawable.ic_brand_github),
                        Triple("WhatsApp", "https://chat.whatsapp.com/BAejUrfRW39FOt8biJleUM", R.drawable.ic_brand_whatsapp),
                        Triple("TikTok", "https://www.tiktok.com/@nayyra.une", R.drawable.ic_brand_tiktok),
                        Triple("Instagram", "https://www.instagram.com/nayyra.une", R.drawable.ic_brand_instagram)
                    )
                    links.forEach { (label, url, iconRes) ->
                        val tint = when (label) {
                            "WhatsApp" -> Color(0xFF25D366)
                            "Instagram" -> Color(0xFFE4405F)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                "Tidak dapat membuka tautan. Periksa aplikasi browser atau koneksi.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = label,
                                colorFilter = ColorFilter.tint(tint),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
            }
        }
    }
 
    if (showUpdateDialog && updateRelease?.available == true) {
        val release = updateRelease!!
        Dialog(onDismissRequest = {
            if (updateDownloadId == null) showUpdateDialog = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                }
                Text(
                    "PEMBARUAN TERSEDIA",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    "Versi baru: v${release.latestVersion}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Rincian Pembaruan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        release.notes.ifBlank { "Tidak ada rincian pembaruan." },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ukuran APK: ${if (release.assetSize > 0L) android.text.format.Formatter.formatShortFileSize(context, release.assetSize) else "Tidak diketahui"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    updateDownloadId != null -> {
                        Text("Mengunduh pembaruan... $updateProgress%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        androidx.compose.material3.LinearProgressIndicator(progress = updateProgress / 100f, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                updateDownloadId?.let { AppUpdateManager.cancelDownload(context, it) }
                                AppUpdateManager.clearPending(context)
                                updateDownloadId = null
                                updateVersion = null
                                updateProgress = 0
                                updateMessage = "Unduhan pembaruan dibatalkan."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("BATALKAN DOWNLOAD") }
                    }
                    readyToInstallVersion != null -> {
                        Button(
                            onClick = {
                                when (AppUpdateManager.install(context, readyToInstallVersion!!)) {
                                    AppUpdateManager.InstallResult.OPENED -> updateMessage = "Installer Android dibuka. Konfirmasi pemasangan untuk menyelesaikan update."
                                    AppUpdateManager.InstallResult.PERMISSION_REQUIRED -> updateMessage = "Aktifkan izin 'Instal aplikasi tidak dikenal' untuk NSiO, lalu tekan Instal lagi."
                                    AppUpdateManager.InstallResult.INVALID_APK -> updateMessage = "APK tidak valid atau tanda tangan aplikasi tidak cocok. Unduh ulang dari rilis resmi."
                                    AppUpdateManager.InstallResult.FAILED -> updateMessage = "Gagal membuka installer Android."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("INSTALL SEKARANG") }
                        Button(onClick = { showUpdateDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("TUTUP") }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    AppUpdateManager.skipVersion(context, release.latestVersion)
                                    showUpdateDialog = false
                                    updateMessage = "Pembaruan v${release.latestVersion} dilewati."
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                            ) { Text("SKIP", fontSize = 11.sp) }
                            Button(
                                onClick = { showUpdateDialog = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                            ) { Text("NANTI", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    runCatching { AppUpdateManager.startDownload(context, release) }
                                        .onSuccess { id ->
                                            updateDownloadId = id
                                            updateVersion = release.latestVersion
                                            readyToInstallVersion = null
                                            updateProgress = 0
                                            updateMessage = "Menyiapkan unduhan..."
                                        }
                                        .onFailure { updateMessage = "Gagal memulai unduhan: ${it.localizedMessage ?: "periksa izin penyimpanan"}" }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("UPDATE", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }

    if (showServerPinDialog) {
        ServerPinChangeDialog(
            currentPin = settings.serverPin.ifBlank { "123456" },
            onDismiss = { showServerPinDialog = false },
            onSave = { newPin ->
                viewModel.updateSettings(settings.copy(serverPin = newPin))
                showServerPinDialog = false
            }
        )
    }
}
 
@Composable
private fun ServerPinChangeDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (verified) "Buat PIN baru" else "Verifikasi PIN lama",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (verified) "Masukkan PIN baru 6 digit, lalu ulangi untuk konfirmasi."
                else "Masukkan PIN saat ini untuk membuka pengaturan keamanan.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!verified) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { value -> if (value.length <= currentPin.length && value.all(Char::isDigit)) oldPin = value },
                    label = { Text("PIN lama") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) newPin = value },
                    label = { Text("PIN baru (6 digit)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) confirmPin = value },
                    label = { Text("Ulangi PIN baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                Button(
                    onClick = {
                        if (!verified) {
                            if (oldPin == currentPin) {
                                verified = true
                                error = null
                            } else {
                                error = "PIN lama salah. Akses perubahan ditolak."
                                oldPin = ""
                            }
                        } else if (!newPin.matches(Regex("\\d{6}"))) {
                            error = "PIN baru harus tepat 6 digit."
                        } else if (newPin != confirmPin) {
                            error = "Konfirmasi PIN tidak cocok."
                            confirmPin = ""
                        } else if (newPin == currentPin) {
                            error = "Gunakan PIN yang berbeda dari PIN lama."
                        } else {
                            onSave(newPin)
                        }
                    },
                    enabled = if (verified) newPin.length == 6 && confirmPin.length == 6 else oldPin.length == currentPin.length,
                    modifier = Modifier.weight(1f)
                ) { Text(if (verified) "Simpan" else "Verifikasi") }
            }
        }
    }
}


@Composable
private fun SettingsChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SettingsGlassCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}