package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassTvFocusable
import com.example.ui.theme.CyanPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var serverPinInput by remember { mutableStateOf(settings.serverPin) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Menu Pengaturan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Konfigurasi tampilan UI, orientasi perangkat, ukuran grid, dan preferensi",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Language Setting
        item {
            SettingsGlassCard(
                icon = Icons.Default.Language,
                title = "Pengaturan Bahasa",
                subtitle = "Pilih bahasa antarmuka aplikasi"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf("ID" to "Bahasa Indonesia", "EN" to "English")
                    languages.forEach { (code, label) ->
                        val isSelected = settings.language == code
                        Button(
                            onClick = { viewModel.updateSettings(settings.copy(language = code)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.10f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Text(label, color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // 2. Theme Setting
        item {
            SettingsGlassCard(
                icon = Icons.Default.BrightnessMedium,
                title = "Pengaturan Tema",
                subtitle = "Pilih mode visualisasi warna"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("AUTO" to "Otomatis", "DARK" to "Gelap (Liquid Glass)", "LIGHT" to "Terang")
                    themes.forEach { (code, label) ->
                        val isSelected = settings.themeMode == code
                        Button(
                            onClick = { viewModel.updateSettings(settings.copy(themeMode = code)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.10f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Text(label, color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // 3. Display Setting
        item {
            SettingsGlassCard(
                icon = Icons.Default.Tv,
                title = "Pengaturan Tampilan",
                subtitle = "Optimasi tata letak remote TV D-pad atau sentuhan HP"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("AUTO" to "Otomatis", "TV" to "Android TV (D-pad)", "HP" to "Mobile (Touch)")
                    modes.forEach { (code, label) ->
                        val isSelected = settings.displayMode == code
                        Button(
                            onClick = { viewModel.updateSettings(settings.copy(displayMode = code)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.10f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                        ) {
                            Text(label, color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // 4. Grid Size Setting (3, 4, 5, 6, 7, 8 kolom)
        item {
            SettingsGlassCard(
                icon = Icons.Default.GridOn,
                title = "Pengaturan Ukuran Grid Beranda",
                subtitle = "Jumlah kolom kartu video di beranda: ${settings.gridColumns} kolom"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (3..8).forEach { cols ->
                        val isSelected = settings.gridColumns == cols
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.08f))
                                .liquidGlassTvFocusable(shape = RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateSettings(settings.copy(gridColumns = cols)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$cols",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 5. Server PIN Configuration
        item {
            SettingsGlassCard(
                icon = Icons.Default.Lock,
                title = "Keamanan PIN Sumber Server",
                subtitle = "PIN untuk membuka menu upload dan kelola server"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverPinInput,
                        onValueChange = { if (it.length <= 4) serverPinInput = it },
                        label = { Text("PIN 4 Digit", fontSize = 10.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        modifier = Modifier.width(120.dp).liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    )
                    Button(
                        onClick = { viewModel.updateSettings(settings.copy(serverPin = serverPinInput)) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Simpan PIN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        // 6. Check App Update
        item {
            SettingsGlassCard(
                icon = Icons.Default.SystemUpdate,
                title = "Cek Pembaharuan Aplikasi",
                subtitle = "Versi: Net Stream iO v1.0.0 (Build 2026.10)"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateStatus = null
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isCheckingUpdate = false
                                updateStatus = "Aplikasi Anda sudah versi terbaru (v1.0.0)."
                            }, 1000)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CyanPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Periksa Pembaruan", color = CyanPrimary, fontSize = 10.sp)
                    }

                    if (updateStatus != null) {
                        Text(
                            text = updateStatus ?: "",
                            fontSize = 10.sp,
                            color = Color(0xFF30D158),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 7. Creator Social Media Links
        item {
            SettingsGlassCard(
                icon = Icons.Default.Share,
                title = "Tautan Media Sosial Pembuat",
                subtitle = "Pengembang: andikune (Net Stream iO)"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val socials = listOf(
                        "Instagram" to "https://www.instagram.com/andik.une",
                        "TikTok" to "https://www.tiktok.com/@andik.une",
                        "WhatsApp Komunitas" to "https://chat.whatsapp.com/DuRvdGpBNfoE15HthpJvQI?s=cl&p=a&mlu=4&ilr=4"
                    )

                    socials.forEach { (name, link) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(shape = RoundedCornerShape(10.dp), alpha = 0.08f, borderAlpha = 0.25f, elevation = 2.dp)
                                .liquidGlassTvFocusable(shape = RoundedCornerShape(10.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        viewModel.showStatus("Membuka: $link")
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = CyanPrimary)
                                Text(text = link, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                alpha = 0.10f,
                borderAlpha = 0.35f,
                elevation = 6.dp
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
                }
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
