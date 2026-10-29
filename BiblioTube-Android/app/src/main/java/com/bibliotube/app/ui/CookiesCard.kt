package com.bibliotube.app.ui

import android.content.Context
import android.widget.Toast
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
@Composable
fun YoutubeCookiesCard(context: Context) {
    var hasCookies by remember { mutableStateOf(BtFiles.cookiesFile(context).exists()) }
    var showDialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

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
                if (hasCookies) "Cookies cargadas: se usan al descargar."
                else "Opcional. Solo para videos con edad, privados o de miembros.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (hasCookies) "Reemplazar" else "Importar") }
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
                TextButton(onClick = {
                    val raw = text.trim()
                    val problem = when {
                        raw.isEmpty() -> "vacío"
                        !raw.contains("youtube.com") -> "no contiene .youtube.com"
                        !raw.lines().any { it.contains('\t') } -> "no parece formato Netscape (faltan TAB)"
                        else -> null
                    }
                    if (problem != null) {
                        Toast.makeText(context, "Cookies inválidas: $problem", Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    val body = if (raw.startsWith("# Netscape")) raw else "# Netscape HTTP Cookie File\n$raw"
                    val dst = BtFiles.cookiesFile(context)
                    dst.parentFile?.mkdirs()
                    dst.writeText(body + "\n")
                    hasCookies = true
                    showDialog = false
                    text = ""
                    AppLog.d("dl", "cookies importadas (${body.length} bytes)")
                    Toast.makeText(context, "Cookies guardadas", Toast.LENGTH_SHORT).show()
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
