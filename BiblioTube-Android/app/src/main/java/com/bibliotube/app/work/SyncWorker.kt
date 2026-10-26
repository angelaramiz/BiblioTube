package com.bibliotube.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.remote.AuthClient

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("bibliotube_prefs", Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
            val token = AuthClient(applicationContext).session()?.accessToken ?: return Result.failure()
            val api = ApiClient(serverUrl).also { it.authToken = token }
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
