package com.bibliotube.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotube.downloader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BtDownloadTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("idle") }
    var progress by remember { mutableFloatStateOf(0f) }
    var useAudio by remember { mutableStateOf(false) }
    var useAria2c by remember { mutableStateOf(false) }
    var withSubs by remember { mutableStateOf(false) }

    fun downloader(): BtDownloader = BtDownloader(
        ctx, BtStorage.publicDir(), BtStorage.tmpDir(ctx),
        BtFiles.cookiesFile(ctx), BtFiles.archiveFile(ctx),
    ).also { runCatching { it.init() } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Descargas (base Seal)", style = MaterialTheme.typography.titleMedium)
        BtYtDlpUpdateCard()
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL video") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !useAudio, onClick = { useAudio = false }, label = { Text("Video") })
            FilterChip(selected = useAudio, onClick = { useAudio = true }, label = { Text("Audio") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = useAria2c, onClick = { useAria2c = !useAria2c }, label = { Text("aria2c") })
            FilterChip(selected = withSubs, onClick = { withSubs = !withSubs }, label = { Text("subs") })
        }
        if (progress > 0f && progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    status = "info..."
                    val dl = downloader()
                    val info = dl.fetchInfo(url).getOrElse { withContext(Dispatchers.Main) { status = "info error: ${it.message}" }; return@launch }
                    withContext(Dispatchers.Main) { status = "${info.title} · ${info.formats?.size ?: 0} formatos · subs=${info.subtitleLangs().take(5)}" }
                }
            }) { Text("Info") }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    status = "descargando..."
                    val dl = downloader()
                    val vo = if (withSubs) BtVideoOptions.custom { subtitles() }.copy(useAria2c = useAria2c) else BtVideoOptions.standard().copy(useAria2c = useAria2c)
                    val res = if (useAudio) dl.downloadAudio(url, videoOptions = vo, audioOptions = BtAudioOptions.standard()) { p, _, _ -> progress = p / 100f }
                    else dl.downloadStandard(url) { p, _, _ -> progress = p / 100f }
                    // standard no lleva aria2c/subs: reintento custom si se pidieron
                    val final = if (!useAudio && (useAria2c || withSubs)) dl.downloadCustom(url, url, vo) { p, _, _ -> progress = p / 100f } else res
                    withContext(Dispatchers.Main) {
                        final.onSuccess { status = "ok: ${it.firstOrNull() ?: "sin ruta"}" ; progress = 1f }
                            .onFailure { status = "error: ${it.message}" }
                    }
                }
            }) { Text("Descargar") }
        }
        Text("Standard = H264/1080. Custom = aria2c+subs aquí. Formatos vía Info.", style = MaterialTheme.typography.bodySmall)
    }
}
