package com.bibliotube.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.api.SERVER_URL
import com.bibliotube.app.data.SessionManager
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.model.Category
import com.bibliotube.app.data.model.VaultVideo
import com.bibliotube.app.ui.theme.BiblioTubeTheme
import com.bibliotube.app.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val URL_REGEX = Regex("https?://[^\\s]+")

fun extractSharedUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return URL_REGEX.find(text)?.value
}

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            extractSharedUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else null
        setContent {
            BiblioTubeTheme {
                QuickSaveScreen(
                    url = shared,
                    onDone = { finish() }
                )
            }
        }
    }
}

@Composable
fun QuickSaveScreen(url: String?, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(SERVER_URL) }
    val repo = remember { VaultRepository(VaultDatabase.get(context), api) }
    val categories by repo.observeCategories().collectAsState(initial = emptyList())
    var link by remember(url) { mutableStateOf(url ?: "") }
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(url != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val res = api.fetchPreview(url.trim())
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                loading = false
                if (res.data != null) {
                    if (titulo.isBlank()) titulo = res.data.titulo
                    if (descripcion.isBlank()) descripcion = res.data.descripcion
                } else error = res.error
            }
        }
    }

    fun save() {
        if (link.isBlank() || titulo.isBlank() || saving) return
        saving = true
        scope.launch(Dispatchers.IO) {
            val v = VaultVideo(
                id = UUID.randomUUID().toString(),
                categoryId = catId,
                titulo = titulo.trim(),
                descripcion = descripcion.trim(),
                plataforma = detectPlatform(link),
                url = link.trim(),
                thumbnailUrl = "",
                fechaGuardado = System.currentTimeMillis(),
                prioridad = 0,
                recordatorioAt = null,
                recordatorioRepeat = "none"
            )
            repo.saveVideo(v, true)
            SyncWorker.enqueue(context)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast("Guardado en BiblioTube")
                onDone()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Text("Guardar en BiblioTube", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (url == null) {
            Text("Sin enlace para guardar.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
        } else {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }
        Text(link, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titulo, onValueChange = { titulo = it },
            label = { Text("Titulo") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Carpeta", style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = catId == null, onClick = { catId = null }, label = { Text("Ninguna") }) }
            items(categories, key = { it.id }) { c: Category ->
                FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.nombre) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { save() }, enabled = !saving && titulo.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Guardando…" else "Guardar")
        }
        } // else(url != null)
        } // CompositionLocalProvider
    }
}

private fun detectPlatform(link: String): String {
    val host = try { java.net.URI(link).host?.lowercase().orEmpty() } catch (e: Exception) { "" }
    return when {
        "youtu" in host -> "youtube"
        "instagram" in host -> "instagram"
        "tiktok" in host -> "tiktok"
        else -> "otra"
    }
}
