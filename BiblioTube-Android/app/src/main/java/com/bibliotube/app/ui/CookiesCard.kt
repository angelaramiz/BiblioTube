package com.bibliotube.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bibliotube.app.data.AppLog
import com.bibliotube.downloader.BtFiles
import java.io.File

/**
 * Cookies de YouTube para yt-dlp.
 * Nota honesta: solo hacen falta para videos con edad/privados/miembros; el
 * "sign in to confirm you're not a bot" se corrige con yt-dlp al dia.
 */
private fun ytCookieCount(context: Context): Int = runCatching {
    BtFiles.cookiesFile(context).readLines().count { it.contains("youtube.com") }
}.getOrDefault(0)

@Composable
fun YoutubeCookiesCard(context: Context) {
    var hasCookies by remember { mutableStateOf(BtFiles.cookiesFile(context).exists()) }
    var showDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    fun saveCookies(raw: String) {
        val body0 = raw.trim()
        val problem = when {
            body0.isEmpty() -> "vacío"
            !body0.contains("youtube.com") -> "no contiene .youtube.com"
            !body0.lines().any { it.contains('\t') } -> "no parece formato Netscape (faltan TAB)"
            else -> null
        }
        if (problem != null) {
            Toast.makeText(context, "Cookies inválidas: $problem", Toast.LENGTH_LONG).show()
            return
        }
        val body = if (body0.startsWith("# Netscape")) body0 else "# Netscape HTTP Cookie File\n$body0"
        val dst = BtFiles.cookiesFile(context)
        dst.parentFile?.mkdirs()
        dst.writeText(body + "\n")
        hasCookies = true
        showDialog = false
        text = ""
        val n = body.lines().count { it.contains("youtube.com") }
        AppLog.d("dl", "cookies importadas ($n lineas de youtube, ${body.length} bytes)")
        Toast.makeText(context, "Cookies guardadas ($n de YouTube)", Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("no se pudo abrir el archivo")
        }.onSuccess { saveCookies(it) }
            .onFailure { Toast.makeText(context, "No se pudo leer: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Cookies de YouTube",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasCookies) "Cookies cargadas (${ytCookieCount(context)} lineas de YouTube): se usan al descargar."
                else "Necesarias para videos con restriccion de edad, privados o de miembros.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Exporta cookies.txt desde tu PC con la extension \"Get cookies.txt LOCALLY\" " +
                    "(con sesion iniciada en YouTube), pasalo al telefono y elige el archivo aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Elegir archivo") }
                OutlinedButton(
                    onClick = { showDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (hasCookies) "Pegar" else "Pegar texto") }
                if (hasCookies) {
                    OutlinedButton(
                        onClick = {
                            BtFiles.cookiesFile(context).delete()
                            hasCookies = false
                            AppLog.d("dl", "cookies eliminadas")
                            Toast.makeText(context, "Cookies eliminadas", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Borrar") }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Pegar cookies de YouTube") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Formato Netscape (cookies.txt): cada linea lleva columnas separadas por TAB y debe incluir .youtube.com.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Contenido de cookies.txt") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveCookies(text) }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
