package com.bibliotube.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val color: String,
    val orden: Int,
    val updatedAt: Long,
    val dirty: Boolean = false
)

@Entity(
    tableName = "videos",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
data class VideoEntity(
    @PrimaryKey val id: String,
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
    val updatedAt: Long,
    val dirty: Boolean = false
)
