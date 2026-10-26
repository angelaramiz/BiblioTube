package com.bibliotube.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Canal notif + init pesado yt-dlp/ffmpeg/aria2c en IO (espejo Seal App.onCreate)
        com.bibliotube.downloader.BtNotifications.ensureChannel(this)
        Thread {
            runCatching {
                com.bibliotube.downloader.BtDownloader(
                    this, com.bibliotube.downloader.BtStorage.publicDir(),
                    com.bibliotube.downloader.BtStorage.tmpDir(this),
                ).init()
            }
        }.start()
        // POST_NOTIFICATIONS Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        setContent { App() }
    }
}
