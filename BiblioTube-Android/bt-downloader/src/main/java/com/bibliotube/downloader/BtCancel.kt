package com.bibliotube.downloader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

// Cancel desde notificación (espejo NotificationActionReceiver + destroyProcessById).
class BtCancelReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TASK) ?: return
        runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(id) }
        BtQueueHolder.queue?.cancel(id)
        runCatching {
            val m = ctx.getSystemService(android.app.NotificationManager::class.java)
            m?.cancel(id.hashCode())
        }
    }
    companion object {
        const val ACTION = "com.bibliotube.downloader.CANCEL"
        const val EXTRA_TASK = "task_id"
        fun pending(ctx: Context, taskId: String): PendingIntent {
            val i = Intent(ctx, BtCancelReceiver::class.java).setAction(ACTION).putExtra(EXTRA_TASK, taskId)
            return PendingIntent.getBroadcast(ctx, taskId.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

object BtQueueHolder { var queue: BtQueue? = null }

fun NotificationCompat.Builder.withCancel(ctx: Context, taskId: String): NotificationCompat.Builder =
    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", BtCancelReceiver.pending(ctx, taskId))
