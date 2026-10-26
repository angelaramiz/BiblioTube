package com.bibliotube.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bibliotube.app.data.local.VaultDatabase
import java.util.concurrent.TimeUnit

const val CHANNEL_REMINDERS = "bibliotube_reminders"

fun ensureReminderChannel(context: Context) {
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (mgr.getNotificationChannel(CHANNEL_REMINDERS) == null) {
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

fun scheduleReminder(context: Context, videoId: String, titulo: String, atMillis: Long, repeat: String) {
    val delay = atMillis - System.currentTimeMillis()
    if (delay <= 0) return
    val input = Data.Builder()
        .putString("videoId", videoId)
        .putString("titulo", titulo)
        .putString("repeat", repeat)
        .putLong("atMillis", atMillis)
        .build()
    val req = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(input)
        .addTag("reminder_$videoId")
        .build()
    WorkManager.getInstance(context).enqueue(req)
}

fun cancelReminder(context: Context, videoId: String) {
    WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$videoId")
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val titulo = inputData.getString("titulo") ?: "Video"
        val repeat = inputData.getString("repeat") ?: "none"
        val atMillis = inputData.getLong("atMillis", 0L)

        ensureReminderChannel(applicationContext)
        if (ActivityCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("BiblioTube: recordatorio")
                .setContentText(titulo)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(videoId.hashCode(), notif)
        }
        if (repeat != "none") {
            val next = when (repeat) {
                "daily" -> atMillis + 24 * 60 * 60 * 1000L
                else -> atMillis + 7 * 24 * 60 * 60 * 1000L
            }
            scheduleReminder(applicationContext, videoId, titulo, next, repeat)
            VaultDatabase.get(applicationContext).videos().byId(videoId)?.let {
                VaultDatabase.get(applicationContext).videos().upsert(it.copy(recordatorioAt = next, dirty = true))
            }
        } else {
            VaultDatabase.get(applicationContext).videos().byId(videoId)?.let {
                VaultDatabase.get(applicationContext).videos().upsert(it.copy(recordatorioAt = null, dirty = true))
            }
        }
        return Result.success()
    }
}
