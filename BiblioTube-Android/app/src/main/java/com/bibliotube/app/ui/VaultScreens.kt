package com.bibliotube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.model.Category
import com.bibliotube.app.data.model.VaultVideo
import com.bibliotube.app.work.SyncWorker
import com.bibliotube.app.work.cancelReminder
import com.bibliotube.app.work.scheduleReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun VaultHome(repo: VaultRepository, api: ApiClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by repo.observeCategories().collectAsState(initial = emptyList())
    var filterCat by remember { mutableStateOf<String?>(null) } // null=todas, "__none__"=sin categoria
    val videos by repo.observeVideos(filterCat).collectAsState(initial = emptyList())
    var showCatDialog by remember { mutableStateOf(false) }
    var showVideoForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultVideo?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    fun doSync() {
        syncing = true
        syncError = null
        scope.launch(Dispatchers.IO) {
            val err = repo.sync()
            withContext(Dispatchers.Main) {
                syncing = false
                syncError = err
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mi boveda", style = MaterialTheme.typography.headlineSmall)
            Row {
                OutlinedButton(onClick = { doSync() }, enabled = !syncing) {
                    Text(if (syncing) "…" else "Sync")
                }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = { showCatDialog = true }) { Text("+ Carpeta") }
            }
        }
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = filterCat == null, onClick = { filterCat = null }, label = { Text("Todas") })
            }
            item {
                FilterChip(selected = filterCat == "__none__", onClick = { filterCat = "__none__" }, label = { Text("Sin categoria") })
            }
            items(categories, key = { it.id }) { c ->
                FilterChip(selected = filterCat == c.id, onClick = { filterCat = c.id }, label = { Text(c.nombre) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (videos.isEmpty()) {
            Text("Sin videos aqui. Agrega el primero con el boton +.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(videos, key = { it.id }) { v ->
                    VideoCard(v, categories,
                        onEdit = { editing = v; showVideoForm = true },
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                cancelReminder(context, v.id)
                                repo.deleteVideo(v.id)
                            }
                        })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FloatingActionButton(onClick = { editing = null; showVideoForm = true }, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("+")
        }
        } // CompositionLocalProvider
    }

    if (showCatDialog) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCatDialog = false },
            title = { Text("Nueva carpeta") },
            text = {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre (Estudios, Tutoriales…)") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nombre.isNotBlank()) {
                        scope.launch(Dispatchers.IO) { repo.saveCategory(nombre.trim(), "#7C3AED"); SyncWorker.enqueue(context) }
                        showCatDialog = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showCatDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showVideoForm) {
        VideoForm(
            initial = editing, categories = categories, api = api,
            onDismiss = { showVideoForm = false },
            onSave = { v, isNew ->
                scope.launch(Dispatchers.IO) {
                    repo.saveVideo(v, isNew)
                    if (v.recordatorioAt != null) scheduleReminder(context, v.id, v.titulo, v.recordatorioAt, v.recordatorioRepeat)
                    else cancelReminder(context, v.id)
                    SyncWorker.enqueue(context)
                }
                showVideoForm = false
            }
        )
    }
}

@Composable
fun VideoCard(v: VaultVideo, categories: List<Category>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val catName = categories.firstOrNull { it.id == v.categoryId }?.nombre ?: "Sin categoria"
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (v.thumbnailUrl.isNotBlank()) {
                com.bibliotube.app.ui.VideoThumbnail(
                    url = v.thumbnailUrl,
                    platform = v.plataforma
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                v.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${v.plataforma} · $catName · prioridad ${v.prioridad}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            if (v.descripcion.isNotBlank()) Text(
                v.descripcion, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Guardado: ${fmt.format(Date(v.fechaGuardado))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            v.recordatorioAt?.let {
                Text(
                    "Recordatorio: ${fmt.format(Date(it))} (${v.recordatorioRepeat})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Borrar") }
            }
        }
    }
}

@Composable
fun VideoForm(initial: VaultVideo?, categories: List<Category>, api: ApiClient, onDismiss: () -> Unit, onSave: (VaultVideo, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var titulo by remember { mutableStateOf(initial?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(initial?.descripcion ?: "") }
    var plataforma by remember { mutableStateOf(initial?.plataforma ?: "otra") }
    var thumbnail by remember { mutableStateOf(initial?.thumbnailUrl ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var prioridad by remember { mutableStateOf((initial?.prioridad ?: 0).toFloat()) }
    var reminderOpt by remember { mutableStateOf(0) } // 0=ninguno 1=manana 2=semana
    var repeat by remember { mutableStateOf(initial?.recordatorioRepeat ?: "none") }
    var loadingPreview by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    fun loadPreview() {
        if (url.isBlank()) return
        loadingPreview = true
        previewError = null
        scope.launch(Dispatchers.IO) {
            val res = api.fetchPreview(url.trim())
            withContext(Dispatchers.Main) {
                loadingPreview = false
                if (res.data != null) {
                    if (titulo.isBlank()) titulo = res.data.titulo
                    if (descripcion.isBlank()) descripcion = res.data.descripcion
                    thumbnail = res.data.thumbnailUrl
                    plataforma = res.data.plataforma
                } else previewError = res.error
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Agregar video" else "Editar video") },
        text = {
            LazyColumn {
                item {
                    Column {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Link del video") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Button(onClick = { loadPreview() }, enabled = !loadingPreview) {
                                Text(if (loadingPreview) "…" else "Vista previa")
                            }
                            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Titulo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, label = { Text("Descripcion / concepto") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Plataforma: $plataforma")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("youtube", "instagram", "tiktok", "otra").forEach { p ->
                                FilterChip(selected = plataforma == p, onClick = { plataforma = p }, label = { Text(p.take(4)) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Carpeta")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = catId == null, onClick = { catId = null }, label = { Text("Ninguna") })
                            categories.forEach { c ->
                                FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.nombre) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Prioridad: ${prioridad.toInt()} (ordena el listado)")
                        Slider(value = prioridad, onValueChange = { prioridad = it }, valueRange = 0f..5f, steps = 4)
                        Text("Recordatorio")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = reminderOpt == 0, onClick = { reminderOpt = 0 }, label = { Text("No") })
                            FilterChip(selected = reminderOpt == 1, onClick = { reminderOpt = 1 }, label = { Text("Manana") })
                            FilterChip(selected = reminderOpt == 2, onClick = { reminderOpt = 2 }, label = { Text("Semana") })
                        }
                        if (reminderOpt > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("none" to "Una vez", "daily" to "Diario", "weekly" to "Semanal").forEach { (r, l) ->
                                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(l) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isBlank() || titulo.isBlank()) return@TextButton
                val now = System.currentTimeMillis()
                val at = when (reminderOpt) {
                    1 -> now + 24 * 60 * 60 * 1000L
                    2 -> now + 7 * 24 * 60 * 60 * 1000L
                    else -> initial?.recordatorioAt
                }
                val v = VaultVideo(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    categoryId = catId, titulo = titulo.trim(), descripcion = descripcion.trim(),
                    plataforma = plataforma, url = url.trim(), thumbnailUrl = thumbnail,
                    fechaGuardado = initial?.fechaGuardado ?: now,
                    prioridad = prioridad.toInt(), recordatorioAt = at, recordatorioRepeat = repeat
                )
                onSave(v, initial == null)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
