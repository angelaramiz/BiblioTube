package com.bibliotube.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN driveFileId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE videos ADD COLUMN driveStatus TEXT NOT NULL DEFAULT 'none'")
        db.execSQL("ALTER TABLE videos ADD COLUMN localPath TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [CategoryEntity::class, VideoEntity::class], version = 2, exportSchema = true)
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
