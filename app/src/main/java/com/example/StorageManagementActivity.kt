package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.cache.AppCacheCleaner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android's manageSpaceActivity entry point. It deliberately does not create
 * MainViewModel, restore a session, select a profile, or request a profile PIN.
 */
class StorageManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme(themeMode = "system") {
                var clearing by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Kelola Penyimpanan NSiO",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Halaman pemeliharaan ini tidak membuka profil, tidak memilih akun, dan tidak meminta PIN profil.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Yang dapat dibersihkan dengan aman",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "• Cache media sementara dari pemutaran Drive dan Telegram.\n" +
                                "• Cache media Telegram lama jika masih ada.\n\n" +
                                "Yang tetap dipertahankan: profil, PIN, playlist dan tautan sumber, " +
                                "pengaturan, login Firebase, kredensial Telegram, sesi TDLib, serta file pembaruan APK.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = {
                                if (!clearing) {
                                    clearing = true
                                    message = null
                                    lifecycleScope.launch {
                                        val success = withContext(Dispatchers.IO) {
                                            AppCacheCleaner.clear(this@StorageManagementActivity)
                                        }
                                        message = if (success) {
                                            "Cache media berhasil dibersihkan. Data profil dan akun tetap dipertahankan."
                                        } else {
                                            "Sebagian cache gagal dibersihkan. Data profil dan akun tidak diubah."
                                        }
                                        clearing = false
                                    }
                                }
                            },
                            enabled = !clearing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (clearing) {
                                CircularProgressIndicator()
                            } else {
                                Text("BERSIHKAN CACHE AMAN")
                            }
                        }

                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { finish() },
                            enabled = !clearing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("KEMBALI")
                        }

                        Text(
                            text = "Penghapusan seluruh data aplikasi tidak disediakan di halaman ini agar profil dan akun tidak terhapus tanpa sengaja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
