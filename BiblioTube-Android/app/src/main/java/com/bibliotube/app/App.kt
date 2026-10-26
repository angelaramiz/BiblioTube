package com.bibliotube.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.data.SessionManager
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.model.VaultVideo
import com.bibliotube.app.data.remote.AuthSession
import com.bibliotube.app.ui.AuthScreen
import com.bibliotube.app.ui.VaultHome
import com.bibliotube.app.work.SyncWorker
import com.bibliotube.app.work.ensureReminderChannel
import com.bibliotube.app.work.scheduleReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

private const val PREFS = "bibliotube_prefs"
private const val SERVER_URL_DEFAULT = "http://10.0.2.2:3000"

@Composable
fun App() {
    val context = LocalContext.current
    val sessions = remember { SessionManager.get(context) }
    val session by sessions.session.collectAsState()

    LaunchedEffect(Unit) { sessions.refreshIfNeeded() }

    val s = session
    if (s == null) {
        AuthScreen(sessions = sessions)
        return
    }
    VaultApp(session = s, onSignOut = { sessions.signOut() })
}

@Composable
fun VaultApp(session: AuthSession, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var serverUrl by remember { mutableStateOf(SERVER_URL_DEFAULT) }
    var updateAvailable by remember { mutableStateOf<com.bibliotube.app.data.AppVersionResponse?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var logs by remember { mutableStateOf(listOf("v${com.bibliotube.app.BuildConfig.VERSION_NAME} lista.")) }

    val api = remember(serverUrl, session.accessToken) {
        ApiClient(serverUrl).also { it.authToken = session.accessToken }
    }
    val repo = remember { VaultRepository(VaultDatabase.get(context), api) }

    fun log(msg: String) { logs = (listOf(msg) + logs).take(50) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun currentVersionCode(): Int = try {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode.toInt()
        else @Suppress("DEPRECATION") pkg.versionCode
    } catch (e: Exception) { 1 }

    val checkForUpdate: (Boolean) -> Unit = { manual ->
        if (manual) isChecking = true
        scope.launch(Dispatchers.IO) {
            val res = api.fetchVersion()
            withContext(Dispatchers.Main) {
                if (res.info != null && res.info.versionCode > currentVersionCode()) {
                    updateAvailable = res.info
                    log("OTA: disponible ${res.info.versionName}")
                    if (manual) toast("Actualizacion disponible: ${res.info.versionName}")
                } else {
                    if (manual) toast(res.error ?: "Ya estas al dia")
                    if (res.error != null) log("Version: ${res.error}")
                }
                if (manual) isChecking = false
            }
        }
    }

    fun updatesDir(): File {
        val dir = File(context.getExternalFilesDir(null), "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun pendingApk(versionCode: Int): File =
        File(updatesDir(), "bibliotube_update_$versionCode.apk")

    fun cleanOldApks(keepCode: Int) {
        updatesDir().listFiles()
            ?.filter { it.name.endsWith(".apk") && it.name != "bibliotube_update_$keepCode.apk" }
            ?.forEach { it.delete() }
    }

    fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "com.bibliotube.app.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            toast("No se pudo iniciar la instalacion: ${e.message}")
            log("Install: ${e.message}")
        }
    }

    fun downloadAndInstall(info: com.bibliotube.app.data.AppVersionResponse) {
        // Si ya se descargo esta version, instalar directo sin re-descargar
        val existing = pendingApk(info.versionCode)
        val recorded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("pending_update_code", -1)
        if (recorded == info.versionCode && existing.exists()) {
            log("OTA: usa descarga existente v${info.versionName}")
            installApk(existing)
            return
        }
        val base = serverUrl.trimEnd('/')
        val full = if (info.apkUrl.startsWith("/")) base + info.apkUrl else info.apkUrl
        val url = "$full?t=${System.currentTimeMillis()}"
        isDownloading = true
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val client = ApiClient(serverUrl).http
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
                    val body = res.body ?: throw RuntimeException("respuesta vacia")
                    val total = body.contentLength()
                    val dir = updatesDir()
                    cleanOldApks(info.versionCode)
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0L
                    body.byteStream().use { input ->
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val p = done.toFloat() / total.toFloat()
                                withContext(Dispatchers.Main) { downloadProgress = p }
                            }
                        }
                    }
                    val file = pendingApk(info.versionCode)
                    file.writeBytes(out.toByteArray())
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putInt("pending_update_code", info.versionCode).apply()
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        updateAvailable = null
                        installApk(file)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    toast("Error de descarga: ${e.message}")
                    log("Descarga: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(session.accessToken) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", SERVER_URL_DEFAULT) ?: SERVER_URL_DEFAULT
        prefs.edit().putString("server_url", serverUrl).apply()
        ensureReminderChannel(context)
        scope.launch(Dispatchers.IO) {
            val err = repo.sync()
            val pending = VaultDatabase.get(context).videos().pendingReminders(System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                if (err != null) log("Sync: $err") else log("Sync OK")
                pending.forEach { v ->
                    v.recordatorioAt?.let { scheduleReminder(context, v.id, v.titulo, it, v.recordatorioRepeat) }
                }
            }
        }
        val sse = com.bibliotube.app.api.SseClient("$serverUrl/api/events/stream") {
            scope.launch(Dispatchers.IO) { repo.sync() }
        }
        sse.addListener { log("Evento tiempo real recibido") }
        sse.start()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Text("BiblioTube", style = MaterialTheme.typography.headlineSmall)
            Text(
                "v${com.bibliotube.app.BuildConfig.VERSION_NAME} (${com.bibliotube.app.BuildConfig.VERSION_CODE}) · ${session.email}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> VaultHome(repo, api)
                1 -> Column(
                    Modifier.fillMaxSize().padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Ajustes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    var urlDraft by remember(serverUrl) { mutableStateOf(serverUrl) }
                    Text("Servidor", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = urlDraft, onValueChange = { urlDraft = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        serverUrl = urlDraft.trim()
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString("server_url", serverUrl).apply()
                        log("Servidor: $serverUrl")
                    }) { Text("Guardar servidor") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { checkForUpdate(true) }, enabled = !isChecking) {
                        Text(if (isChecking) "Comprobando…" else "Buscar actualizaciones")
                    }
                    updateAvailable?.let { info ->
                        Spacer(Modifier.height(8.dp))
                        Text("Actualizacion disponible: ${info.versionName}")
                        Spacer(Modifier.height(8.dp))
                        if (isDownloading) {
                            LinearProgressIndicator(progress = { downloadProgress })
                            Text("Descargando… ${(downloadProgress * 100).toInt()}%")
                        } else {
                            Button(onClick = { downloadAndInstall(info) }) {
                                Text("Descargar e instalar")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) { SyncWorker.enqueue(context); repo.sync() }
                    }) { Text("Sincronizar ahora") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSignOut) { Text("Cerrar sesion") }
                    Spacer(Modifier.height(12.dp))
                    Text("Logs", style = MaterialTheme.typography.titleMedium)
                    logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        NavigationBar(Modifier.navigationBarsPadding()) {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("B") }, label = { Text("Boveda") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("A") }, label = { Text("Ajustes") })
        }
    }
}
