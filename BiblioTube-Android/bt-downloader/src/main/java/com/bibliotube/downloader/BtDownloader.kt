package com.bibliotube.downloader

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.serialization.json.Json

// Fachada de acople: init + fetchInfo + 2 modos + cancel + update.
// No UI, no permisos: el host aporta dirs y muestra progreso.
class BtDownloader(
    private val context: Context,
    private val videoDir: File,
    private val tempDir: File? = null,
    private val cookiesFile: File? = null,
    private val archiveFile: File? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * QuickJS empaquetado como libqjs.so (extraido a nativeLibraryDir).
     * Sin runtime JS yt-dlp no puede resolver el desafio n-sig de YouTube.
     */
    private val jsRuntime: String? = runCatching {
        val f = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (!f.exists()) null else {
            f.setExecutable(true)
            f.absolutePath
        }
    }.getOrNull()

    fun init() {
        initDetailed()?.let { throw RuntimeException(it) }
    }

    /** null si OK; si no, "paso: causa". */
    fun initDetailed(): String? {
        val steps = listOf(
            "youtubedl" to { YoutubeDL.init(context) },
            "ffmpeg" to { FFmpeg.init(context) },
            "aria2c" to { Aria2c.init(context) },
        )
        for ((name, run) in steps) {
            val r = runCatching { run() }
            if (r.isFailure) {
                val e = r.exceptionOrNull()
                return "$name: ${e?.javaClass?.simpleName}: ${e?.message}"
            }
        }
        return null
    }

    fun isReady(): Boolean = runCatching {
        YoutubeDL.getInstance()
        true
    }.getOrDefault(false)

    /**
     * Info con fallback de cliente: los bloqueos (edad/bot/regional) ocurren al
     * resolver la info, antes de descargar, asi que la cadena debe aplicar aqui.
     */
    fun fetchInfoResilient(url: String, playlistIndex: Int? = null): Result<BtVideoInfo> {
        val clients = youtubeClientsFor(url)
        val usingCookies = cookiesFile?.let { it.exists() && it.length() > 0 } == true
        BtLog.d("info: clientes=${clients.joinToString { it ?: "auto" }} cookies=$usingCookies js=${jsRuntime != null}")
        var last: Throwable? = null
        for ((i, client) in clients.withIndex()) {
            val r = fetchInfo(url, playlistIndex, client)
            if (r.isSuccess) return r
            last = r.exceptionOrNull()
            val mapped = BtErrorMapper.map(last ?: RuntimeException("sin detalle"))
            if (i == clients.lastIndex || !mapped.worthAnotherClient()) return r
            BtLog.d("info con cliente=${client ?: "auto"} fallo (${mapped.userMessage()}), probando otro")
        }
        return Result.failure(last ?: IllegalStateException("sin info"))
    }

    fun fetchInfo(url: String, playlistIndex: Int? = null, youtubeClient: String? = null): Result<BtVideoInfo> = runCatching {
        val req = BtRequestBuilder.infoRequest(url, playlistIndex, youtubeClient, cookiesFile?.absolutePath, jsRuntime)
        val out = YoutubeDL.getInstance().execute(req, null, null).out
        json.decodeFromString<BtVideoInfo>(out)
    }

    fun fetchPlaylist(url: String): Result<BtPlaylistInfo> = runCatching {
        val clean = validateBtUrl(url).getOrThrow()
        val out = YoutubeDL.getInstance().execute(BtRequestBuilder.playlistRequest(clean), null, null).out
        // flat-playlist devuelve playlist o video único; normaliza a playlist
        runCatching { json.decodeFromString<BtPlaylistInfo>(out) }.getOrElse {
            val v = json.decodeFromString<BtVideoInfo>(out)
            BtPlaylistInfo(title = v.title, type = "playlist", entries = listOf(BtPlaylistEntry(id = v.id, url = v.webpageUrl ?: clean, title = v.title)), webpageUrl = clean, originalUrl = clean)
        }
    }

    /** Modo 1 Standard. */
    fun downloadStandard(
        url: String,
        taskId: String = url,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): Result<List<String>> =
        downloadCustom(url, taskId, BtVideoOptions.standard(), onProgress = onProgress)

    /** Modo 2 Personalizada. */
    fun downloadCustom(
        url: String,
        taskId: String = url,
        options: BtVideoOptions,
        audioDir: File? = null,
        audioOptions: BtAudioOptions? = null,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): Result<List<String>> = runCatching {
        val clean = validateBtUrl(url).getOrThrow()
        val clients = youtubeClientsFor(clean)
        var last: Throwable? = null
        for ((i, client) in clients.withIndex()) {
            try {
                return@runCatching attemptDownload(clean, taskId, options, audioDir, audioOptions, onProgress, client)
            } catch (t: Throwable) {
                // Errores propios (archive/espacio) no se reintentan con otro cliente.
                if (t is IllegalStateException) throw t
                last = t
                if (i == clients.lastIndex) throw t
                val mapped = BtErrorMapper.map(t)
                // Edad/miembros/privado no mejoran cambiando cliente: se corta ya.
                if (!mapped.worthAnotherClient()) throw t
            }
        }
        throw last ?: IllegalStateException("fallo desconocido")
    }.recoverCatching { t ->
        if (t is IllegalStateException) throw t
        throw IllegalStateException(BtErrorMapper.map(t).userMessage(), t)
    }

    /**
     * YouTube cambia cada pocos meses: se prueban clientes que hoy no exigen PO Token.
     * null = dejar que yt-dlp elija (default).
     */
    private fun youtubeClientsFor(url: String): List<String?> {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return listOf(null)
        val isYt = host.endsWith("youtube.com") || host.endsWith("youtu.be") || host.endsWith("youtube-nocookie.com")
        if (!isYt) return listOf(null)
        val withCookies = cookiesFile?.let { it.exists() && it.length() > 0 } == true
        // Con sesion: los clientes TV no necesitan el desafio JS (n-sig) y
        // tv_embedded esta pensado justo para contenido que exige cuenta.
        return if (withCookies) {
            // Con runtime JS, web_safari entra como ultimo recurso.
            listOf("tv_embedded", "tv_simply", "android_vr", "web_safari", null)
        } else {
            listOf("tv,web_safari", "android_vr", "web_embedded", null)
        }
    }

    private fun attemptDownload(
        clean: String,
        taskId: String,
        options: BtVideoOptions,
        audioDir: File?,
        audioOptions: BtAudioOptions?,
        onProgress: ((Float, Long, String) -> Unit)?,
        youtubeClient: String?,
    ): List<String> {
        val usingCookies = cookiesFile?.let { it.exists() && it.length() > 0 } == true
        BtLog.d("intento cliente=${youtubeClient ?: "auto"} cookies=$usingCookies")
        // 1. resolver URL real vía info (como Seal: originalUrl ?: webpageUrl)
        val info = fetchInfo(clean, options.playlistItem.takeIf { it != 0 }, youtubeClient).getOrThrow()
        if (options.useArchive && archiveFile != null && BtArchive.contains(archiveFile, info.extractor ?: info.extractorKey, info.id)) {
            throw IllegalStateException("Ya descargado (archive): ${info.extractorKey} ${info.id}")
        }
        val target = info.originalUrl ?: info.webpageUrl ?: clean
        val useAudio = audioOptions != null || info.vcodec == "none"
        val outDir = if (useAudio && audioDir != null) audioDir else videoDir
        if (!BtStorage.hasSpace(outDir, 100L * 1024 * 1024)) throw IllegalStateException("Sin espacio en destino")
        fun buildReq(noAria2c: Boolean = false): YoutubeDLRequest = YoutubeDLRequest(target).apply {
            with(BtRequestBuilder) {
                if (useAudio) audioOptions(audioOptions ?: BtAudioOptions.standard())
                else videoOptions(options)
                globalOptions(
                    (if (noAria2c) options.copy(useAria2c = false, concurrentFragments = 1) else options),
                    outDir.absolutePath +
                        (if (options.subdirExtractor && info.extractorKey.isNotEmpty()) "/${info.extractorKey}" else ""),
                    tempDir?.absolutePath,
                    cookiesFile?.absolutePath,
                    archiveFile?.absolutePath,
                    youtubeClient,
                    jsRuntime,
                )
            }
        }
        val execErr = try {
            YoutubeDL.getInstance().execute(buildReq(false), taskId, onProgress)
            null
        } catch (t: Throwable) {
            // fallback aria2c -> nativo una vez
            if (options.useAria2c && "aria2" in (t.message ?: "").lowercase()) {
                YoutubeDL.getInstance().execute(buildReq(true), taskId, onProgress)
                null
            } else t
        }
        if (execErr != null) throw execErr
        // 2. verificar salidas (título + filtro aux + tamaño>0)
        val files = BtOutput.verifyAndScan(context, outDir, info.title.ifEmpty { info.id })
        if (options.useArchive && archiveFile != null) BtArchive.mark(archiveFile, info.extractor ?: info.extractorKey, info.id)
        return files
    }

    /** Rama audio directa. */
    fun downloadAudio(
        url: String,
        taskId: String = url,
        videoOptions: BtVideoOptions = BtVideoOptions.standard(),
        audioOptions: BtAudioOptions = BtAudioOptions.standard(),
        audioDir: File? = null,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): Result<List<String>> = downloadCustom(url, taskId, videoOptions, audioDir ?: videoDir, audioOptions, onProgress)

    fun downloadCustomCommand(
        urls: List<String>,
        rawTemplate: String,
        taskId: String,
        options: BtVideoOptions,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): Result<String> = runCatching {
        val cfg = File(context.cacheDir, "bt_cmd_$taskId.txt").apply { writeText(rawTemplate) }
        val req = YoutubeDLRequest(urls).apply {
            addOption("--newline")
            addOption("--config-locations", cfg.absolutePath)
            addOption("-P", videoDir.absolutePath)
            if (options.useAria2c) addOption("--downloader", "libaria2c.so")
            if (options.useArchive && archiveFile != null) addOption("--download-archive", archiveFile.absolutePath)
        }
        YoutubeDL.getInstance().execute(req, taskId, onProgress).out
    }

    fun cancel(taskId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId); true }.getOrDefault(false)

    fun ytDlpVersion(): String? = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    fun updateYtDlp(channel: String = "nightly"): Boolean = runCatching {
        val ch = when (channel.lowercase()) {
            "stable" -> YoutubeDL.UpdateChannel.STABLE
            "master" -> YoutubeDL.UpdateChannel.MASTER
            else -> YoutubeDL.UpdateChannel.NIGHTLY
        }
        YoutubeDL.getInstance().updateYoutubeDL(context, ch)
        true
    }.getOrDefault(false)
}
