package com.bibliotube.downloader

// Mapeo de fallos yt-dlp a errores accionables (sin UI).
sealed interface BtDownloadError {
    data object Canceled : BtDownloadError
    data object NoSpace : BtDownloadError
    data object NoNetwork : BtDownloadError
    data object UnsupportedUrl : BtDownloadError
    data object CookiesRequired : BtDownloadError
    data object BotCheck : BtDownloadError
    data object AgeRestricted : BtDownloadError
    data object MembersOnly : BtDownloadError
    data object PrivateVideo : BtDownloadError
    data object FfmpegMissing : BtDownloadError
    data class Failed(val msg: String) : BtDownloadError

    fun userMessage(): String = when (this) {
        Canceled -> "Descarga cancelada"
        NoSpace -> "Sin espacio en destino"
        NoNetwork -> "Sin red o timeout (reintenta)"
        UnsupportedUrl -> "Sitio no soportado por yt-dlp"
        CookiesRequired -> "Requiere iniciar sesion (cookies) para este video"
        BotCheck -> "YouTube pidio verificacion anti-bot: reintenta o importa cookies"
        AgeRestricted -> "Video con restriccion de edad: importa cookies de una cuenta"
        MembersOnly -> "Video solo para miembros del canal: requiere cookies con acceso"
        PrivateVideo -> "Video privado o eliminado"
        FfmpegMissing -> "Falta ffmpeg para mux 1080p+"
        is Failed -> msg.take(300)
    }

    /** Reintentar con otro player_client solo tiene sentido para bloqueos de cliente. */
    fun worthAnotherClient(): Boolean = when (this) {
        // "no disponible" puede ser regional: otro cliente a veces lo resuelve.
        // android_vr/web_embedded ademas suelen saltar la restriccion de edad.
        BotCheck, CookiesRequired, AgeRestricted, PrivateVideo, is Failed -> true
        else -> false
    }
}

object BtErrorMapper {
    fun map(t: Throwable): BtDownloadError {
        if (t is com.yausername.youtubedl_android.YoutubeDL.CanceledException) return BtDownloadError.Canceled
        val m = (t.message ?: "").lowercase()
        return when {
            "canceled" in m || "cancelled" in m -> BtDownloadError.Canceled
            "no space" in m || "enospc" in m -> BtDownloadError.NoSpace
            // Contenido restringido (no se arregla con otro cliente).
            "members-only" in m || "join this channel" in m || "channel members" in m ||
                "miembros" in m -> BtDownloadError.MembersOnly
            "video is unavailable" in m || "video unavailable" in m || "this video is private" in m ||
                "private video" in m || "removed by the uploader" in m || "no está disponible" in m ||
                "no longer available" in m -> BtDownloadError.PrivateVideo
            "age" in m && ("restrict" in m || "confirm your age" in m || "sign in" in m) -> BtDownloadError.AgeRestricted
            "confirm your age" in m || "inappropriate for some users" in m -> BtDownloadError.AgeRestricted
            // Verificacion anti-bot (se arregla con yt-dlp al dia o cookies).
            "not a bot" in m || "confirm you're not a bot" in m || "confirm you’re not a bot" in m ->
                BtDownloadError.BotCheck
            "po token" in m || "po_token" in m || "sabr" in m -> BtDownloadError.BotCheck
            "unable to download" in m && "network" in m -> BtDownloadError.NoNetwork
            "429" in m || "too many requests" in m ->
                BtDownloadError.Failed("YouTube limito las peticiones (429): espera unos minutos")
            "socket" in m || "timeout" in m || "failed to resolve" in m -> BtDownloadError.NoNetwork
            "unsupported url" in m || "no video formats found" in m -> BtDownloadError.UnsupportedUrl
            "sign in" in m || "cookies" in m || "login" in m -> BtDownloadError.CookiesRequired
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
