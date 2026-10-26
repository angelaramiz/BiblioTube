package com.bibliotube.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CategoryEntity::class, VideoEntity::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun videos(): VideoDao

    companion object {
        @Volatile private var instance: VaultDatabase? = null

        fun get(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "bibliotube.db"
                ).build().also { instance = it }
            }
    }
}
