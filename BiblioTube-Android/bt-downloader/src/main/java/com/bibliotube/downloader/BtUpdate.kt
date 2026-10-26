package com.bibliotube.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Canales update espejo UpdateUtil.updateYtDlp (stable/nightly) + persistencia versión.
object BtUpdate {
    const val STABLE = 0
    const val NIGHTLY = 1

    suspend fun updateYtDlp(ctx: Context, channel: Int = NIGHTLY): String? =
        withContext(Dispatchers.IO) {
            val ch = if (channel == NIGHTLY) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
            YoutubeDL.getInstance().updateYoutubeDL(ctx, ch).also {
                if (it == YoutubeDL.UpdateStatus.DONE) {
                    YoutubeDL.getInstance().version(ctx)?.let { v ->
                        ctx.getSharedPreferences("bt", Context.MODE_PRIVATE).edit().putString("ytdlp_ver", v).apply()
                    }
                }
            }?.name
        }

    fun currentVersion(ctx: Context): String? = runCatching {
        YoutubeDL.getInstance().version(ctx)
    }.getOrNull()
}
