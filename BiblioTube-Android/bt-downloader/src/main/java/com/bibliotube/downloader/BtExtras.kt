package com.bibliotube.downloader

import android.content.Context
import java.io.File

// Templates custom + cookies/archive espejo CommandTemplate + DownloadUtil cookies/archive.
data class BtCommandTemplate(val id: Long = 0, val name: String, val template: String)

object BtFiles {
    fun cookiesFile(ctx: Context): File = File(ctx.cacheDir, "bt_cookies.txt")
    fun archiveFile(ctx: Context): File = File(ctx.filesDir, "bt_archive.txt")
    fun configFile(ctx: Context, taskId: String): File = File(ctx.cacheDir, "bt_cfg_$taskId.txt")

    fun writeNetscapeCookies(cookies: List<String>, out: File): File {
        val header = "# Netscape HTTP Cookie File\n"
        out.writeText(header + cookies.joinToString("\n"))
        return out
    }

    fun archiveContains(archive: File, extractor: String, id: String): Boolean {
        if (!archive.exists()) return false
        return archive.readText().contains("$extractor $id")
    }
}
