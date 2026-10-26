package com.bibliotube.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.bibliotube.app.ui.VideoThumbnail
import com.bibliotube.app.work.scheduleReminder
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
import androidx.compose.ui.graphics.Color
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
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent?) {
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
    var plataforma by remember { mutableStateOf("otra") }
    var catId by remember { mutableStateOf<String?>(null) }
    var prioridad by remember { mutableStateOf(0f) }
    var reminderOpt by remember { mutableStateOf(0) }
    var repeat by remember { mutableStateOf("none") }
    var loading by remember { mutableStateOf(url != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var thumbnail by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val res = api.fetchPreview(url.trim())
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                loading = false
                if (res.data != null) {
                    res.data.titulo?.let { if (titulo.isBlank()) titulo = it }
                    res.data.descripcion?.let { if (descripcion.isBlank()) descripcion = it }
                    res.data.plataforma?.let { plataforma = it }
                    res.data.thumbnailUrl?.let { thumbnail = it }
                } else error = res.error
            }
        }
    }

    fun save() {
        if (link.isBlank() || titulo.isBlank() || saving) return
        saving = true
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val at = when (reminderOpt) {
                1 -> now + 24 * 60 * 60 * 1000L
                2 -> now + 7 * 24 * 60 * 60 * 1000L
                else -> null
            }
            val v = VaultVideo(
                id = UUID.randomUUID().toString(),
                categoryId = catId,
                titulo = titulo.trim(),
                descripcion = descripcion.trim(),
                plataforma = plataforma,
                url = link.trim(),
                thumbnailUrl = thumbnail,
                fechaGuardado = now,
                prioridad = prioridad.toInt(),
                recordatorioAt = at,
                recordatorioRepeat = repeat
            )
            repo.saveVideo(v, true)
            if (at != null) scheduleReminder(context, v.id, v.titulo, at, repeat)
            SyncWorker.enqueue(context)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast("Guardado en BiblioTube")
                onDone()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDone() }
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
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
        VideoThumbnail(url = thumbnail, platform = plataforma)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titulo, onValueChange = { titulo = it },
            label = { Text("Titulo") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = descripcion, onValueChange = { descripcion = it },
            label = { Text("Descripcion") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text("Plataforma: $plataforma", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Carpeta", style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = catId == null, onClick = { catId = null }, label = { Text("Ninguna") }) }
            items(categories, key = { it.id }) { c: Category ->
                FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.nombre) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        Text("Prioridad: ${prioridad.toInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = prioridad, onValueChange = { prioridad = it },
            valueRange = 0f..5f, steps = 4, modifier = Modifier.fillMaxWidth()
        )
        Text("Recordatorio", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = reminderOpt == 0, onClick = { reminderOpt = 0 }, label = { Text("No") })
            FilterChip(selected = reminderOpt == 1, onClick = { reminderOpt = 1 }, label = { Text("Manana") })
            FilterChip(selected = reminderOpt == 2, onClick = { reminderOpt = 2 }, label = { Text("Semana") })
        }
        if (reminderOpt > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("none" to "Una vez", "daily" to "Diario", "weekly" to "Semanal").forEach { (r, l) ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { save() }, enabled = !saving && titulo.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Guardando…" else "Guardar")
        }
        } // Column form
        } // Card
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
