package com.bibliotube.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orden ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirty(): List<CategoryEntity>

    @Query("UPDATE categories SET dirty = 1")
    suspend fun markAllDirty()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY prioridad DESC, fechaGuardado DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE categoryId = :categoryId ORDER BY prioridad DESC, fechaGuardado DESC")
    fun observeByCategory(categoryId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE categoryId IS NULL ORDER BY prioridad DESC, fechaGuardado DESC")
    fun observeUncategorized(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE dirty = 1")
    suspend fun dirty(): List<VideoEntity>

    @Query("UPDATE videos SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT * FROM videos WHERE recordatorioAt IS NOT NULL AND recordatorioAt > :now ORDER BY recordatorioAt ASC")
    suspend fun pendingReminders(now: Long): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun byId(id: String): VideoEntity?
}
