package com.bibliotube.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bibliotube.app.data.AppLog
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.drive.DriveAccount
import com.bibliotube.app.data.drive.DriveToken
import com.bibliotube.app.data.drive.DriveUpload
import com.bibliotube.app.data.local.VaultDatabase
import java.io.File

class DriveUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val db = VaultDatabase.get(applicationContext)
        val api = com.bibliotube.app.api.ApiClient(com.bibliotube.app.api.SERVER_URL)
        val repo = VaultRepository(db, api)
        val video = db.videos().byId(videoId) ?: run {
            AppLog.d("drive", "video no encontrado: $videoId")
            return Result.failure()
        }
        val file = File(video.localPath)
        if (!file.exists()) {
            AppLog.d("drive", "archivo ausente: ${video.localPath}")
            return Result.failure()
        }
        repo.setDriveState(videoId, "uploading")
        AppLog.d("drive", "subiendo ${file.name} (${file.length() / 1024} KB)")
        return when (val t = DriveAccount.token(applicationContext)) {
            is DriveToken.Ok -> {
                when (val r = DriveUpload().upload(t.token, file, "${video.titulo}.mp4")) {
                    is DriveUpload.Result.Done -> {
                        repo.setDriveState(videoId, "done", r.fileId)
                        SyncWorker.enqueue(applicationContext)
                        AppLog.d("drive", "subida OK: ${r.fileId}")
                        Result.success()
                    }
                    is DriveUpload.Result.Failed -> {
                        if (r.message == "AUTH_EXPIRED") DriveAccount.invalidate(applicationContext, t.token)
                        repo.setDriveState(videoId, "error")
                        AppLog.d("drive", "subida fallo: ${r.message}")
                        Result.failure()
                    }
                }
            }
            is DriveToken.NeedsAction -> {
                repo.setDriveState(videoId, "error")
                AppLog.d("drive", "cuenta requiere accion del usuario")
                Result.failure()
            }
            is DriveToken.Error -> {
                repo.setDriveState(videoId, "error")
                AppLog.d("drive", "token error: ${t.message}")
                Result.failure()
            }
        }
    }

    companion object {
        fun enqueue(context: Context, videoId: String) {
            val input = Data.Builder().putString("videoId", videoId).build()
            WorkManager.getInstance(context)
                .enqueue(
                    OneTimeWorkRequestBuilder<DriveUploadWorker>()
                        .setInputData(input)
                        .addTag("drive_$videoId")
                        .build()
                )
        }
    }
}
