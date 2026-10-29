package com.bibliotube.app.data.model

data class Category(
    val id: String,
    val nombre: String,
    val color: String,
    val orden: Int
)

object DriveStatus {
    const val NONE = "none"
    const val PENDING = "pending"
    const val UPLOADING = "uploading"
    const val DONE = "done"
    const val ERROR = "error"
}

data class VaultVideo(
    val id: String,
    val categoryId: String?,
    val titulo: String,
    val descripcion: String,
    val plataforma: String,
    val url: String,
    val thumbnailUrl: String,
    val fechaGuardado: Long,
    val prioridad: Int,
    val recordatorioAt: Long?,
    val recordatorioRepeat: String,
    val driveFileId: String = "",
    val driveStatus: String = DriveStatus.NONE,
    val localPath: String = ""
)

data class VideoPreview(
    val titulo: String,
    val descripcion: String,
    @com.google.gson.annotations.SerializedName("thumbnail_url") val thumbnailUrl: String,
    val plataforma: String,
    /** false = oEmbed sin datos: privado, eliminado, con edad o bloqueado. */
    val available: Boolean = true
)
