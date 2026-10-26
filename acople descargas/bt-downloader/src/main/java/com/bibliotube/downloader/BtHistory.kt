package com.bibliotube.downloader

// Historial mínimo espejo DownloadedVideoInfo + VideoInfoDao. Interfaz para Room o memoria.
data class BtHistoryEntry(
    val id: Long = 0,
    val title: String,
    val url: String,
    val path: String,
    val extractor: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

interface BtHistoryStore {
    suspend fun insert(title: String, url: String, path: String, extractor: String): Long
    suspend fun all(): List<BtHistoryEntry>
    suspend fun clear()
}

object BtHistory {
    private var impl: BtHistoryStore = Memory()
    fun memory(): BtHistoryStore = impl
    fun set(store: BtHistoryStore) { impl = store }

    private class Memory : BtHistoryStore {
        private val list = mutableListOf<BtHistoryEntry>()
        private var seq = 1L
        override suspend fun insert(title: String, url: String, path: String, extractor: String): Long {
            val e = BtHistoryEntry(seq++, title, url, path, extractor)
            list.add(0, e)
            return e.id
        }
        override suspend fun all(): List<BtHistoryEntry> = list.toList()
        override suspend fun clear() { list.clear() }
    }
}

// Room (copiar al host si se usa Room):
// @Entity(tableName="bt_history") data class BtHistoryEntity(...)
// @Dao interface BtHistoryDao { @Insert suspend fun insert(...); @Query("SELECT * ...") ... }
