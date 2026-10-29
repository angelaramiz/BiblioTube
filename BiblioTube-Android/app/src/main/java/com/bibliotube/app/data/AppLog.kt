package com.bibliotube.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX = 120
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val lock = Any()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun d(tag: String, msg: String?) {
        val line = "${fmt.format(Date())} [$tag] ${msg ?: "-"}"
        android.util.Log.d("BiblioTube", line)
        synchronized(lock) {
            _lines.value = (listOf(line) + _lines.value).take(MAX)
        }
    }

    fun text(): String = _lines.value.reversed().joinToString("\n")
}
