package com.bibliotube.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import com.bibliotube.app.data.AppLog
import com.bibliotube.app.data.SessionManager
import com.bibliotube.app.data.drive.DriveAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.bibliotube.app.data.VaultRepository
import com.bibliotube.app.data.local.VaultDatabase
import com.bibliotube.app.data.remote.AuthSession
import com.bibliotube.app.ui.AuthScreen
import com.bibliotube.app.ui.DownloadsScreen
import com.bibliotube.app.ui.VaultHome
import com.bibliotube.app.ui.theme.BiblioTubeTheme
import com.bibliotube.app.work.SyncWorker
import com.bibliotube.app.work.ensureReminderChannel
import com.bibliotube.app.work.scheduleReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import com.bibliotube.app.ui.components.BiblioTubeLogoHeader

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
            sessions = sessions,
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
    sessions: SessionManager,
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
    val logs by AppLog.lines.collectAsState()

    val api = remember(session.accessToken) {
        ApiClient(SERVER_URL).also { it.authToken = session.accessToken }
    }
    val repo = remember { VaultRepository(VaultDatabase.get(context), api) }

    fun log(msg: String) = AppLog.d("app", msg)
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

    suspend fun downloadToFile(url: String, file: File, onFrac: (Float) -> Unit) {
        val client = ApiClient(SERVER_URL).http
        val resumed = if (file.exists()) file.length() else 0L
        val builder = Request.Builder().url(url)
        if (resumed > 0) builder.header("Range", "bytes=$resumed-")
        client.newCall(builder.build()).execute().use { res ->
            val append = res.code == 206
            if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
            val body = res.body ?: throw RuntimeException("respuesta vacia")
            if (!append && resumed > 0) file.delete()
            val startAt = if (append) resumed else 0L
            val total = body.contentLength().let { if (it > 0) it + startAt else -1L }
            var done = startAt
            java.io.FileOutputStream(file, append).use { out ->
                val buf = ByteArray(32 * 1024)
                var read: Int
                body.byteStream().use { input ->
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val p = done.toFloat() / total.toFloat()
                            withContext(Dispatchers.Main) { onFrac(p) }
                        }
                    }
                }
            }
            if (total > 0 && file.length() != total) {
                throw RuntimeException("descarga incompleta (${file.length()}/$total bytes)")
            }
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
                val file = pendingApk(info.versionCode)
                cleanOldApks(info.versionCode)
                var lastErr: Exception? = null
                var ok = false
                for (attempt in 1..3) {
                    if (attempt > 1) {
                        withContext(Dispatchers.Main) {
                            downloadProgress = 0f
                            log("OTA: reintento $attempt/3")
                        }
                    }
                    try {
                        downloadToFile(url, file) { frac ->
                            downloadProgress = frac
                        }
                        if (!downloadedApkValid(file, info.versionCode)) {
                            file.delete()
                            throw RuntimeException("APK descargado corrupto (intento $attempt/3)")
                        }
                        ok = true
                        break
                    } catch (e: Exception) {
                        lastErr = e
                        withContext(Dispatchers.Main) { log("Descarga intento $attempt: ${e.message}") }
                    }
                }
                if (!ok) throw lastErr ?: RuntimeException("descarga fallida")
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt("pending_update_code", info.versionCode).apply()
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    updateAvailable = null
                    installApk(file)
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

    suspend fun syncWithAuthRetry(): String? {
        val first = repo.sync()
        if (first != VaultRepository.AUTH_EXPIRED) return first
        log("Sync: sesion caducada, renovando…")
        sessions.refreshIfNeeded()
        val current = sessions.session.value
        if (current != null) api.authToken = current.accessToken
        val second = repo.sync()
        if (second == VaultRepository.AUTH_EXPIRED) {
            withContext(Dispatchers.Main) {
                toast("Sesion caducada, entra de nuevo")
                onSignOut()
            }
            return "Sesion caducada"
        }
        return second
    }

    val queue = remember { com.bibliotube.app.downloader.DownloadCenter.queue(context) }
    val dlTasks by queue.tasks.collectAsState()
    var processedTasks by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(dlTasks) {
        val fresh = dlTasks.values.filter {
            it.state == com.bibliotube.downloader.BtTaskState.Done && it.id !in processedTasks
        }
        val failed = dlTasks.values.filter {
            it.state == com.bibliotube.downloader.BtTaskState.Error && it.id !in processedTasks
        }
        if (fresh.isEmpty() && failed.isEmpty()) return@LaunchedEffect
        processedTasks = processedTasks + (fresh + failed).map { it.id }.toSet()
        failed.forEach { t -> AppLog.d("dl", "tarea error: ${t.url} :: ${t.error}") }
        scope.launch(Dispatchers.IO) {
            fresh.forEach { t ->
                val link = com.bibliotube.app.downloader.DownloadCenter.takeLink(t.id) ?: return@forEach
                val path = t.files.firstOrNull() ?: return@forEach
                AppLog.d("dl", "completada: $path")
                repo.setLocalPath(link.first, path)
                if (link.second) {
                    repo.setDriveState(link.first, "pending")
                    com.bibliotube.app.work.DriveUploadWorker.enqueue(context, link.first)
                    AppLog.d("drive", "subida encolada: ${link.first}")
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
            val err = syncWithAuthRetry()
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
            // Header formal, plano y minimalista con el nuevo logotipo
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    BiblioTubeLogoHeader(
                        subtitle = "Bóveda de Video"
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "v${com.bibliotube.app.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Cuerpo principal
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> VaultHome(repo, api)
                    1 -> DownloadsScreen()
                    2 -> SettingsTabs(
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
                            scope.launch(Dispatchers.IO) { SyncWorker.enqueue(context); syncWithAuthRetry() }
                        },
                        onSignOut = onSignOut
                    )
                }
            }

            // Barra de navegacion inferior plana con iconos Material
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (tab == 0) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                            contentDescription = "Bóveda"
                        )
                    },
                    label = {
                        Text(
                            "Bóveda",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (tab == 1) Icons.Filled.Download else Icons.Outlined.Download,
                            contentDescription = "Descargas"
                        )
                    },
                    label = {
                        Text(
                            "Descargas",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (tab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Ajustes"
                        )
                    },
                    label = {
                        Text(
                            "Ajustes",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        } // CompositionLocalProvider
    }
}

