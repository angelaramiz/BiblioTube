package com.bibliotube.app.data

import android.content.Context
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.data.local.CategoryEntity
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.local.VideoEntity
import com.bibliotube.app.data.model.Category
import com.bibliotube.app.data.model.VaultVideo
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

private data class RemoteCategory(
    val id: String, val nombre: String, val color: String?, val orden: Int?
)
private data class RemoteVideo(
    val id: String,
    @SerializedName("category_id") val categoryId: String?,
    val titulo: String,
    val descripcion: String?,
    val plataforma: String?,
    val url: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("fecha_guardado") val fechaGuardado: String?,
    val prioridad: Int?,
    @SerializedName("recordatorio_at") val recordatorioAt: String?,
    @SerializedName("recordatorio_repeat") val recordatorioRepeat: String?,
    @SerializedName("drive_file_id") val driveFileId: String?,
    @SerializedName("drive_status") val driveStatus: String?,
    @SerializedName("local_path") val localPath: String?
)

fun parseInstant(s: String?): Long = try {
    if (s.isNullOrBlank()) System.currentTimeMillis()
    else java.time.Instant.parse(s).toEpochMilli()
} catch (e: Exception) { System.currentTimeMillis() }

class VaultRepository(
    private val db: VaultDatabase,
    private val api: ApiClient,
    private val context: Context? = null,
) {

    companion object {
        const val AUTH_EXPIRED = "AUTH_EXPIRED"
        private const val DELETES_PREFS = "bibliotube_pending_deletes"
    }

    /** Borrados pendientes: si el DELETE falla, se reintenta en el proximo sync. */
    private fun pendingDeletes(): MutableSet<String> {
        val prefs = context?.getSharedPreferences(DELETES_PREFS, Context.MODE_PRIVATE) ?: return mutableSetOf()
        return prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun savePendingDeletes(ids: Set<String>) {
        val prefs = context?.getSharedPreferences(DELETES_PREFS, Context.MODE_PRIVATE) ?: return
        prefs.edit().putStringSet("ids", ids).apply()
    }

    fun observeCategories(): Flow<List<Category>> =
        db.categories().observeAll().map { rows ->
            rows.map { Category(it.id, it.nombre, it.color, it.orden) }
        }

    fun observeVideos(categoryId: String?): Flow<List<VaultVideo>> {
        val flow = when (categoryId) {
            null -> db.videos().observeAll()
            "__none__" -> db.videos().observeUncategorized()
            else -> db.videos().observeByCategory(categoryId)
        }
        return flow.map { rows -> rows.map { it.toModel() } }
    }

    suspend fun saveCategory(nombre: String, color: String, existingId: String? = null): String =
        withContext(Dispatchers.IO) {
            val id = existingId ?: UUID.randomUUID().toString()
            db.categories().upsert(CategoryEntity(id, nombre, color, 0, System.currentTimeMillis(), dirty = true))
            id
        }

    suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        db.categories().deleteById(id)
        val res = api.vaultDelete("/api/categories/$id")
        if (res.error != null) {
            val p = pendingDeletes().also { it.add("categories:$id") }
            savePendingDeletes(p)
            AppLog.d("sync", "borrado pendiente (categoria $id): ${res.error}")
        }
    }

    suspend fun saveVideo(v: VaultVideo, isNew: Boolean): String = withContext(Dispatchers.IO) {
        db.videos().upsert(v.toEntity(dirty = true))
        if (!isNew) {
            val res = api.vaultPatch<Map<String, Any>>("/api/videos/${v.id}", v.toPayload())
            if (res.error != null) AppLog.d("sync", "edicion local pendiente: ${res.error}")
            v.id
        } else v.id
    }

    suspend fun deleteVideo(id: String) = withContext(Dispatchers.IO) {
        db.videos().deleteById(id)
        val res = api.vaultDelete("/api/videos/$id")
        if (res.error != null) {
            val p = pendingDeletes().also { it.add("videos:$id") }
            savePendingDeletes(p)
            AppLog.d("sync", "borrado pendiente (video $id): ${res.error}")
        }
    }

    suspend fun setLocalPath(id: String, path: String) = withContext(Dispatchers.IO) {
        db.videos().byId(id)?.let { db.videos().upsert(it.copy(localPath = path, dirty = true)) }
    }

    suspend fun setDriveState(id: String, status: String, fileId: String = "") = withContext(Dispatchers.IO) {
        db.videos().byId(id)?.let { db.videos().upsert(it.copy(driveStatus = status, driveFileId = fileId, dirty = true)) }
    }

    /** Migracion: versiones previas marcaban "limpio" aunque el push fallara. */
    suspend fun markAllDirty() = withContext(Dispatchers.IO) {
        db.categories().markAllDirty()
        db.videos().markAllDirty()
    }

    /** Pull (filas limpias) + push (filas dirty). Devuelve error o null si OK. */
    suspend fun sync(): String? = withContext(Dispatchers.IO) {
        try {
            retryPendingDeletes()

            val cats = api.vaultList<RemoteCategory>("/api/categories")
            if (cats.error != null) {
                return@withContext if (api.lastHttpCode == 401) AUTH_EXPIRED else cats.error
            }
            val dirtyCatIds = db.categories().dirty().map { it.id }.toSet()
            val remoteCats = cats.data ?: emptyList()
            val remoteCatIds = remoteCats.map { it.id }.toSet()
            db.categories().upsertAll(
                remoteCats.filter { it.id !in dirtyCatIds }.map {
                    CategoryEntity(it.id, it.nombre, it.color ?: "#7C3AED", it.orden ?: 0, System.currentTimeMillis(), dirty = false)
                }
            )
            var pushFailures = 0
            for (c in db.categories().dirty()) {
                val payload = mapOf<String, Any?>(
                    "id" to c.id, "nombre" to c.nombre, "color" to c.color, "orden" to c.orden
                )
                val res = if (c.id in remoteCatIds) {
                    api.vaultPatch<Map<String, Any>>("/api/categories/${c.id}", payload)
                } else {
                    api.vaultPost<Map<String, Any>>("/api/categories", payload)
                }
                if (res.error == null) {
                    db.categories().upsert(c.copy(dirty = false))
                } else {
                    pushFailures++
                    AppLog.d("sync", "categoria sin subir (${c.nombre}): ${res.error}")
                }
            }

            val vids = api.vaultList<RemoteVideo>("/api/videos")
            if (vids.error != null) {
                return@withContext if (api.lastHttpCode == 401) AUTH_EXPIRED else vids.error
            }
            val dirtyVidIds = db.videos().dirty().map { it.id }.toSet()
            val remoteVids = vids.data ?: emptyList()
            val remoteIds = remoteVids.map { it.id }.toSet()
            db.videos().upsertAll(
                remoteVids.filter { it.id !in dirtyVidIds }.map {
                    it.toEntity(localPath = db.videos().byId(it.id)?.localPath.orEmpty())
                }
            )
            for (v in db.videos().dirty()) {
                val payload = v.toModel().toPayload() + mapOf<String, Any?>("id" to v.id)
                val res = if (v.id in remoteIds) {
                    api.vaultPatch<Map<String, Any>>("/api/videos/${v.id}", payload)
                } else {
                    api.vaultPost<Map<String, Any>>("/api/videos", payload)
                }
                if (res.error == null) {
                    db.videos().upsert(v.copy(dirty = false))
                } else {
                    pushFailures++
                    AppLog.d("sync", "video sin subir (${v.titulo}): ${res.error}")
                }
            }
            if (pushFailures > 0) "$pushFailures cambio(s) sin subir" else null
        } catch (e: Exception) {
            e.message ?: "Error de sincronizacion"
        }
    }

    private suspend fun retryPendingDeletes() {
        val pending = pendingDeletes()
        if (pending.isEmpty()) return
        val left = mutableSetOf<String>()
        for (entry in pending) {
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) continue
            val path = "/api/${parts[0]}/${parts[1]}"
            val res = api.vaultDelete(path)
            // 404 = ya no existe en el servidor: objetivo cumplido.
            if (res.error != null && api.lastHttpCode != 404) left.add(entry)
        }
        savePendingDeletes(left)
        if (pending.size != left.size) {
            AppLog.d("sync", "borrados remotos aplicados: ${pending.size - left.size}")
        }
    }

    private fun VideoEntity.toModel() = VaultVideo(
        id, categoryId, titulo, descripcion, plataforma, url, thumbnailUrl,
        fechaGuardado, prioridad, recordatorioAt, recordatorioRepeat,
        driveFileId, driveStatus, localPath
    )

    private fun VaultVideo.toEntity(dirty: Boolean = false) = VideoEntity(
        id, categoryId, titulo, descripcion, plataforma, url, thumbnailUrl,
        fechaGuardado, prioridad, recordatorioAt, recordatorioRepeat,
        System.currentTimeMillis(), dirty,
        driveFileId, driveStatus, localPath
    )

    private fun VaultVideo.toPayload(): Map<String, Any?> = mapOf(
        "category_id" to categoryId,
        "titulo" to titulo,
        "descripcion" to descripcion,
        "plataforma" to plataforma,
        "url" to url,
        "thumbnail_url" to thumbnailUrl,
        "prioridad" to prioridad,
        "recordatorio_at" to recordatorioAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
        "recordatorio_repeat" to recordatorioRepeat,
        "drive_file_id" to driveFileId,
        "drive_status" to driveStatus
        // local_path NO se sincroniza: es una ruta valida solo en este telefono.
    )

    private fun RemoteVideo.toEntity(localPath: String = "") = VideoEntity(
        id = id,
        categoryId = categoryId,
        titulo = titulo,
        descripcion = descripcion.orEmpty(),
        plataforma = plataforma ?: "otra",
        url = url,
        thumbnailUrl = thumbnailUrl.orEmpty(),
        fechaGuardado = parseInstant(fechaGuardado),
        prioridad = prioridad ?: 0,
        recordatorioAt = recordatorioAt?.let { parseInstant(it) },
        recordatorioRepeat = recordatorioRepeat ?: "none",
        updatedAt = System.currentTimeMillis(),
        dirty = false,
        driveFileId = driveFileId.orEmpty(),
        driveStatus = driveStatus ?: "none",
        localPath = localPath.orEmpty()
    )
}
