package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.Profile
import com.example.ui.MainViewModel

@Composable
fun ProfileLoginScreen(
    viewModel: MainViewModel,
    onLogin: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    var pendingLoginProfileId by remember { mutableStateOf<String?>(null) }
    var pinProfile by remember { mutableStateOf<Profile?>(null) }
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

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
                "Pilih profil untuk melanjutkan ke beranda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (profiles.isEmpty()) {
                Text("Memuat profil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    modifier = Modifier.size(48.dp).background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        CircleShape
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(profile.avatarEmoji, style = MaterialTheme.typography.headlineSmall)
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
                }
            }
        }
    }

    pinProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                pinProfile = null
                pinValue = ""
                pinError = null
            },
            title = { Text("PIN ${profile.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Masukkan PIN untuk membuka profil ini.")
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
                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = pinValue.length == 4,
                    onClick = {
                        if (pinValue == profile.pin) {
                            pinProfile = null
                            pinValue = ""
                            pinError = null
                            selectProfile(profile)
                        } else {
                            pinValue = ""
                            pinError = "PIN tidak sesuai. Coba lagi."
                        }
                    }
                ) { Text("Buka Profil") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinProfile = null
                    pinValue = ""
                    pinError = null
                }) { Text("Batal") }
            }
        )
    }
}
