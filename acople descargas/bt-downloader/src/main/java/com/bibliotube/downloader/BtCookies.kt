package com.bibliotube.downloader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import java.io.File

// Import cookies WebView -> Netscape (espejo Seal getCookieListFromDatabase/toCookiesFileContent).
object BtCookies {
    private const val HEADER = "# Netscape HTTP Cookie File\n"

    fun exportWebView(ctx: Context, out: File? = null): Result<File> = runCatching {
        val mgr = CookieManager.getInstance()
        mgr.flush()
        val db = File(ctx.dataDir.resolve("app_webview/Default/Cookies").absolutePath)
        if (!db.exists()) throw IllegalStateException("Sin DB WebView (abre un WebView primero)")
        SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { s ->
            val sb = StringBuilder(HEADER)
            s.query("cookies", arrayOf("host_key", "expires_utc", "path", "name", "value", "is_secure"), null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    val host = c.getString(0); val exp = c.getLong(1) / 1000000 - 11644473600L
                    val path = c.getString(2); val name = c.getString(3); val value = c.getString(4)
                    val sec = c.getInt(5) == 1
                    val h = if (host.startsWith(".")) host else ".$host"
                    sb.append("$h\tTRUE\t$path\t$sec\t$exp\t$name\t$value\n")
                }
            }
            val dst = out ?: BtFiles.cookiesFile(ctx)
            dst.writeText(sb.toString())
            dst
        }
    }
}
