package com.bibliotube.app.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SseClient(
    private val streamUrl: String,
    private val onEvent: (String) -> Unit
) {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    @Volatile private var running = false

    fun addListener(l: (String) -> Unit) { listeners.add(l) }

    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true, name = "sse-stream") {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            while (running) {
                try {
                    val req = Request.Builder().url(streamUrl).build()
                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
                        val src = res.body?.source() ?: throw RuntimeException("sin body")
                        while (running && !src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (line.startsWith("data:")) {
                                val payload = line.removePrefix("data:").trim()
                                onEvent(payload)
                                listeners.forEach { it(payload) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    try { Thread.sleep(5000) } catch (ie: InterruptedException) { break }
                }
            }
        }
    }

    fun stop() { running = false }
}
