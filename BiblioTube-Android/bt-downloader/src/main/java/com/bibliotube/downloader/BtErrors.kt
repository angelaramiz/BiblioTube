package com.bibliotube.downloader

// Mapeo de fallos yt-dlp a errores accionables (sin UI).
sealed interface BtDownloadError {
    data object Canceled : BtDownloadError
    data object NoSpace : BtDownloadError
    data object NoNetwork : BtDownloadError
    data object UnsupportedUrl : BtDownloadError
    data object CookiesRequired : BtDownloadError
    data object FfmpegMissing : BtDownloadError
    data class Failed(val msg: String) : BtDownloadError
    fun userMessage(): String = when (this) {
        Canceled -> "Descarga cancelada"
        NoSpace -> "Sin espacio en destino"
        NoNetwork -> "Sin red o timeout (reintenta)"
        UnsupportedUrl -> "Sitio no soportado por yt-dlp"
        CookiesRequired -> "YouTube pidió verificación (bot/cookies): importa cookies o reintenta"
        FfmpegMissing -> "Falta ffmpeg para mux 1080p+"
        is Failed -> msg.take(300)
    }
}

object BtErrorMapper {
    fun map(t: Throwable): BtDownloadError {
        if (t is com.yausername.youtubedl_android.YoutubeDL.CanceledException) return BtDownloadError.Canceled
        val m = (t.message ?: "").lowercase()
        return when {
            "canceled" in m || "cancelled" in m -> BtDownloadError.Canceled
            "no space" in m || "enospc" in m -> BtDownloadError.NoSpace
            "unable to download" in m && "network" in m -> BtDownloadError.NoNetwork
            "socket" in m || "timeout" in m || "failed to resolve" in m -> BtDownloadError.NoNetwork
            "unsupported url" in m || "no video formats found" in m -> BtDownloadError.UnsupportedUrl
            "sign in" in m || "cookies" in m || "age" in m || "private" in m || "login" in m -> BtDownloadError.CookiesRequired
            "ffmpeg" in m -> BtDownloadError.FfmpegMissing
            else -> BtDownloadError.Failed(t.message ?: "fallo desconocido")
        }
    }
}

fun validateBtUrl(raw: String): Result<String> {
    val u = raw.trim()
    if (u.isEmpty()) return Result.failure(IllegalArgumentException("URL vacía"))
    if (u.contains("\n") || u.contains(" ")) return Result.failure(IllegalArgumentException("URL con espacios/saltos"))
    if (!(u.startsWith("http://") || u.startsWith("https://"))) return Result.failure(IllegalArgumentException("Solo http/https"))
    if (u.length > 2048) return Result.failure(IllegalArgumentException("URL demasiado larga"))
    if (u.startsWith("-") || u.contains("--config")) return Result.failure(IllegalArgumentException("URL no válida"))
    return Result.success(u)
}
