package com.bibliotube.downloader

import android.content.Context
import java.io.File

// Verificación de salidas: filtra thumbs/subs, exige tamaño>0, escanea MediaStore.
object BtOutput {
    private val AUX = Regex("(?i)\\.(jpg|jpeg|png|webp|srt|ass|vtt|lrc|json|info\\.json)$")

    fun verify(dir: File, titleHint: String): List<String> {
        if (!dir.exists()) return emptyList()
        val base = dir.walkTopDown().filter { it.isFile }.toList()
        val withHint = if (titleHint.isNotEmpty()) base.filter { it.absolutePath.contains(titleHint.take(30)) } else base
        val cand = (if (withHint.isNotEmpty()) withHint else base)
            .filterNot { AUX.containsMatchIn(it.name) }
            .filter { it.length() > 0 }
            .map { it.absolutePath }
        return cand
    }

    fun verifyAndScan(ctx: Context, dir: File, titleHint: String): List<String> {
        val files = verify(dir, titleHint)
        runCatching {
            android.media.MediaScannerConnection.scanFile(ctx, files.toTypedArray(), null, null)
        }
        return files
    }
}
