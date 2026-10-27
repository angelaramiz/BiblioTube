package com.bibliotube.app.data

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
    @SerializedName("recordatorio_repeat") val recordatorioRepeat: String?
)

fun parseInstant(s: String?): Long = try {
    if (s.isNullOrBlank()) System.currentTimeMillis()
    else java.time.Instant.parse(s).toEpochMilli()
} catch (e: Exception) { System.currentTimeMillis() }

class VaultRepository(private val db: VaultDatabase, private val api: ApiClient) {

    companion object {
        const val AUTH_EXPIRED = "AUTH_EXPIRED"
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
        try { api.vaultDelete("/api/categories/$id") } catch (e: Exception) { /* se reintenta en sync */ }
    }

    suspend fun saveVideo(v: VaultVideo, isNew: Boolean): String = withContext(Dispatchers.IO) {
        db.videos().upsert(v.toEntity(dirty = true))
        if (!isNew) {
            try {
                api.vaultPatch<Map<String, Any>>("/api/videos/${v.id}", v.toPayload())
            } catch (e: Exception) { /* queda dirty para el sync */ }
            v.id
        } else v.id
    }

    suspend fun deleteVideo(id: String) = withContext(Dispatchers.IO) {
        db.videos().deleteById(id)
        try { api.vaultDelete("/api/videos/$id") } catch (e: Exception) { /* sync lo reintentara si hay red */ }
    }

    /** Pull (filas limpias) + push (filas dirty). Devuelve error o null si OK. */
    suspend fun sync(): String? = withContext(Dispatchers.IO) {
        try {
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
            for (c in db.categories().dirty()) {
                val payload = mapOf("nombre" to c.nombre, "color" to c.color, "orden" to c.orden)
                try {
                    if (c.id in remoteCatIds) api.vaultPatch<Map<String, Any>>("/api/categories/${c.id}", payload)
                    else api.vaultPost<Map<String, Any>>("/api/categories", payload)
                    db.categories().upsert(c.copy(dirty = false))
                } catch (e: Exception) { /* sigue dirty */ }
            }

            val vids = api.vaultList<RemoteVideo>("/api/videos")
            if (vids.error != null) {
                return@withContext if (api.lastHttpCode == 401) AUTH_EXPIRED else vids.error
            }
            val dirtyVidIds = db.videos().dirty().map { it.id }.toSet()
            val remoteVids = vids.data ?: emptyList()
            val remoteIds = remoteVids.map { it.id }.toSet()
            db.videos().upsertAll(
                remoteVids.filter { it.id !in dirtyVidIds }.map { it.toEntity() }
            )
            for (v in db.videos().dirty()) {
                try {
                    if (v.id in remoteIds) api.vaultPatch<Map<String, Any>>("/api/videos/${v.id}", v.toModel().toPayload())
                    else api.vaultPost<Map<String, Any>>("/api/videos", v.toModel().toPayload())
                    db.videos().upsert(v.copy(dirty = false))
                } catch (e: Exception) { /* sigue dirty */ }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Error de sincronizacion"
        }
    }

    private fun VideoEntity.toModel() = VaultVideo(
        id, categoryId, titulo, descripcion, plataforma, url, thumbnailUrl,
        fechaGuardado, prioridad, recordatorioAt, recordatorioRepeat
    )

    private fun VaultVideo.toEntity(dirty: Boolean = false) = VideoEntity(
        id, categoryId, titulo, descripcion, plataforma, url, thumbnailUrl,
        fechaGuardado, prioridad, recordatorioAt, recordatorioRepeat,
        System.currentTimeMillis(), dirty
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
        "recordatorio_repeat" to recordatorioRepeat
    )

    private fun RemoteVideo.toEntity() = VideoEntity(
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
        dirty = false
    )
}
