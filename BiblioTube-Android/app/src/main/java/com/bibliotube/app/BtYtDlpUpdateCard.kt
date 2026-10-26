package com.bibliotube.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotube.downloader.BtUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BtYtDlpUpdateCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<String?>(null) }
    var channel by remember { mutableIntStateOf(BtUpdate.NIGHTLY) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { version = BtUpdate.currentVersion(ctx) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("yt-dlp: ${version ?: "desconocido"}", style = MaterialTheme.typography.titleSmall)
            if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = channel == BtUpdate.NIGHTLY, onClick = { channel = BtUpdate.NIGHTLY }, label = { Text("nightly") })
                FilterChip(selected = channel == BtUpdate.STABLE, onClick = { channel = BtUpdate.STABLE }, label = { Text("stable") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        busy = true
                        msg = "actualizando..."
                        val st = BtUpdate.updateYtDlp(ctx, channel)
                        version = BtUpdate.currentVersion(ctx)
                        withContext(Dispatchers.Main) {
                            msg = if (st == "DONE") "actualizado: $version" else "estado: ${st ?: "null"} ver=$version"
                            busy = false
                        }
                    }
                }, enabled = !busy) { Text(if (busy) "Trabajando…" else "Actualizar yt-dlp") }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { version = BtUpdate.currentVersion(ctx) }
                }) { Text("Ver versión") }
            }
            Text("nightly = fixes rápidas YouTube. stable = releases. Seal usa nightly por defecto.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
