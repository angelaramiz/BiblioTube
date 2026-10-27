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

    fun init() {
        YoutubeDL.init(context)
        FFmpeg.init(context)
        Aria2c.init(context)
    }

    fun isReady(): Boolean = runCatching {
        YoutubeDL.getInstance()
        true
    }.getOrDefault(false)

    fun fetchInfo(url: String, playlistIndex: Int? = null): Result<BtVideoInfo> = runCatching {
        val req = BtRequestBuilder.infoRequest(url, playlistIndex)
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
        // 1. resolver URL real vía info (como Seal: originalUrl ?: webpageUrl)
        val info = fetchInfo(clean, options.playlistItem.takeIf { it != 0 }).getOrThrow()
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
        files
    }.recoverCatching { t ->
        if (t is IllegalStateException) throw t
        throw IllegalStateException(BtErrorMapper.map(t).userMessage(), t)
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
