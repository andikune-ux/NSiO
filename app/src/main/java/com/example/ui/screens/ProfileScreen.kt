package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.data.model.Profile
import com.example.ui.MainViewModel
import com.example.ui.components.PinDialog
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CyanPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingSwitchProfile by remember { mutableStateOf<Profile?>(null) }
    var showPinDialogForSwitch by remember { mutableStateOf(false) }

    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = "Menu Profil (Multi-User & Keamanan)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Kelola akun mandiri, proteksi PIN, dan backup data terpadu",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah Profil", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        // Section 1: Profil Tersedia
        item {
            Text(
                text = "Daftar Profil Aktif & Tersedia:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(profiles, key = { it.id }) { p ->
            val isActive = activeProfile?.id == p.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(14.dp),
                        alpha = if (isActive) 0.16f else 0.08f,
                        borderAlpha = if (isActive) 0.45f else 0.25f,
                        elevation = 4.dp
                    )
                    .liquidGlassTvFocusable(shape = RoundedCornerShape(14.dp))
                    .clickable {
                        if (!isActive) {
                            if (p.isPinEnabled && p.pin.isNotEmpty()) {
                                pendingSwitchProfile = p
                                showPinDialogForSwitch = true
                            } else {
                                viewModel.switchProfile(p)
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isActive) CyanPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                .border(
                                    1.dp,
                                    if (isActive) CyanPrimary else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = p.avatarEmoji, fontSize = 18.sp)
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = p.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentGreen)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("Aktif", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (p.isPinEnabled) {
                                    Icon(Icons.Default.Lock, contentDescription = "PIN", tint = CyanPrimary, modifier = Modifier.size(11.dp))
                                    Text("PIN Aktif", fontSize = 9.sp, color = CyanPrimary)
                                } else {
                                    Text("Bebas PIN", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (!isActive) {
                        Button(
                            onClick = {
                                if (p.isPinEnabled && p.pin.isNotEmpty()) {
                                    pendingSwitchProfile = p
                                    showPinDialogForSwitch = true
                                } else {
                                    viewModel.switchProfile(p)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(8.dp))
                        ) {
                            Text("Beralih", color = CyanPrimary, fontSize = 10.sp)
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Backup Biasa Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                        Text("1. Backup Biasa (Per-Profil)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "Mem-backup daftar link dan pengaturan khusus profil '${activeProfile?.name ?: ""}' ke NSiO/Backup.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.runProfileBackup { res ->
                                backupStatusMessage = res.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Jalankan Backup Profil", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        // Backup Aman Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(14.dp), alpha = 0.10f, borderAlpha = 0.35f, elevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(16.dp))
                        Text("2. Backup Aman (Full Backup Keseluruhan)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "Mem-backup seluruh isi aplikasi secara utuh (semua profil, riwayat, video, dan settings) ke NSiO/Backup Aman.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.runFullBackupAman { res ->
                                backupStatusMessage = res.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Buat Full Backup Aman", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        if (backupStatusMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = RoundedCornerShape(10.dp), alpha = 0.15f, borderAlpha = 0.4f)
                        .padding(10.dp)
                ) {
                    Text(
                        text = backupStatusMessage ?: "",
                        fontSize = 10.sp,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showPinDialogForSwitch && pendingSwitchProfile != null) {
        val target = pendingSwitchProfile!!
        PinDialog(
            title = "Proteksi Profil '${target.name}'",
            subtitle = "Profil ini dilindungi PIN. Masukkan PIN untuk beralih.",
            expectedPin = target.pin,
            onSuccess = {
                viewModel.switchProfile(target)
                showPinDialogForSwitch = false
                pendingSwitchProfile = null
            },
            onDismiss = {
                showPinDialogForSwitch = false
                pendingSwitchProfile = null
            }
        )
    }

    if (showAddDialog) {
        AddProfileGlassDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, emoji, pin, pinEnabled ->
                viewModel.addProfile(name, emoji, pin, pinEnabled)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddProfileGlassDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎬") }
    var pin by remember { mutableStateOf("") }
    var isPinEnabled by remember { mutableStateOf(false) }

    val emojis = listOf("🎬", "🍿", "🚀", "🎮", "👑", "🌟", "🔥", "🦄")

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(20.dp),
                    alpha = 0.16f,
                    borderAlpha = 0.45f,
                    elevation = 16.dp
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Tambah Profil Baru", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Profil", fontSize = 10.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Pilih Avatar Emoji:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    emojis.forEach { emoji ->
                        val isSel = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isSel) CyanPrimary else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isSel) CyanPrimary else Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Proteksi PIN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { isPinEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary)
                    )
                }

                if (isPinEnabled) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        label = { Text("PIN 4 Digit", fontSize = 10.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.fillMaxWidth().liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name, selectedEmoji, pin, isPinEnabled)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
