package com.bibliotube.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var searchQuery by remember { mutableStateOf("") }
    val allVideos by repo.observeVideos(filterCat).collectAsState(initial = emptyList())

    // Filtro de búsqueda en tiempo real
    val filteredVideos = remember(allVideos, searchQuery) {
        if (searchQuery.isBlank()) allVideos
        else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            allVideos.filter {
                it.titulo.lowercase(Locale.getDefault()).contains(q) ||
                it.descripcion.lowercase(Locale.getDefault()).contains(q) ||
                it.plataforma.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    var showCatDialog by remember { mutableStateOf(false) }
    var showVideoForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultVideo?>(null) }
    var videoToDelete by remember { mutableStateOf<VaultVideo?>(null) }
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
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            // Contenedor principal con espaciado estructurado
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Barra de control superior: Título y acciones rápidas
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mi Bóveda",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${filteredVideos.size} ${if (filteredVideos.size == 1) "video" else "videos"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón de sincronización plano
                        IconButton(
                            onClick = { doSync() },
                            enabled = !syncing,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sincronizar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Botón añadir carpeta plano
                        OutlinedButton(
                            onClick = { showCatDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Carpeta",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }

                        // Botón añadir video primario plano
                        Button(
                            onClick = {
                                editing = null
                                showVideoForm = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Video",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                // Error de sincronización si existe
                syncError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Campo de búsqueda minimalista plano
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Buscar videos por título, concepto o plataforma…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpiar búsqueda",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(Modifier.height(10.dp))

                // Filtro horizontal plano por carpetas
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FlatFolderChip(
                            label = "Todos",
                            selected = filterCat == null,
                            onClick = { filterCat = null }
                        )
                    }
                    item {
                        FlatFolderChip(
                            label = "Sin carpeta",
                            selected = filterCat == "__none__",
                            onClick = { filterCat = "__none__" }
                        )
                    }
                    items(categories, key = { it.id }) { c ->
                        FlatFolderChip(
                            label = c.nombre,
                            selected = filterCat == c.id,
                            onClick = { filterCat = c.id }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Contenido principal de la bóveda
                if (filteredVideos.isEmpty()) {
                    FlatEmptyState(
                        hasQuery = searchQuery.isNotBlank(),
                        onAddVideo = {
                            editing = null
                            showVideoForm = true
                        },
                        onClearSearch = { searchQuery = "" }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredVideos, key = { it.id }) { v ->
                            FlatVideoCard(
                                video = v,
                                categories = categories,
                                onOpenUrl = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Error silencioso o gestionado
                                    }
                                },
                                onEdit = {
                                    editing = v
                                    showVideoForm = true
                                },
                                onDelete = {
                                    videoToDelete = v
                                }
                            )
                        }
                        item {
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        } // CompositionLocalProvider
    }

    // Modal de confirmación para eliminar video
    videoToDelete?.let { targetVideo ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = {
                Text(
                    "Eliminar video",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar '${targetVideo.titulo}' de tu bóveda?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            cancelReminder(context, targetVideo.id)
                            repo.deleteVideo(targetVideo.id)
                            SyncWorker.enqueue(context)
                        }
                        videoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal para crear carpeta
    if (showCatDialog) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCatDialog = false },
            title = {
                Text(
                    "Nueva Carpeta",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        "Crea una categoría para estructurar tus videos guardados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre (p. ej. Tutoriales, Ciencia, Podcasts)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombre.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                repo.saveCategory(nombre.trim(), "#7C3AED")
                                SyncWorker.enqueue(context)
                            }
                            showCatDialog = false
                        }
                    },
                    enabled = nombre.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCatDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal para crear o editar video
    if (showVideoForm) {
        VideoFormDialog(
            initial = editing,
            categories = categories,
            api = api,
            onDismiss = { showVideoForm = false },
            onSave = { v, isNew ->
                scope.launch(Dispatchers.IO) {
                    repo.saveVideo(v, isNew)
                    if (v.recordatorioAt != null) {
                        scheduleReminder(context, v.id, v.titulo, v.recordatorioAt, v.recordatorioRepeat)
                    } else {
                        cancelReminder(context, v.id)
                    }
                    SyncWorker.enqueue(context)
                }
                showVideoForm = false
            }
        )
    }
}

/**
 * Chip plano minimalista para filtrado por carpetas.
 */
@Composable
private fun FlatFolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val textCol = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = textCol
        )
    }
}

/**
 * Tarjeta de video con diseño plano minimalista y estructurado.
 */
@Composable
fun FlatVideoCard(
    video: VaultVideo,
    categories: List<Category>,
    onOpenUrl: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val catName = categories.firstOrNull { it.id == video.categoryId }?.nombre
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            // Fila superior: Plataforma, Carpeta y Prioridad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge plano de plataforma
                    PlatformBadge(video.plataforma)

                    // Badge de carpeta si existe
                    if (catName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Indicador de prioridad plano
                if (video.prioridad > 0) {
                    PriorityBadge(video.prioridad)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Thumbnail y contenido
            if (video.thumbnailUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenUrl)
                ) {
                    VideoThumbnail(
                        url = video.thumbnailUrl,
                        platform = video.plataforma
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Título formal
            Text(
                text = video.titulo,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Descripción breve
            if (video.descripcion.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = video.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            // Fila de metadatos (fecha de guardado y recordatorio)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Añadido el ${fmt.format(Date(video.fechaGuardado))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                video.recordatorioAt?.let { remAt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Recordatorio activo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = fmt.format(Date(remAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Barra inferior de acciones planas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón abrir enlace directo
                OutlinedButton(
                    onClick = onOpenUrl,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Abrir enlace",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Abrir", style = MaterialTheme.typography.labelSmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Editar
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Eliminar
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Insignia visual limpia para la plataforma del video.
 */
@Composable
private fun PlatformBadge(platform: String) {
    val norm = platform.lowercase(Locale.getDefault())
    val (label, bgCol, textCol) = when {
        norm.contains("youtu") -> Triple("YouTube", Color(0xFFFF0000).copy(alpha = 0.12f), Color(0xFFFF4D4D))
        norm.contains("insta") -> Triple("Instagram", Color(0xFFE1306C).copy(alpha = 0.12f), Color(0xFFF06292))
        norm.contains("tik") -> Triple("TikTok", Color(0xFF00F2FE).copy(alpha = 0.12f), Color(0xFF00BCD4))
        else -> Triple("Web", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgCol)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textCol
        )
    }
}

/**
 * Insignia formal para la prioridad.
 */
@Composable
private fun PriorityBadge(priority: Int) {
    val (label, color) = when (priority) {
        in 4..5 -> "Alta" to MaterialTheme.colorScheme.error
        in 2..3 -> "Media" to MaterialTheme.colorScheme.primary
        else -> "Baja" to MaterialTheme.colorScheme.secondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}

/**
 * Estado vacío formal con diseño plano.
 */
@Composable
private fun FlatEmptyState(
    hasQuery: Boolean,
    onAddVideo: () -> Unit,
    onClearSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    RoundedCornerShape(16.dp)
                )
                .padding(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasQuery) Icons.Default.Search else Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (hasQuery) "Sin resultados" else "Tu bóveda está vacía",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (hasQuery)
                    "No encontramos ningún video que coincida con tu búsqueda."
                else
                    "Guarda videos compartiendo enlaces desde YouTube o añade uno directamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            if (hasQuery) {
                OutlinedButton(onClick = onClearSearch) {
                    Text("Limpiar búsqueda")
                }
            } else {
                Button(onClick = onAddVideo) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir primer video")
                }
            }
        }
    }
}

/**
 * Diálogo modal formal para añadir y editar videos.
 */
@Composable
fun VideoFormDialog(
    initial: VaultVideo?,
    categories: List<Category>,
    api: ApiClient,
    onDismiss: () -> Unit,
    onSave: (VaultVideo, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var titulo by remember { mutableStateOf(initial?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(initial?.descripcion ?: "") }
    var plataforma by remember { mutableStateOf(initial?.plataforma ?: "otra") }
    var thumbnail by remember { mutableStateOf(initial?.thumbnailUrl ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var prioridad by remember { mutableFloatStateOf((initial?.prioridad ?: 0).toFloat()) }
    var reminderOpt by remember { mutableIntStateOf(0) } // 0=ninguno 1=manana 2=semana
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
                } else {
                    previewError = res.error
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Añadir a la Bóveda" else "Editar Video",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Enlace con botón de obtención de metadatos
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Enlace del video") },
                            placeholder = { Text("https://www.youtube.com/watch?v=…") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { loadPreview() },
                                enabled = !loadingPreview && url.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (loadingPreview) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Analizando…", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("Obtener datos automáticos", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        previewError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        // Thumbnail de vista previa si existe
                        if (thumbnail.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                VideoThumbnail(url = thumbnail, platform = plataforma)
                            }
                        }

                        // Título
                        OutlinedTextField(
                            value = titulo,
                            onValueChange = { titulo = it },
                            label = { Text("Título") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Descripción / Notas personales
                        OutlinedTextField(
                            value = descripcion,
                            onValueChange = { descripcion = it },
                            label = { Text("Notas / Concepto clave") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Selección de plataforma
                        Text(
                            "Plataforma",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("youtube", "instagram", "tiktok", "otra").forEach { p ->
                                FilterChip(
                                    selected = plataforma == p,
                                    onClick = { plataforma = p },
                                    label = { Text(p.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        // Selección de carpeta
                        Text(
                            "Carpeta",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = catId == null,
                                    onClick = { catId = null },
                                    label = { Text("Ninguna") }
                                )
                            }
                            items(categories, key = { it.id }) { c ->
                                FilterChip(
                                    selected = catId == c.id,
                                    onClick = { catId = c.id },
                                    label = { Text(c.nombre) }
                                )
                            }
                        }

                        // Prioridad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Prioridad",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            PriorityBadge(prioridad.toInt())
                        }
                        Slider(
                            value = prioridad,
                            onValueChange = { prioridad = it },
                            valueRange = 0f..5f,
                            steps = 4
                        )

                        // Recordatorio
                        Text(
                            "Programar recordatorio",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = reminderOpt == 0, onClick = { reminderOpt = 0 }, label = { Text("No") })
                            FilterChip(selected = reminderOpt == 1, onClick = { reminderOpt = 1 }, label = { Text("Mañana") })
                            FilterChip(selected = reminderOpt == 2, onClick = { reminderOpt = 2 }, label = { Text("En 1 semana") })
                        }
                        if (reminderOpt > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Button(
                onClick = {
                    if (url.isBlank() || titulo.isBlank()) return@Button
                    val now = System.currentTimeMillis()
                    val at = when (reminderOpt) {
                        1 -> now + 24 * 60 * 60 * 1000L
                        2 -> now + 7 * 24 * 60 * 60 * 1000L
                        else -> initial?.recordatorioAt
                    }
                    val v = VaultVideo(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        categoryId = catId,
                        titulo = titulo.trim(),
                        descripcion = descripcion.trim(),
                        plataforma = plataforma,
                        url = url.trim(),
                        thumbnailUrl = thumbnail,
                        fechaGuardado = initial?.fechaGuardado ?: now,
                        prioridad = prioridad.toInt(),
                        recordatorioAt = at,
                        recordatorioRepeat = repeat
                    )
                    onSave(v, initial == null)
                },
                enabled = url.isNotBlank() && titulo.isNotBlank()
            ) {
                Text("Guardar Video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
