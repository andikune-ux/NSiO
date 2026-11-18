package com.example.embedded.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.embedded.engine.EmbeddedModuleManager

@Composable
fun EmbeddedModuleCenter() {
    val context=LocalContext.current
    val manager=remember(context) { EmbeddedModuleManager(context) }
    val states by manager.states.collectAsState()
    val module=states.firstOrNull { it.metadata.id=="dev.andikune.nsio.ddtv" }
    var showModule by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f))
    ) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Extension,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSiO Embedded Module Engine",style=MaterialTheme.typography.titleMedium)
                    Text("Hybrid prototype • API 1",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Lingkungan modul native dan dynamic loader. Terpisah dari player utama NSiO.",style=MaterialTheme.typography.bodySmall)
            if(module!=null) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(module.metadata.name,style=MaterialTheme.typography.titleSmall)
                        Text("v${module.metadata.version} • Native prototype",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked=module.enabled,onCheckedChange={ enabled ->
                        runCatching { manager.setEnabled(module.metadata.id,enabled) }
                            .onFailure { error=it.localizedMessage ?: "Tidak dapat mengubah status modul." }
                    })
                }
                Button(
                    onClick={
                        runCatching { manager.start(module.metadata.id); showModule=true }
                            .onFailure { error=it.localizedMessage ?: "Modul gagal dijalankan." }
                    },
                    enabled=module.enabled,
                    modifier=Modifier.fillMaxWidth()
                ) { Text("Buka DDTV Prototype") }
                Text("Dynamic DEX terkunci secara default sampai artefak lolos verifikasi hash tepercaya.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    error?.let { message ->
        AlertDialog(onDismissRequest={error=null},confirmButton={TextButton(onClick={error=null}){Text("Tutup")}},title={Text("Module Engine")},text={Text(message)})
    }
    if(showModule) {
        AlertDialog(
            onDismissRequest={showModule=false;manager.stop("dev.andikune.nsio.ddtv")},
            title={Text("DDTV Experimental")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Module Host berhasil membuka modul native percobaan.")
                    Text("Belum terhubung ke server, protokol, atau payload DDTV MONYET PRO.",style=MaterialTheme.typography.bodySmall)
                    Text("Lifecycle aktif • penyimpanan terpisah • player utama tidak disentuh.",style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={TextButton(onClick={showModule=false;manager.stop("dev.andikune.nsio.ddtv")}){Text("Tutup Modul")}}
        )
    }
}
