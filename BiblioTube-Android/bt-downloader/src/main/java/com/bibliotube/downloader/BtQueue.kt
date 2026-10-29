package com.bibliotube.downloader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

// Cola simplificada espejo DownloaderV2: FetchingInfo -> Ready -> Running -> Done/Error, con bacup en memoria.
enum class BtTaskState { Idle, FetchingInfo, Ready, Running, Done, Error, Canceled }

data class BtTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val isAudio: Boolean = false,
    val videoOptions: BtVideoOptions = BtVideoOptions.standard(),
    val audioOptions: BtAudioOptions = BtAudioOptions.standard(),
    val state: BtTaskState = BtTaskState.Idle,
    val progress: Float = 0f,
    val eta: Long = -1,
    val message: String = "",
    val files: List<String> = emptyList(),
    val error: String? = null,
)

class BtQueue(
    private val dl: BtDownloader,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxConcurrency: Int = 2,
) {
    private val scope = scope
    private val sem = Semaphore(maxConcurrency)
    private val _tasks = MutableStateFlow<Map<String, BtTask>>(emptyMap())
    val tasks: StateFlow<Map<String, BtTask>> = _tasks

    init { BtQueueHolder.queue = this }

    fun enqueue(url: String, isAudio: Boolean = false, vo: BtVideoOptions = BtVideoOptions.standard(), ao: BtAudioOptions = BtAudioOptions.standard()): String {
        val clean = validateBtUrl(url).getOrElse { throw IllegalArgumentException(it.message) }
        val t = BtTask(url = clean, isAudio = isAudio, videoOptions = vo, audioOptions = ao)
        _tasks.value += (t.id to t)
        scope.launch { run(t.id) }
        return t.id
    }

    /** Lote playlist: --flat-playlist -> N enqueues con playlistItem. */
    suspend fun enqueuePlaylist(url: String, isAudio: Boolean = false, vo: BtVideoOptions, ao: BtAudioOptions = BtAudioOptions.standard(), max: Int = 50): List<String> {
        val pl = dl.fetchPlaylist(url).getOrThrow()
        val entries = pl.entries.orEmpty().take(max)
        if (entries.isEmpty()) return listOf(enqueue(url, isAudio, vo, ao))
        return entries.mapIndexed { i, e ->
            val itemUrl = e.url ?: url
            val voItem = vo.copy(downloadPlaylist = true, playlistItem = i + 1)
            enqueue(itemUrl, isAudio, voItem, ao)
        }
    }

    fun cancel(id: String) {
        dl.cancel(id)
        _tasks.value[id]?.let { _tasks.value += (id to it.copy(state = BtTaskState.Canceled)) }
    }

    private suspend fun run(id: String) = sem.withPermit {
        var t = _tasks.value[id] ?: return@withPermit
        try {
            _tasks.value += (id to t.copy(state = BtTaskState.FetchingInfo))
            val info = dl.fetchInfo(t.url).getOrThrow()
            t = _tasks.value[id] ?: return@withPermit
            _tasks.value += (id to t.copy(state = BtTaskState.Ready))
            _tasks.value += (id to t.copy(state = BtTaskState.Running))
            val res = if (t.isAudio) {
                dl.downloadCustom(t.url, id, t.videoOptions, audioOptions = t.audioOptions) { p, e, m ->
                    _tasks.value[id]?.let { cur -> _tasks.value += (id to cur.copy(progress = p, eta = e, message = m)) }
                }
            } else {
                dl.downloadCustom(t.url, id, t.videoOptions) { p, e, m ->
                    _tasks.value[id]?.let { cur -> _tasks.value += (id to cur.copy(progress = p, eta = e, message = m)) }
                }
            }
            val files = res.getOrThrow()
            // info para historial: título se resuelve en caller vía fetchInfo previo si se requiere
            _tasks.value[id]?.let { cur ->
                _tasks.value += (id to cur.copy(state = BtTaskState.Done, progress = 100f, files = files))
                BtHistory.memory().insert(info.title, t.url, files.firstOrNull() ?: "", info.extractorKey)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val be = BtErrorMapper.map(e)
            // Error crudo de yt-dlp (sirve para distinguir bot / edad / miembros / DRM).
            val raw = (e.cause?.message ?: e.message ?: "").replace("\n", " ").trim()
            BtLog.d("${be.userMessage()} :: ${raw.ifEmpty { "sin detalle" }}")
            _tasks.value[id]?.let { cur ->
                _tasks.value += (id to cur.copy(state = BtTaskState.Error, error = be.userMessage()))
            }
        }
    }
}
