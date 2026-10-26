package com.bibliotube.downloader

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Ejemplo de acople en BiblioTube (copiar a app, no parte del lib).
class BtViewModel(private val dl: BtDownloader) : ViewModel() {
    var status by mutableStateOf("idle")
    var progress by mutableFloatStateOf(0f)

    fun standard(url: String, dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            status = "fetching"
            dl.fetchInfo(url).onFailure { status = "info-error: ${it.message}"; return@launch }
            status = "downloading"
            dl.downloadStandard(url) { p, _, _ -> progress = p / 100f }
                .onSuccess { status = "done ${it.size} files" }
                .onFailure { status = "error: ${it.message}" }
        }
    }

    fun custom(url: String) {
        val opts = BtVideoOptions.custom {
            resolution(BtVideoOptions.RES_720)
            subtitles(embed = true)
            clips(listOf(BtVideoClip(10, 60)))
        }.copy(useAria2c = true, concurrentFragments = 1, downloadPlaylist = false)
        viewModelScope.launch(Dispatchers.IO) {
            status = "downloading-custom"
            dl.downloadCustom(url, url, opts) { p, _, _ -> progress = p / 100f }
                .onSuccess { status = "done" }
                .onFailure { status = "error: ${it.message}" }
        }
    }
}
