package com.bibliotube.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bibliotube.app.data.remote.AuthClient
import com.bibliotube.app.data.remote.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(onSession: (AuthSession) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var modeLogin by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (email.isBlank() || password.length < 6) {
            error = "Email valido y clave de 6+ caracteres"
            return
        }
        busy = true
        error = null
        scope.launch(Dispatchers.IO) {
            val auth = AuthClient(context)
            val res = if (modeLogin) auth.signIn(email.trim(), password) else auth.signUp(email.trim(), password)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                busy = false
                res.onSuccess { onSession(it) }.onFailure { error = it.message }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("BiblioTube", style = MaterialTheme.typography.headlineMedium)
        Text(if (modeLogin) "Inicia sesion para ver tu boveda" else "Crea tu cuenta", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        TextField(
            value = password, onValueChange = { password = it }, label = { Text("Clave") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Espera…" else if (modeLogin) "Entrar" else "Crear cuenta")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { modeLogin = !modeLogin }, modifier = Modifier.fillMaxWidth()) {
            Text(if (modeLogin) "No tengo cuenta" else "Ya tengo cuenta")
        }
    }
}
