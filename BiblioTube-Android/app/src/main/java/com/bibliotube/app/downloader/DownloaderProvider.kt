package com.bibliotube.app.downloader

import android.content.Context
import com.bibliotube.downloader.BtDownloader
import com.bibliotube.downloader.BtFiles
import com.bibliotube.downloader.BtStorage

object DownloaderProvider {
    @Volatile private var instance: BtDownloader? = null
    @Volatile private var initialized = false

    fun get(context: Context): BtDownloader {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: BtDownloader(
                app,
                BtStorage.publicDir(),
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

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching { get(context).init() }
            initialized = true
        }
    }
}
