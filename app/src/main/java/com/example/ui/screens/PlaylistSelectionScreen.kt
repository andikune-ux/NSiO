package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppText as Text
import com.example.ui.components.liquidGlass

@Composable
fun PlaylistSelectionScreen(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSourceManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.sourcePlaylists.collectAsState()
    val mediaItems by viewModel.mediaList.collectAsState()
    val repos by viewModel.pluginManager.repositories.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val telegramPlaylists = remember(playlists) { playlists.filter { it.sourceType.startsWith("TELEGRAM", true) }.sortedBy { it.name.lowercase() } }
    val drivePlaylists = remember(playlists, mediaItems) {
        playlists.filter { playlist ->
            playlist.sourceType.equals("DRIVE", ignoreCase = true) ||
                com.example.data.model.MediaSourceClassifier.isGoogleDriveUrl(playlist.url) ||
                mediaItems.any { item ->
                    item.playlistId == playlist.id &&
                        com.example.data.model.MediaSourceClassifier.isDriveMedia(item)
                }
        }.sortedBy { it.name.lowercase() }
    }
    val enabledPlugins = remember(repos) {
        repos.flatMap { repo ->
            repo.plugins.filter { entry ->
                entry.status != 0 && viewModel.pluginManager.isCloudStreamPluginInstalled(entry)
            }.map { entry -> viewModel.pluginManager.cloudStreamPluginId(entry) to entry.name }
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp).testTag("playlist_selection_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Daftar Playlist", fontSize = 19.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                Text("Pilih playlist atau plugin yang ingin ditampilkan di Beranda.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().liquidGlass(shape = RoundedCornerShape(18.dp), alpha = 0.12f, borderAlpha = 0.28f, elevation = 3.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Playlist tersimpan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { viewModel.selectFilter("ALL_SOURCES"); onNavigateToHome() }, modifier = Modifier.fillMaxWidth().testTag("playlist_all_combined")) {
                    Text("Tampilkan Drive + CloudStream + Telegram", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.selectFilter("DRIVE_ALL"); onNavigateToHome() },
                    modifier = Modifier.fillMaxWidth().testTag("playlist_all_drive")
                ) {
                    Text("Tampilkan Semua Playlist Drive (${drivePlaylists.size})", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { viewModel.selectFilter("TELEGRAM_ALL"); onNavigateToHome() }, modifier = Modifier.fillMaxWidth().testTag("playlist_all_telegram")) {
                    Text("Tampilkan Semua Telegram (${telegramPlaylists.size})", fontSize = 11.sp)
                }
                telegramPlaylists.forEach { playlist ->
                    val filter = "TELEGRAM_SOURCE:${playlist.id}"
                    val active = selectedFilter == filter
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).clickable { viewModel.selectFilter(filter); onNavigateToHome() }.padding(horizontal = 10.dp, vertical = 10.dp).testTag("playlist_telegram_${playlist.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.size(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val importedCount = mediaItems.count { it.playlistId == playlist.id }
                            val sourceLabel = if (playlist.sourceType == "TELEGRAM_POST") "Postingan Telegram" else "Channel / Grup Telegram"
                            Text(
                                if (importedCount == 0) "$sourceLabel • belum ada media" else "$sourceLabel • $importedCount media",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (active) Icon(Icons.Default.LockOpen, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                if (playlists.isEmpty()) {
                    Text("Belum ada playlist atau sumber tersimpan. Tambahkan sumber melalui Manajemen Sumber.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    playlists.filterNot { it.sourceType.startsWith("TELEGRAM", true) }.forEach { playlist ->
                        val filter = "PLAYLIST:${playlist.id}"
                        val active = selectedFilter == filter
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable { viewModel.selectFilter(filter); onNavigateToHome() }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("playlist_saved_${playlist.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.size(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.itemCount} media", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (active) Icon(Icons.Default.LockOpen, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().liquidGlass(shape = RoundedCornerShape(18.dp), alpha = 0.12f, borderAlpha = 0.28f, elevation = 3.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Plugin aktif", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Hanya plugin yang sudah terpasang dan diaktifkan tersedia untuk dipilih.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { viewModel.selectFilter("CLOUDSTREAM_ALL"); onNavigateToHome() }, modifier = Modifier.fillMaxWidth().testTag("playlist_all_plugins")) {
                    Text("Tampilkan Semua Plugin (${enabledPlugins.size})", fontSize = 11.sp)
                }
                if (enabledPlugins.isEmpty()) {
                    Text("Belum ada plugin aktif. Aktifkan plugin melalui Manajemen Sumber → Plugin & Repo.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    enabledPlugins.forEach { (pluginId, pluginName) ->
                        val filter = "CLOUDSTREAM_PLUGIN:$pluginId"
                        val active = selectedFilter == filter
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable { viewModel.selectFilter(filter); onNavigateToHome() }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("playlist_plugin_$pluginId"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(9.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(pluginName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val videoCount = mediaItems.count { item ->
                                    item.sourceType.equals("CLOUDSTREAM", ignoreCase = true) &&
                                        item.pluginId.equals(pluginId, ignoreCase = true)
                                }
                                Text("$videoCount video", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (active) Icon(Icons.Default.LockOpen, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onNavigateToSourceManagement, modifier = Modifier.fillMaxWidth().testTag("open_source_management")) {
                Text("Buka Manajemen Sumber")
            }
        }
    }
}
