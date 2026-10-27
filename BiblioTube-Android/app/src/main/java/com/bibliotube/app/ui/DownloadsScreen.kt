package com.bibliotube.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.downloader.DownloadCenter
import com.bibliotube.downloader.BtTaskState

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val queue = DownloadCenter.queue(context)
    val tasks by queue.tasks.collectAsState()
    val list = tasks.values.sortedByDescending { it.progress }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Descargas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Videos descargados al dispositivo con yt-dlp.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Text("Sin descargas. Usa el boton de descarga en un video de la boveda.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (t.state) {
                                    BtTaskState.Done -> "Completada: ${t.files.firstOrNull() ?: ""}"
                                    BtTaskState.Error -> "Error: ${t.error ?: ""}"
                                    BtTaskState.Canceled -> "Cancelada"
                                    BtTaskState.Running -> "Descargando… ${t.progress.toInt()}%"
                                    else -> "En cola…"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (t.state == BtTaskState.Running || t.state == BtTaskState.FetchingInfo || t.state == BtTaskState.Ready) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { (t.progress / 100f).coerceIn(0f, 1f) })
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { queue.cancel(t.id) }) { Text("Cancelar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
