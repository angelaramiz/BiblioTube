package com.bibliotube.app.data.drive

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.net.URLConnection
import java.util.concurrent.TimeUnit

private data class DriveFile(val id: String?)

class DriveUpload(
    private val http: OkHttpClient = defaultHttp(),
    private val chunkBytes: Int = 8 * 1024 * 1024
) {
    private val gson = Gson()

    sealed interface Result {
        data class Done(val fileId: String) : Result
        data class Failed(val message: String) : Result
    }

    fun upload(token: String, file: File, title: String, onFrac: (Float) -> Unit = {}): Result {
        if (!file.exists()) return Result.Failed("Archivo no encontrado")
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "video/mp4"
        return try {
            val session = startSession(token, title, mime)
                ?: return Result.Failed("No se pudo iniciar subida")
            putChunks(token, session, file, onFrac)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Error de subida")
        }
    }

    private fun startSession(token: String, title: String, mime: String): String? {
        val meta = gson.toJson(mapOf("name" to title, "mimeType" to mime))
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("X-Upload-Content-Type", mime)
            .post(meta.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            return res.header("Location")
        }
    }

    private fun putChunks(token: String, session: String, file: File, onFrac: (Float) -> Unit): Result {
        val total = file.length()
        var offset = 0L
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(chunkBytes)
            while (offset < total) {
                val n = raf.read(buf)
                if (n <= 0) break
                val end = offset + n - 1
                val chunk = if (n == buf.size) buf else buf.copyOf(n)
                val req = Request.Builder()
                    .url(session)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Length", n.toString())
                    .header("Content-Range", "bytes $offset-$end/$total")
                    .put(chunk.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
                http.newCall(req).execute().use { res ->
                    when (res.code) {
                        200, 201 -> {
                            val id = gson.fromJson(res.body?.string().orEmpty(), DriveFile::class.java)?.id
                            return if (id != null) Result.Done(id)
                            else Result.Failed("Respuesta sin id")
                        }
                        308 -> { /* incompleto, seguir */ }
                        401, 403 -> return Result.Failed("AUTH_EXPIRED")
                        else -> return Result.Failed("Drive HTTP ${res.code}")
                    }
                }
                offset += n
                onFrac(offset.toFloat() / total.toFloat())
            }
        }
        return Result.Failed("Subida incompleta")
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
