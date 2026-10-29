package com.bibliotube.downloader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import java.io.File

// Import cookies WebView -> Netscape (espejo Seal getCookieListFromDatabase/toCookiesFileContent).
object BtCookies {
    private const val HEADER = "# Netscape HTTP Cookie File\n"

    /** Cookies que solo existen con sesion iniciada en YouTube. */
    private val SESSION_NAMES = listOf("SID", "HSID", "SSID", "SAPISID", "LOGIN_INFO", "__Secure-3PSID")

    /**
     * true si el cookies.txt tiene sesion real (no solo cookies de invitado).
     * Las cookies de invitado sirven para nada en contenido con edad/privado.
     */
    fun isSignedIn(ctx: Context, file: File? = null): Boolean = runCatching {
        val f = file ?: BtFiles.cookiesFile(ctx)
        if (!f.exists() || f.length() == 0L) return false
        val names = f.readLines().mapNotNull { line ->
            if (line.startsWith("#") || line.isBlank()) null else line.split('\t').getOrNull(5)
        }.toSet()
        SESSION_NAMES.any { it in names }
    }.getOrDefault(false)

    /**
     * Exporta las cookies del WebView interno a formato Netscape.
     * Copia la BD antes de leerla: WebView la tiene abierta y SQLite puede bloquear.
     */
    fun exportWebView(ctx: Context, out: File? = null): Result<File> = runCatching {
        val mgr = CookieManager.getInstance()
        mgr.flush()
        val src = File(ctx.dataDir, "app_webview/Default/Cookies")
        if (!src.exists()) throw IllegalStateException("Sin sesion: inicia sesion en YouTube primero")

        val copy = File(ctx.cacheDir, "bt_cookies_snapshot.db")
        src.copyTo(copy, overwrite = true)
        // WAL: sin estos el snapshot puede perder las escrituras recientes.
        for (suffix in listOf("-wal", "-shm")) {
            val extra = File(src.absolutePath + suffix)
            if (extra.exists()) extra.copyTo(File(copy.absolutePath + suffix), overwrite = true)
        }

        val sb = StringBuilder(HEADER)
        var total = 0
        SQLiteDatabase.openDatabase(copy.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { s ->
            s.query(
                "cookies",
                arrayOf("host_key", "expires_utc", "path", "name", "value", "is_secure"),
                null, null, null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val host = c.getString(0) ?: continue
                    val exp = c.getLong(1) / 1000000 - 11644473600L
                    val path = c.getString(2) ?: "/"
                    val name = c.getString(3) ?: continue
                    val value = c.getString(4) ?: ""
                    val sec = c.getInt(5) == 1
                    val h = if (host.startsWith(".")) host else ".$host"
                    sb.append("$h\tTRUE\t$path\t$sec\t$exp\t$name\t$value\n")
                    total++
                }
            }
        }
        File(copy.absolutePath).delete()
        File(copy.absolutePath + "-wal").delete()
        File(copy.absolutePath + "-shm").delete()
        if (total == 0) throw IllegalStateException("El WebView no tiene cookies: inicia sesion primero")
        val dst = out ?: BtFiles.cookiesFile(ctx)
        dst.parentFile?.mkdirs()
        dst.writeText(sb.toString())
        dst
    }
}
