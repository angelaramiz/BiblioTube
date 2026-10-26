package com.bibliotube.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.api.SERVER_URL
import com.bibliotube.app.data.SessionManager
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val token = SessionManager.get(applicationContext).session.value?.accessToken ?: return Result.failure()
            val api = ApiClient(SERVER_URL).also { it.authToken = token }
            val repo = VaultRepository(VaultDatabase.get(applicationContext), api)
            val err = repo.sync()
            if (err != null) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
        }
    }
}
