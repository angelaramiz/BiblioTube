package com.bibliotube.app.downloader

import android.content.Context
import com.bibliotube.downloader.BtDownloader
import com.bibliotube.downloader.BtFiles
import com.bibliotube.downloader.BtStorage
import java.io.File

object DownloaderProvider {
    @Volatile private var instance: BtDownloader? = null
    @Volatile private var initialized = false
    @Volatile private var lastError: String? = null

    fun get(context: Context): BtDownloader {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: BtDownloader(
                app,
                BtStorage.publicDir(app),
                BtStorage.tmpDir(app),
                BtFiles.cookiesFile(app),
                BtFiles.archiveFile(app),
            ).also { instance = it }
        }
    }

    fun isReady(): Boolean {
        val inst = instance ?: return false
        return initialized && runCatching { inst.isReady() }.getOrDefault(false)
    }

    /** null mientras no se haya intentado; motivo exacto si el ultimo init fallo. */
    fun failReason(): String? = lastError

    /** Runtime extraido por la libreria (python/ffmpeg/aria2): puede quedar corrupto. */
    private fun runtimeDirs(app: Context): List<File> =
        (app.filesDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && Regex("(?i)^(youtubedl|ffmpeg|aria2)").containsMatchIn(it.name) }

    /** Borra el runtime y obliga a re-extraerlo (reparacion). */
    fun reset(context: Context) {
        synchronized(this) {
            val app = context.applicationContext
            instance = null
            initialized = false
            lastError = null
            runtimeDirs(app).forEach { runCatching { it.deleteRecursively() } }
        }
    }

    fun ensureInit(context: Context) {
        if (initialized && isReady()) return
        synchronized(this) {
            if (initialized && isReady()) return
            val app = context.applicationContext
            var fail = get(context).initDetailed()
            if (fail != null) {
                // Primera vez que falla: el runtime pudo quedar a medias. Se limpia y se reintenta.
                com.bibliotube.app.data.AppLog.d("dl", "init fallo ($fail), reparando runtime…")
                reset(context)
                fail = get(context).initDetailed()
            }
            val info = "abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()} " +
                "libre=${app.filesDir.usableSpace / 1024 / 1024}MB"
            if (fail == null && runCatching { get(context).isReady() }.getOrDefault(false)) {
                initialized = true
                lastError = null
                com.bibliotube.app.data.AppLog.d("dl", "motor yt-dlp listo ($info)")
            } else {
                initialized = false
                lastError = "${fail ?: "isReady() falso"} ($info)"
                com.bibliotube.app.data.AppLog.d("dl", "init fallo, reintentable: $lastError")
            }
        }
    }

    /** Version del binario yt-dlp en uso (para diagnostico). */
    fun version(context: Context): String? =
        runCatching { get(context).ytDlpVersion() }.getOrNull()

    /**
     * YouTube cambia cada pocas semanas: un yt-dlp viejo acaba pidiendo
     * "sign in to confirm you're not a bot". Se actualiza como maximo 1/dia.
     */
    fun updateIfStale(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("bt", Context.MODE_PRIVATE)
        val last = prefs.getLong("ytdlp_checked_at", 0L)
        val day = 24L * 60 * 60 * 1000
        if (!force && System.currentTimeMillis() - last < day) return
        prefs.edit().putLong("ytdlp_checked_at", System.currentTimeMillis()).apply()
        val before = version(context)
        val ok = runCatching { get(context).updateYtDlp("nightly") }.getOrDefault(false)
        val after = version(context)
        com.bibliotube.app.data.AppLog.d(
            "dl", "yt-dlp $before -> $after (actualizado=${if (ok) "si" else "no"})"
        )
    }
}
