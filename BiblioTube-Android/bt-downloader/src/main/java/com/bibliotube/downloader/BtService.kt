package com.bibliotube.downloader

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Foreground vacío espejo DownloadService (solo mantiene viva la notif de descarga).
class BtDownloadService : Service() {
    override fun onBind(intent: Intent): IBinder {
        val pi = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, BtNotifications.CHANNEL)
            .setContentTitle("BiblioTube descargas activas")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1001, n)
        return Binder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
        return super.onUnbind(intent)
    }
}
