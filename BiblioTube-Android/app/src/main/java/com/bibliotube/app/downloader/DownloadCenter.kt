package com.bibliotube.app.downloader

import android.content.Context
import com.bibliotube.downloader.BtQueue

object DownloadCenter {
    @Volatile private var instance: BtQueue? = null

    fun queue(context: Context): BtQueue =
        instance ?: synchronized(this) {
            instance ?: BtQueue(DownloaderProvider.get(context)).also { instance = it }
        }

    private val links = mutableMapOf<String, Pair<String, Boolean>>()

    @Synchronized
    fun link(taskId: String, videoId: String, uploadToDrive: Boolean) {
        links[taskId] = videoId to uploadToDrive
    }

    @Synchronized
    fun takeLink(taskId: String): Pair<String, Boolean>? = links.remove(taskId)
}
