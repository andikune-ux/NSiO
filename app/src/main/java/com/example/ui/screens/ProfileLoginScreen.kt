package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Profile
import com.example.ui.MainViewModel

@Composable
fun ProfileLoginScreen(
    viewModel: MainViewModel,
    onLogin: () -> Unit
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    var pendingLoginProfileId by remember { mutableStateOf<String?>(null) }
    var pinProfile by remember { mutableStateOf<Profile?>(null) }
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showAddProfile by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileAvatar by remember { mutableStateOf("👤") }
    var newProfilePin by remember { mutableStateOf("") }
    var newProfilePinEnabled by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            newProfileAvatar = uri.toString()
        }
    }

    LaunchedEffect(activeProfile?.id, pendingLoginProfileId) {
        if (pendingLoginProfileId != null && activeProfile?.id == pendingLoginProfileId) {
            pendingLoginProfileId = null
            onLogin()
        }
    }

    fun selectProfile(profile: Profile) {
        pendingLoginProfileId = profile.id
        viewModel.switchProfile(profile)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("NSiO", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Pilih Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pilih akun yang ingin digunakan. Data baru ditampilkan setelah profil berhasil dibuka.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (profiles.isEmpty()) {
                CircularProgressIndicator()
                Text("Memuat daftar profil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (profile.isPinEnabled) {
                                    pinProfile = profile
                                    pinValue = ""
                                    pinError = null
                                } else {
                                    selectProfile(profile)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(54.dp).background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profile.avatarEmoji.startsWith("content://") ||
                                        profile.avatarEmoji.startsWith("https://") ||
                                        profile.avatarEmoji.startsWith("file://")
                                    ) {
                                        AsyncImage(
                                            model = profile.avatarEmoji,
                                            contentDescription = "Foto profil ${profile.name}",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(profile.avatarEmoji, style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (profile.isPinEnabled) "Profil terkunci • PIN diperlukan" else "Tanpa PIN • langsung masuk",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (profile.isPinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (profile.isPinEnabled) "Terkunci" else "Tidak terkunci",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddProfile = true },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tambah Profil")
                        }
                    }
                }
            }
        }
    }

    pinProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pinProfile = null; pinValue = ""; pinError = null },
            title = { Text("PIN ${profile.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Masukkan PIN profil untuk melanjutkan.")
                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all(Char::isDigit)) {
                                pinValue = value
                                pinError = null
                            }
                        },
                        label = { Text("PIN 4 digit") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(enabled = pinValue.length == 4, onClick = {
                    if (pinValue == profile.pin) {
                        pinProfile = null
                        pinValue = ""
                        pinError = null
                        selectProfile(profile)
                    } else {
                        pinValue = ""
                        pinError = "PIN tidak sesuai. Coba lagi."
                    }
                }) { Text("Buka Profil") }
            },
            dismissButton = {
                TextButton(onClick = { pinProfile = null; pinValue = ""; pinError = null }) { Text("Batal") }
            }
        )
    }

    if (showAddProfile) {
        AlertDialog(
            onDismissRequest = { showAddProfile = false },
            title = { Text("Tambah Profil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                        if (newProfileAvatar.startsWith("content://") || newProfileAvatar.startsWith("file://")) {
                            AsyncImage(model = newProfileAvatar, contentDescription = "Foto profil baru", modifier = Modifier.size(36.dp).clip(CircleShape))
                        } else Text(newProfileAvatar, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih ikon dari galeri")
                    }
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Nama profil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newProfilePinEnabled, onCheckedChange = {
                            newProfilePinEnabled = it
                            if (!it) newProfilePin = ""
                        })
                        Text("Kunci profil dengan PIN")
                    }
                    if (newProfilePinEnabled) {
                        OutlinedTextField(
                            value = newProfilePin,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newProfilePin = it },
                            label = { Text("PIN 4 digit") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newProfileName.isNotBlank() && (!newProfilePinEnabled || newProfilePin.length == 4),
                    onClick = {
                        viewModel.addProfile(newProfileName.trim(), newProfileAvatar, newProfilePin, newProfilePinEnabled, switchToNewProfile = false)
                        newProfileName = ""
                        newProfileAvatar = "👤"
                        newProfilePin = ""
                        newProfilePinEnabled = false
                        showAddProfile = false
                    }
                ) { Text("Simpan Profil") }
            },
            dismissButton = { TextButton(onClick = { showAddProfile = false }) { Text("Batal") } }
        )
    }
}
