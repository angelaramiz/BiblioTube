package com.bibliotube.downloader

import android.content.Context
import java.io.File

// Archive + templates custom (módulo, sin UI). Espejo DownloadUtil useDownloadArchive + CommandTemplate.
object BtArchive {
    fun file(ctx: Context): File = File(ctx.filesDir, "bt_archive.txt").also {
        runCatching { if (!it.exists()) it.createNewFile() }
    }
    fun contains(f: File, extractor: String, id: String): Boolean {
        if (!f.exists()) return false
        return runCatching { f.readText().contains("$extractor $id") }.getOrDefault(false)
    }
    fun mark(f: File, extractor: String, id: String) {
        runCatching { f.appendText("$extractor $id\n") }
    }
}

interface BtTemplateStore {
    suspend fun all(): List<BtCommandTemplate>
    suspend fun save(t: BtCommandTemplate): Long
    suspend fun delete(id: Long)
}

object BtTemplates {
    private var impl: BtTemplateStore = Memory()
    fun set(s: BtTemplateStore) { impl = s }
    fun store(): BtTemplateStore = impl
    fun defaults(): List<BtCommandTemplate> = listOf(
        BtCommandTemplate(0, "best", "--no-mtime -S \"ext\""),
        BtCommandTemplate(0, "audio-mp3", "-x --audio-format mp3 --embed-thumbnail"),
    )
    private class Memory : BtTemplateStore {
        private val l = defaults().toMutableList(); private var s = 1L
        override suspend fun all() = l.toList()
        override suspend fun save(t: BtCommandTemplate): Long { l.add(t.copy(id = s)); return s++ }
        override suspend fun delete(id: Long) { l.removeAll { it.id == id } }
    }
}
