package com.bibliotube.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

// Notificaciones espejo NotificationUtil (progreso/error). El host aporta channelId e iconos.
object BtNotifications {
    const val CHANNEL = "bt_downloads"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = ctx.getSystemService(NotificationManager::class.java)
            m?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun progress(ctx: Context, id: Int, title: String, pct: Int, text: String = "", taskId: String? = null) {
        val m = ctx.getSystemService(NotificationManager::class.java) ?: return
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText(text.ifEmpty { "$pct%" })
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, pct, pct < 0)
            .setOngoing(true)
        if (taskId != null) b.withCancel(ctx, taskId)
        m.notify(id, b.build())
    }

    fun done(ctx: Context, id: Int, title: String) {
        val m = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText("Completado")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .build()
        m.notify(id, n)
    }

    fun error(ctx: Context, id: Int, title: String, err: String) {
        val m = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText(err.take(200))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .build()
        m.notify(id, n)
    }
}
