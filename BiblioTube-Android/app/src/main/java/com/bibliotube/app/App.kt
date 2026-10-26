package com.bibliotube.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bibliotube.app.api.ApiClient
import com.bibliotube.app.api.SERVER_URL
import com.bibliotube.app.data.SessionManager
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.remote.AuthSession
import com.bibliotube.app.ui.AuthScreen
import com.bibliotube.app.ui.VaultHome
import com.bibliotube.app.ui.theme.BiblioTubeTheme
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

@Composable
fun App() {
    val context = LocalContext.current
    val sessions = remember { SessionManager.get(context) }
    val session by sessions.session.collectAsState()
    var themeMode by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("theme_mode", "system") ?: "system"
        )
    }

    LaunchedEffect(Unit) { sessions.refreshIfNeeded() }

    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    BiblioTubeTheme(darkTheme = dark) {
        val s = session
        if (s == null) {
            AuthScreen(sessions = sessions)
            return@BiblioTubeTheme
        }
        VaultApp(
            session = s,
            onSignOut = { sessions.signOut() },
            themeMode = themeMode,
            onThemeMode = {
                themeMode = it
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("theme_mode", it).apply()
            }
        )
    }
}

@Composable
fun VaultApp(
    session: AuthSession,
    onSignOut: () -> Unit,
    themeMode: String,
    onThemeMode: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var updateAvailable by remember { mutableStateOf<com.bibliotube.app.data.AppVersionResponse?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var logs by remember { mutableStateOf(listOf("v${com.bibliotube.app.BuildConfig.VERSION_NAME} lista.")) }

    val api = remember(session.accessToken) {
        ApiClient(SERVER_URL).also { it.authToken = session.accessToken }
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

    fun downloadedApkValid(file: File, expectedCode: Int): Boolean {
        if (!file.exists()) return false
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION") context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: return false
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else @Suppress("DEPRECATION") info.versionCode
            info.packageName == context.packageName && code == expectedCode
        } catch (e: Exception) {
            log("APK invalido (${file.name}): ${e.message}")
            false
        }
    }

    fun downloadAndInstall(info: com.bibliotube.app.data.AppVersionResponse) {
        val existing = pendingApk(info.versionCode)
        val recorded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("pending_update_code", -1)
        if (recorded == info.versionCode && downloadedApkValid(existing, info.versionCode)) {
            log("OTA: usa descarga existente v${info.versionName}")
            installApk(existing)
            return
        }
        if (existing.exists()) {
            log("OTA: descarga previa invalida, se vuelve a descargar")
            existing.delete()
        }
        val full = if (info.apkUrl.startsWith("/")) SERVER_URL.trimEnd('/') + info.apkUrl else info.apkUrl
        val url = "$full?t=${System.currentTimeMillis()}"
        isDownloading = true
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val client = ApiClient(SERVER_URL).http
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
                    if (total > 0 && file.length() != total) {
                        file.delete()
                        throw RuntimeException("descarga incompleta (${file.length()}/$total bytes)")
                    }
                    if (!downloadedApkValid(file, info.versionCode)) {
                        file.delete()
                        throw RuntimeException("APK descargado corrupto, reintenta")
                    }
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
        ensureReminderChannel(context)
        // Limpieza post-instalacion: si ya corre la version descargada, borrar APKs viejos
        scope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pending = prefs.getInt("pending_update_code", -1)
            if (pending > 0 && currentVersionCode() >= pending) {
                updatesDir().listFiles()
                    ?.filter { it.name.endsWith(".apk") }
                    ?.forEach { it.delete() }
                prefs.edit().putInt("pending_update_code", -1).apply()
                withContext(Dispatchers.Main) { log("OTA: limpieza post-instalacion v$pending") }
            }
        }
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
        val sse = com.bibliotube.app.api.SseClient("$SERVER_URL/api/events/stream") {
            scope.launch(Dispatchers.IO) { repo.sync() }
        }
        sse.addListener { log("Evento tiempo real recibido") }
        sse.start()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "BiblioTube",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "v${com.bibliotube.app.BuildConfig.VERSION_NAME} (${com.bibliotube.app.BuildConfig.VERSION_CODE}) · ${session.email}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> VaultHome(repo, api)
                1 -> SettingsTabs(
                    session = session,
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    logs = logs,
                    updateAvailable = updateAvailable,
                    isChecking = isChecking,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    onCheckUpdate = { checkForUpdate(true) },
                    onDownload = { i -> downloadAndInstall(i) },
                    onSync = {
                        scope.launch(Dispatchers.IO) { SyncWorker.enqueue(context); repo.sync() }
                    },
                    onSignOut = onSignOut
                )
            }
        }
        NavigationBar(Modifier.navigationBarsPadding()) {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("B") }, label = { Text("Boveda") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("A") }, label = { Text("Ajustes") })
        }
        } // CompositionLocalProvider
    }
}

@Composable
fun SettingsTabs(
    session: AuthSession,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    logs: List<String>,
    updateAvailable: com.bibliotube.app.data.AppVersionResponse?,
    isChecking: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onCheckUpdate: () -> Unit,
    onDownload: (com.bibliotube.app.data.AppVersionResponse) -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sub by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Perfil", "Tema", "App", "Logs").forEachIndexed { i, label ->
                FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (sub) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perfil", style = MaterialTheme.typography.titleMedium)
                Text(session.email, style = MaterialTheme.typography.bodyLarge)
                Text("ID: ${session.userId.take(8)}…", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onSignOut) { Text("Cerrar sesion") }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personalizacion", style = MaterialTheme.typography.titleMedium)
                Text("Tema", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (v, l) ->
                        FilterChip(selected = themeMode == v, onClick = { onThemeMode(v) }, label = { Text(l) })
                    }
                }
            }
            2 -> Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("App", style = MaterialTheme.typography.titleMedium)
                Text(
                    "BiblioTube v${com.bibliotube.app.BuildConfig.VERSION_NAME} (${com.bibliotube.app.BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onCheckUpdate, enabled = !isChecking) {
                    Text(if (isChecking) "Comprobando…" else "Buscar actualizaciones")
                }
                updateAvailable?.let { info ->
                    Text("Actualizacion disponible: ${info.versionName}")
                    if (isDownloading) {
                        LinearProgressIndicator(progress = { downloadProgress })
                        Text("Descargando… ${(downloadProgress * 100).toInt()}%")
                    } else {
                        Button(onClick = { onDownload(info) }) { Text("Descargar e instalar") }
                    }
                }
                Button(onClick = onSync) { Text("Sincronizar ahora") }
            }
            3 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Logs", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                    Toast.makeText(context, "Logs copiados", Toast.LENGTH_LONG).show()
                }) { Text("Copiar logs") }
                Spacer(Modifier.height(8.dp))
                logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
