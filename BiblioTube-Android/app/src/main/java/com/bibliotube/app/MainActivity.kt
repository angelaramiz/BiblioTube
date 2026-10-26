package com.bibliotube.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Canal notif + init pesado yt-dlp/ffmpeg/aria2c en IO (singleton del provider).
        com.bibliotube.downloader.BtNotifications.ensureChannel(this)
        Thread {
            runCatching { com.bibliotube.app.downloader.DownloaderProvider.ensureInit(this) }
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