@Composable
fun DriveLinkCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(DriveAccount.email(context)) }
    var busy by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val acct = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(res.data)
                    .getResult(ApiException::class.java)
            }.getOrNull()
            DriveAccount.onSignedIn(context, acct)
            email = DriveAccount.email(context)
        }
        busy = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Google Drive",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (email != null) {
                Text(email!!, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            DriveAccount.signOut(context)
                            withContext(Dispatchers.Main) { email = null }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Desvincular") }
            } else {
                Text(
                    "Vincula tu cuenta para guardar videos en tu Drive.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        val act = context as? Activity ?: return@Button
                        busy = true
                        launcher.launch(DriveAccount.signInIntent(act))
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (busy) "Abriendo…" else "Vincular Drive") }
            }
        }
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Selector de secciones plano
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Perfil", "Apariencia", "Aplicación", "Registros").forEachIndexed { i, label ->
                FilterChip(
                    selected = sub == i,
                    onClick = { sub = i },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (sub) {
            // Perfil
            0 -> Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Column {
                                Text(
                                    session.email,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                )
                                Text(
                                    "ID: ${session.userId.take(8)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Cerrar Sesión")
                }

                DriveLinkCard()
            }

            // Apariencia / Tema
            1 -> Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Modo Visual",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        )
                        Text(
                            "Selecciona la paleta de visualización para BiblioTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (v, l) ->
                                FilterChip(
                                    selected = themeMode == v,
                                    onClick = { onThemeMode(v) },
                                    label = { Text(l) }
                                )
                            }
                        }
                    }
                }
            }

            // Aplicación & OTA
            2 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Estado del Sistema",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        )
                        Text(
                            "BiblioTube v${com.bibliotube.app.BuildConfig.VERSION_NAME} (Compilación ${com.bibliotube.app.BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(6.dp))

                        Button(
                            onClick = onCheckUpdate,
                            enabled = !isChecking,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isChecking) "Comprobando servidor…" else "Buscar Actualizaciones")
                        }

                        updateAvailable?.let { info ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Nueva versión disponible: v${info.versionName}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (isDownloading) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Descargando paquete… ${(downloadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Button(
                                            onClick = { onDownload(info) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Descargar e Instalar")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = onSync,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Forzar Sincronización")
                        }
                    }
                }
            }

            // Registros / Logs
            3 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Consola de Diagnóstico",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    )
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(AppLog.text()))
                            Toast.makeText(context, "Registros copiados al portapapeles", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copiar", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        logs.forEach { logItem ->
                            Text(
                                text = logItem,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

