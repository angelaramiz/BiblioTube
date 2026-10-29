package com.bibliotube.downloader

/**
 * Puente de logs del modulo de descarga hacia el host (la app lo conecta a AppLog).
 * La libreria no depende de la app: sin sink, va a logcat.
 */
object BtLog {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun d(msg: String) {
        val line = msg.take(600)
        val s = sink
        if (s != null) runCatching { s(line) } else android.util.Log.d("BiblioTube", "[dl] $line")
    }
}
