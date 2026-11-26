package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text as Material3Text
import com.example.ui.components.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.firebase.AuthState
import com.example.data.model.Profile
import com.example.ui.MainViewModel
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentGreen

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isSyncing by viewModel.isCloudSyncing.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingSwitchProfile by remember { mutableStateOf<Profile?>(null) }
    var showPinDialogForSwitch by remember { mutableStateOf(false) }
    var pinResetProfile by remember { mutableStateOf<Profile?>(null) }
    var pinManagementProfile by remember { mutableStateOf<Profile?>(null) }
    var pinManagementAction by remember { mutableStateOf("CHANGE") }
    var pinDisableProfile by remember { mutableStateOf<Profile?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var requestedRestoreType by remember { mutableStateOf("PROFILE_BACKUP") }
    var pendingEncryptedBackup by remember { mutableStateOf<String?>(null) }
    var backupPinInput by remember { mutableStateOf("") }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var profileSettingsTarget by remember { mutableStateOf<Profile?>(null) }
    var editingProfileName by remember { mutableStateOf("") }
    var editingProfileAvatar by remember { mutableStateOf("") }

    val context = LocalContext.current
    val restoreScope = androidx.compose.runtime.rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            editingProfileAvatar = uri.toString()
        }
    }
    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            if (!isBackupInProgress) {
                isBackupInProgress = true
                restoreScope.launch {
                    try {
                        val jsonContent = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8 * 1024)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 64 * 1024 * 1024) {
                                        "File backup melebihi batas 64 MB."
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.toString(Charsets.UTF_8.name())
                            } ?: throw IllegalArgumentException("File tidak dapat dibuka.")
                        }
                        val actualBackupType = org.json.JSONObject(
                            com.example.data.backup.BackupAmanManager.extractJson(jsonContent)
                        ).optString("backupType")
                        val encryptedProfileBackup =
                            requestedRestoreType == "PROFILE_BACKUP" &&
                                actualBackupType == "PROFILE_BACKUP_ENCRYPTED"
                        require(actualBackupType == requestedRestoreType || encryptedProfileBackup) {
                            if (requestedRestoreType == "FULL_BACKUP_AMAN") {
                                "File yang dipilih bukan Backup Aman. Pilih file TXT dari folder Backup Aman."
                            } else {
                                "File yang dipilih bukan Backup Profil. Pilih file JSON dari folder Backup Profil."
                            }
                        }
                        if (encryptedProfileBackup) {
                            pendingEncryptedBackup = jsonContent
                            backupPinInput = ""
                            isBackupInProgress = false
                        } else {
                            viewModel.restoreBackup(jsonContent) { res ->
                                backupStatusMessage = res.message
                                isBackupInProgress = false
                            }
                        }
                    } catch (e: Exception) {
                        backupStatusMessage = "Gagal membaca file backup: ${e.localizedMessage ?: "file tidak valid"}"
                        isBackupInProgress = false
                    }
                }
            }
        }
    }

    fun openRestorePicker(backupType: String) {
        if (isBackupInProgress) return
        requestedRestoreType = backupType
        val isFullBackup = backupType == "FULL_BACKUP_AMAN"
        val documentId = when {
            isFullBackup && com.example.data.backup.BackupAmanManager.hasStorageAccess(context) ->
                "primary:NSiO/backup aman"
            isFullBackup ->
                "primary:Android/data/${context.packageName}/files/NSiO/backup aman"
            com.example.data.backup.BackupAmanManager.hasStorageAccess(context) ->
                "primary:NSiO/Backup Profil"
            else ->
                "primary:Android/data/${context.packageName}/files/NSiO/Backup Profil"
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    "android.provider.extra.INITIAL_URI",
                    Uri.parse("content://com.android.externalstorage.documents/document/${Uri.encode(documentId)}")
                )
            }
        }
        restorePicker.launch(intent)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Menu Profil",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Multi-User, Proteksi PIN, Cloud Firestore & Backup Terpadu",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showAccountSwitcher = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Tambah atau beralih akun",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Cloud Google Account Card
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Akun Cloud (Firebase Firestore)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Penyimpanan cloud untuk semua profil & media streaming",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val state = authState) {
                        is AuthState.Authenticated -> {
                            val user = state.user
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!user.photoUrl?.toString().isNullOrEmpty()) {
                                    AsyncImage(
                                        model = user.photoUrl.toString(),
                                        contentDescription = "Avatar Pengguna",
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (user.displayName ?: "U").take(1).uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: "Pengguna Google",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user.email ?: user.uid,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { viewModel.syncToCloud() },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sync", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        is AuthState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Menghubungkan ke akun Google...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.signInWithGoogle() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Hubungkan Akun Google (Cloud Firestore)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Profile identities stay at the top, above cloud and backup settings.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Akun Profil",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(profiles, key = { it.id }) { p ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .width(76.dp)
                                .clickable {
                                    profileSettingsTarget = p
                                    editingProfileName = p.name
                                    editingProfileAvatar = p.avatarEmoji
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeProfile?.id == p.id)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        if (activeProfile?.id == p.id) 2.dp else 1.dp,
                                        if (activeProfile?.id == p.id) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.avatarEmoji.startsWith("content://") ||
                                    p.avatarEmoji.startsWith("https://") ||
                                    p.avatarEmoji.startsWith("file://")
                                ) {
                                    AsyncImage(
                                        model = p.avatarEmoji,
                                        contentDescription = "Avatar ${p.name}",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(p.avatarEmoji, fontSize = 24.sp)
                                }
                            }
                            Text(
                                p.name,
                                fontSize = 10.sp,
                                fontWeight = if (activeProfile?.id == p.id) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeProfile?.id == p.id) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Backup & Restore
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cadangan Data (Backup & Restore):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Backup Biasa Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        alpha = 0.10f,
                        borderAlpha = 0.30f,
                        elevation = 6.dp
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "1. Backup Biasa (Per-Profil)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Mem-backup daftar link dan pengaturan khusus profil '${activeProfile?.name ?: ""}' ke file JSON lokal.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!isBackupInProgress) {
                                isBackupInProgress = true
                                viewModel.runProfileBackup { res ->
                                    backupStatusMessage = res.message
                                    isBackupInProgress = false
                                }
                            }
                        },
                        enabled = !isBackupInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            if (isBackupInProgress) "Backup sedang berjalan..." else "Mulai Backup Profil Ini",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Full Backup Aman Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        alpha = 0.10f,
                        borderAlpha = 0.30f,
                        elevation = 6.dp
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "2. Backup Aman (Full Backup Keseluruhan)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Mem-backup semua profil, database media, pengaturan, dan histori. PIN profil dan PIN server tidak disertakan demi keamanan; setelah restore, atur ulang PIN yang diperlukan.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!isBackupInProgress) {
                                isBackupInProgress = true
                                viewModel.runFullBackupAman { res ->
                                    backupStatusMessage = res.message
                                    isBackupInProgress = false
                                }
                            }
                        },
                        enabled = !isBackupInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            if (isBackupInProgress) "Backup sedang berjalan..." else "Buat Full Backup Terpadu",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Restore actions open the matching backup folder directly.
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        alpha = 0.10f,
                        borderAlpha = 0.30f,
                        elevation = 6.dp
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "3. Pulihkan Backup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Setiap tombol langsung membuka folder sesuai jenis backup. File dengan jenis yang tidak sesuai akan ditolak.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { openRestorePicker("FULL_BACKUP_AMAN") },
                        enabled = !isBackupInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isBackupInProgress) "Memulihkan..." else "Pulihkan Backup Aman",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { openRestorePicker("PROFILE_BACKUP") },
                        enabled = !isBackupInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isBackupInProgress) "Memulihkan..." else "Pulihkan Backup Profil",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (backupStatusMessage != null) {
            item {
                Text(
                    text = backupStatusMessage ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (pendingEncryptedBackup != null) {
        Dialog(onDismissRequest = {
            if (!isBackupInProgress) {
                pendingEncryptedBackup = null
                backupPinInput = ""
            }
        }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(24.dp),
                        alpha = 0.18f,
                        borderAlpha = 0.45f,
                        elevation = 16.dp
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Backup Profil Terkunci",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Masukkan PIN profil sumber yang digunakan saat backup dibuat. PIN profil penerima tidak digunakan untuk membuka file ini.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPinInput,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all(Char::isDigit)) backupPinInput = value
                        },
                        label = { Text("PIN profil sumber") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                pendingEncryptedBackup = null
                                backupPinInput = ""
                            },
                            enabled = !isBackupInProgress,
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) { Text("Batal", color = MaterialTheme.colorScheme.onSurface) }
                        Button(
                            onClick = {
                                val content = pendingEncryptedBackup ?: return@Button
                                isBackupInProgress = true
                                viewModel.restoreBackup(content, backupPinInput) { res ->
                                    backupStatusMessage = res.message
                                    isBackupInProgress = false
                                    if (res.isSuccess) {
                                        pendingEncryptedBackup = null
                                        backupPinInput = ""
                                    }
                                }
                            },
                            enabled = !isBackupInProgress && backupPinInput.matches(Regex("\\d{4}")),
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isBackupInProgress) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            else Text("Buka & Pulihkan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAccountSwitcher) {
        Dialog(onDismissRequest = { showAccountSwitcher = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(24.dp),
                        alpha = 0.18f,
                        borderAlpha = 0.45f,
                        elevation = 16.dp
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pilih atau tambah akun", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                profiles.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showAccountSwitcher = false
                                if (activeProfile?.id == p.id) return@clickable
                                if (p.isPinEnabled && p.pin.matches(Regex("\\d{4}"))) {
                                    pendingSwitchProfile = p
                                    showPinDialogForSwitch = true
                                } else if (p.isPinEnabled) {
                                    viewModel.showStatus("PIN profil ini belum valid. Atur ulang PIN sebelum beralih.")
                                } else {
                                    viewModel.switchProfile(p)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (p.avatarEmoji.startsWith("content://") || p.avatarEmoji.startsWith("https://")) {
                                AsyncImage(model = p.avatarEmoji, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            } else Text(p.avatarEmoji, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (p.isPinEnabled) "PIN Dilindungi" else "Bebas PIN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activeProfile?.id == p.id) Icon(Icons.Default.Lock, contentDescription = "Aktif", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Button(
                    onClick = {
                        showAccountSwitcher = false
                        showAddDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah Profil")
                }
            }
        }
    }

    if (profileSettingsTarget != null) {
        val target = profileSettingsTarget!!
        Dialog(
            onDismissRequest = { profileSettingsTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Pengaturan Profil", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                    IconButton(onClick = { profileSettingsTarget = null }) {
                        Icon(Icons.Default.Add, contentDescription = "Tutup", modifier = Modifier.size(22.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { avatarPicker.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (editingProfileAvatar.startsWith("content://") ||
                        editingProfileAvatar.startsWith("https://") ||
                        editingProfileAvatar.startsWith("file://")
                    ) {
                        AsyncImage(model = editingProfileAvatar, contentDescription = "Avatar profil", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Text(editingProfileAvatar.ifBlank { "👤" }, fontSize = 42.sp)
                    }
                }
                Text("Tekan gambar untuk memilih Avatar dari galeri", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                OutlinedTextField(
                    value = editingProfileName,
                    onValueChange = { editingProfileName = it },
                    label = { Text("Nama profil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.updateProfileDetails(target, editingProfileName, editingProfileAvatar)
                        profileSettingsTarget = null
                    },
                    enabled = editingProfileName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Simpan Profil") }
                androidx.compose.material3.HorizontalDivider()
                Text("Keamanan Profil", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Button(
                    onClick = {
                        if (target.isPinEnabled && target.pin.matches(Regex("\\d{4}"))) {
                            pinManagementProfile = target
                            pinManagementAction = "CHANGE"
                        } else if (target.isPinEnabled) {
                            viewModel.showStatus("PIN profil tidak dapat diverifikasi. Pulihkan akses PIN terlebih dahulu.")
                        } else pinResetProfile = target
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text(if (target.isPinEnabled) "Ubah PIN" else "Atur PIN") }
                if (target.isPinEnabled) {
                    Button(
                        onClick = {
                            if (target.pin.matches(Regex("\\d{4}"))) {
                                pinManagementProfile = target
                                pinManagementAction = "DISABLE"
                            } else viewModel.showStatus("PIN profil tidak dapat diverifikasi.")
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) { Text("Nonaktifkan PIN", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // Add Profile Dialog
    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, avatar, pin, isPinEnabled ->
                viewModel.addProfile(name, avatar, pin, isPinEnabled)
                showAddDialog = false
            }
        )
    }

    if (pinResetProfile != null) {
        PinResetDialog(
            profileName = pinResetProfile?.name.orEmpty(),
            onDismiss = { pinResetProfile = null },
            onSave = { pin ->
                pinResetProfile?.let { viewModel.updateProfilePin(it, pin) }
                pinResetProfile = null
            }
        )
    }

    // Verify the existing PIN before changing or disabling protection.
    if (pinManagementProfile != null) {
        val target = pinManagementProfile!!
        PinDialog(
            title = "Verifikasi PIN",
            subtitle = "Masukkan PIN saat ini untuk profil '${target.name}'.",
            expectedPin = target.pin,
            onSuccess = {
                if (pinManagementAction == "CHANGE") pinResetProfile = target
                else pinDisableProfile = target
                pinManagementProfile = null
            },
            onDismiss = { pinManagementProfile = null }
        )
    }

    if (pinDisableProfile != null) {
        Dialog(onDismissRequest = { pinDisableProfile = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.18f, borderAlpha = 0.45f, elevation = 16.dp)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nonaktifkan PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Profil '${pinDisableProfile?.name}' tidak akan meminta PIN saat dipilih atau saat NSiO dibuka.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { pinDisableProfile = null }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                pinDisableProfile?.let { viewModel.disableProfilePin(it) }
                                pinDisableProfile = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Nonaktifkan", color = MaterialTheme.colorScheme.onError) }
                    }
                }
            }
        }
    }

    // Pin Dialog for switching
    if (showPinDialogForSwitch && pendingSwitchProfile != null) {
        PinDialog(
            title = "Verifikasi PIN Profil",
            subtitle = "Masukkan PIN untuk beralih ke profil '${pendingSwitchProfile?.name}'",
            expectedPin = pendingSwitchProfile?.pin ?: "",
            onSuccess = {
                pendingSwitchProfile?.let { viewModel.switchProfile(it) }
                pendingSwitchProfile = null
                showPinDialogForSwitch = false
            },
            onDismiss = {
                pendingSwitchProfile = null
                showPinDialogForSwitch = false
            }
        )
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, avatar: String, pin: String, isPinEnabled: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🎬") }
    var pin by remember { mutableStateOf("") }
    var isPinEnabled by remember { mutableStateOf(false) }

    val avatars = listOf("👑", "🎬", "🍿", "🚀", "🎮", "👾", "🦊", "⭐")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    alpha = 0.18f,
                    borderAlpha = 0.45f,
                    elevation = 16.dp
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tambah Profil Baru",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nama Profil...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pilih Avatar:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    avatars.take(5).forEach { emoji ->
                        val isSelected = selectedAvatar == emoji
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .clickable { selectedAvatar = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Kunci dengan PIN?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { isPinEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (isPinEnabled) {
                    Text(
                        text = "PIN wajib tepat 4 digit angka.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value -> if (value.length <= 4 && value.all(Char::isDigit)) pin = value },
                        placeholder = { Text("PIN 4 Digit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Batal", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && (!isPinEnabled || pin.matches(Regex("\\d{4}")))) {
                                onConfirm(name.trim(), selectedAvatar, pin, isPinEnabled)
                            }
                        },
                        enabled = name.isNotBlank() && (!isPinEnabled || pin.matches(Regex("\\d{4}"))),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Simpan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun PinResetDialog(
    profileName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    alpha = 0.18f,
                    borderAlpha = 0.45f,
                    elevation = 16.dp
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Atur Ulang PIN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Buat PIN baru untuk profil '$profileName'.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmation = it },
                    label = { Text("Ulangi PIN baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))
                    ) { Text("Batal", color = MaterialTheme.colorScheme.onSurface) }
                    Button(
                        onClick = { onSave(pin) },
                        enabled = pin.matches(Regex("\\d{4}")) && pin == confirmation,
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Simpan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
