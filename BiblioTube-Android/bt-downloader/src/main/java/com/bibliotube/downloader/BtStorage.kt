package com.bibliotube.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

// Espejo FileUtil: dirs públicos/privados, tmp, scan MediaStore, mover a SD vía SAF.
object BtStorage {
    const val PRIVATE_SUFFIX = "BiblioTube/.private"

    /**
     * Carpeta de destino de los videos.
     * En Android 10+ la carpeta publica Download/ es de solo lectura sin permisos,
     * asi que se usa la carpeta propia de la app (siempre escribible, sin permisos).
     */
    fun publicDir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        return File(base, "BiblioTube").also { it.mkdirs() }
    }

    fun privateDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "private").also { it.mkdirs() }

    fun tmpDir(ctx: Context): File = File(ctx.cacheDir, "bt_tmp").also {
        it.mkdirs()
        File(it, ".nomedia").runCatching { createNewFile() }
    }

    fun scanPostDownload(ctx: Context, downloadDir: String, title: String): List<String> =
        File(downloadDir).walkTopDown()
            .filter { it.isFile && it.absolutePath.contains(title) }
            .map { it.absolutePath }
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(ctx, toList().toTypedArray(), null, null)
                removeAll { it.contains(Regex("(?i)\\.(jpg|png|webp)$")) || it.contains(Regex("(?i)\\.(srt|ass|vtt|lrc)$")) }
            }

    fun hasSpace(dir: File, neededBytes: Long): Boolean {
        val free = runCatching { dir.usableSpace }.getOrDefault(-1L)
        if (free < 0) return true
        return free > neededBytes + 50L * 1024 * 1024
    }

    fun takePersistableTree(ctx: Context, treeUri: android.net.Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun moveToSdcard(ctx: Context, tempPath: File, treeUri: String): Result<List<String>> {
        val out = mutableListOf<String>()
        val destDir = Uri.parse(treeUri).run {
            DocumentsContract.buildDocumentUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))
        }
        val res = runCatching {
            tempPath.walkTopDown().forEach {
                if (it.isDirectory) return@forEach
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"
                val dest = DocumentsContract.createDocument(ctx.contentResolver, destDir, mime, it.name) ?: return@forEach
                it.inputStream().use { ins ->
                    ctx.contentResolver.openOutputStream(dest)?.use { outs -> ins.copyTo(outs) }
                }
                out.add(dest.toString())
            }
            out.toList()
        }
        tempPath.deleteRecursively()
        return res
    }
}
