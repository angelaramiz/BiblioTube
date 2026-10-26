---
name: ota-android-generic
version: 1.0.0
description: Patrón OTA (auto-update) para apps Android con backend propio (endpoint de versión + APK descargable + FileProvider) y pipeline release en PowerShell. Usar al implementar o mantener actualización automática sin tienda, al publicar una versión nueva, o cuando el OTA no detecta/descarga/instala.
---

# SKILL: OTA Android Updates — Plantilla Genérica

## Resumen

Patrón estándar de actualización OTA para apps Android con backend propio:
el servidor anuncia la versión publicada, la app compara `versionCode`,
descarga el APK y lo instala vía FileProvider. Incluye pipeline release
(`scripts/`) y checklist de publicación. Todo lo específico de cada proyecto
(tabla, endpoints, apps, URLs) vive en su `ADAPTER.md` + `apps.json`, nunca aquí.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{db_table}}` | Tabla que almacena versiones | `app_versions` |
| `{{db_key_column}}` | Columna clave | `clave` |
| `{{db_value_column}}` | Columna valor JSON | `valor` |
| `{{db_key}}` | Key por app en la tabla | `app_version_miapp` |
| `{{endpoint_version}}` | Endpoint que sirve la versión | `/api/app-version?app=miapp` |
| `{{apk_dir}}` | Dónde se sirven los APKs | `public/` |
| `{{server_base_url}}` | Base pública del backend | `https://mi-servidor.com` |
| `{{package}}` | Package ID de la app | `com.ejemplo.miapp` |

El `MAPEO DEL PROYECTO` de abajo es la versión extendida de esta tabla:
llenarlo en el `ADAPTER.md` es el paso 1 de la instalación.

## 🔧 MAPEO DEL PROYECTO (llenar en el ADAPTER antes de usar)

```yaml
# ─── Backend ─────────────────────────────────────────────
server_file:           # Archivo del servidor (ej: server.ts, app.py, index.js)
version_endpoint:      # Ruta del endpoint de versión (ej: /api/android-version)
version_endpoint_params: # Query params por app (ej: ?app=conteo)
db_table:              # Tabla que almacena versiones (ej: warehouse_settings)
db_key_column:         # Columna clave (ej: clave)
db_value_column:       # Columna valor JSON (ej: valor)
apk_directory:         # Donde se sirven los APKs (ej: public/, static/, downloads/)

# ─── Apps Android ────────────────────────────────────────
apps:
  - name:              # Nombre legible (ej: "Mi App")
    package:           # Package ID (ej: com.ejemplo.miapp)
    db_key:            # Key en la tabla (ej: app_version_miapp)
    apk_filename:      # Nombre del APK (ej: miapp.apk)
    source_path:       # Ruta al código fuente Kotlin/Java
    main_activity:     # Archivo MainActivity (ej: MainActivity.kt)

# ─── Configuración ──────────────────────────────────────
comparison_field:      # Campo para comparar versiones (versionCode o versionName)
cache_busting:         # true/false — usar ?t=timestamp en descarga
clean_old_apks:        # true/false — limpiar APKs antes de descargar
auto_check_on_start:   # true/false — check silencioso al inicio
manual_check_button:   # true/false — botón manual en UI
```

---

## Arquitectura General

```
┌──────────────┐    GET /version-endpoint    ┌──────────────┐
│  App Android │ ──────────────────────────► │   Server     │
│  (on-create  │ ◄────────────────────────── │   (REST API) │
│   or manual) │    { versionCode, apkUrl }  │              │
└──────┬───────┘                             │   DB Table   │
       │                                     └──────────────┘
       │ versionCode > localVersionCode?
       │
       ▼
┌──────────────┐
│  Dialog UI   │  "New version available"
│  Download    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  HTTP GET    │  Full URL → bytes → file.apk
│  (OkHttp)    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Intent      │  ACTION_VIEW + apk URI
│  Install     │  (FileProvider on Android 7+)
└──────────────┘
```

---

## Server Endpoint (Plantilla)

### Express/Node.js
```typescript
// GET /api/app-version — Returns latest version for specified app
app.get("/api/app-version", async (req, res) => {
  const app = req.query.app as string;
  const key = app ? `app_version_${app}` : "app_version";

  const { data } = await supabase
    .from("app_versions")  // ← {{db_table}}
    .select("valor")       // ← {{db_value_column}}
    .eq("clave", key)      // ← {{db_key_column}}
    .single();

  const versionInfo = data?.valor || {
    versionCode: 1,
    versionName: "1.0.0",
    apkUrl: "/public/app.apk"
  };

  res.json(versionInfo);
});
```

### Python/Flask
```python
@app.route("/api/app-version")
def app_version():
    app = request.args.get("app", "")
    key = f"app_version_{app}" if app else "app_version"

    result = supabase.table("app_versions") \
        .select("valor") \
        .eq("clave", key) \
        .single() \
        .execute()

    return result.data["valor"] if result.data else {
        "versionCode": 1,
        "versionName": "1.0.0",
        "apkUrl": "/public/app.apk"
    }
```

### Database Schema
```sql
CREATE TABLE app_versions (
  clave TEXT PRIMARY KEY,
  valor JSONB NOT NULL
);

-- Example data
INSERT INTO app_versions (clave, valor) VALUES
  ('app_version', '{"versionCode": 5, "versionName": "1.0.4", "apkUrl": "/public/app.apk"}'),
  ('app_version_beta', '{"versionCode": 8, "versionName": "1.1.0-beta", "apkUrl": "/public/app-beta.apk"}');
```

---

## Android Code (Plantilla Kotlin/Jetpack Compose)

### Data Model
```kotlin
data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)
```

### Version Check
```kotlin
val checkForUpdate: (Boolean) -> Unit = { manual ->
    if (manual) isCheckingUpdate = true
    scope.launch(Dispatchers.IO) {
        try {
            // 1. Get current version
            val currentVersionCode = try {
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") pkgInfo.versionCode
                }
            } catch (e: Exception) { 1 }

            // 2. Fetch latest from server
            val url = "${serverUrl}${VERSION_ENDPOINT}"  // ← {{endpoint_version}}
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val info = Gson().fromJson(res.body?.string(), AppVersionResponse::class.java)
                    withContext(Dispatchers.Main) {
                        if (info.versionCode > currentVersionCode) {
                            updateAvailable = info
                            if (manual) toast("Update available: ${info.versionName}")
                        } else {
                            if (manual) toast("Already up to date")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (manual) withContext(Dispatchers.Main) { toast("Network error") }
        } finally {
            withContext(Dispatchers.Main) { isCheckingUpdate = false }
        }
    }
}

// Auto-check on start
LaunchedEffect(Unit) { checkForUpdate(false) }
```

### Download & Install
```kotlin
Button(onClick = {
    val apkUrl = updateAvailable!!.apkUrl
    // Build full URL with cache busting
    val fullUrl = if (apkUrl.startsWith("/")) "${serverUrl.trimEnd('/')}$apkUrl" else apkUrl
    val urlWithTimestamp = "$fullUrl?t=${System.currentTimeMillis()}"

    isDownloading = true
    scope.launch(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(urlWithTimestamp).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val bytes = res.body?.bytes() ?: return@use
                    // Clean old APKs
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    dir?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
                    // Save new APK
                    val file = File(dir, "app_update_${System.currentTimeMillis()}.apk")
                    file.writeBytes(bytes)

                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        updateAvailable = null
                        // Install via FileProvider
                        try {
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            toast("Could not start installation")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isDownloading = false
                toast("Download error")
            }
        }
    }
}) { Text("Download & Install") }
```

### FileProvider Config (AndroidManifest.xml)
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### file_paths.xml (res/xml/)
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="downloads" path="Download/" />
</paths>
```

---

## Scripts (`scripts/` — archivos reales, no snippets)

Pipeline release en PowerShell. La configuración de apps NO va hardcodeada:
`release.ps1` lee un JSON (`apps.json`, ver `apps.example.json`) que cada
proyecto provee junto a su `ADAPTER.md`.

| Script | Qué hace |
|---|---|
| `build-debug.ps1 -AppDir` | Compila APK debug para pruebas |
| `build-release.ps1 -AppDir [-Keystore*]` | Compila APK release firmado (o firma del gradle) |
| `build-and-deploy.ps1 -AppDir -ServerDir -ApkName` | Build release + copia el APK al servidor |
| `update-version.ps1 -SupabaseUrl -SupabaseKey -DbTable -DbKey -VersionCode -VersionName [-ApkUrl]` | PATCH de la versión en Supabase vía PostgREST |
| `full-release.ps1` | Una app: build + deploy + DB |
| `release.ps1 -AppsJson -App <key\|all> -VersionCode -VersionName` | Multi-app: build → deploy → DB → verify + resumen |

```powershell
# Release de una app (apps.json del proyecto)
./release.ps1 -AppsJson ./apps.json -App miapp -VersionCode 15 -VersionName "1.0.15" `
    -ServerDir "../server/public" -ServerBaseUrl "https://mi-servidor.com" `
    -SupabaseKey "eyJ..."

# Todas las apps + firma personalizada + solo deploy (APK ya existe)
./release.ps1 -AppsJson ./apps.json -App all -VersionCode 15 -VersionName "1.0.15" -SkipBuild `
    -KeystorePath "C:\keys\mi-keystore.jks" -KeystorePassword "x" `
    -KeyAlias "mi-key" -KeyPassword "x" -SupabaseKey "eyJ..."
```

`apps.json` (una entrada por app):

```json
{
  "apps": {
    "miapp": {
      "Name": "Mi App",
      "Package": "com.ejemplo.miapp",
      "SourceDir": "../MiApp-Android",
      "ApkName": "miapp.apk",
      "DbKey": "app_version_miapp",
      "VersionEndpoint": "/api/app-version?app=miapp"
    }
  }
}
```

---

## Publicar Nueva Versión (Checklist)

1. **Build** APK firmado (`build-release.ps1` o `release.ps1`).
2. **Copiar** APK al directorio del servidor (`{{apk_dir}}`).
3. **Actualizar** versión en la DB (`update-version.ps1` o paso DB del release).
4. **Verificar**: `curl {{server_base_url}}{{endpoint_version}}` devuelve la nueva versión.
5. **Las apps** la detectan al próximo check (arranque o botón manual).

---

## Common Pitfalls

| Problema | Causa | Solución |
|----------|-------|----------|
| APK no se descarga | URL relativa sin server base | Siempre construir URL completa: `serverUrl + apkUrl` |
| Instalación falla en Android 7+ | No usa FileProvider | Configurar FileProvider + file_paths.xml |
| App no detecta nueva versión | Cache del navegador/servidor | Agregar `?t={timestamp}` a la URL |
| "Install unknown apps" blocked | Permiso no otorgado | Guiar al usuario a Settings > Allow unknown sources |
| Version check siempre falla | Endpoint devuelve error | Verificar DB table existe y tiene datos |
| APK corrupted | Descarga incompleta | Verificar `response.isSuccessful` y `bytes != null` |

---

## Adaptación a Otros Frameworks

### React Native
Usar `react-native-code-push` o `expo-updates` para OTA updates nativos.

### Flutter
Usar `flutter_downloader` + `open_file` para download e install manual.

### Ionic/Capacitor
Implementar descarga manual + intent de instalación, o plugin de downloader del ecosistema.

---

## Adaptadores conocidos

- Gestor inventory → `skills/ota-android-generic/ADAPTER.md` (v1.0.0, 2026-10-04; sin backend OTA — ver huecos)

## Referencias

| Recurso | Ruta |
|---------|------|
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Ciclo de vida | `.agents/rules/ciclo-vida-skills.md` en BiblioSkills |
| Skill hermana | `skill/android-kotlin-app` (§3–§5 cubren versionado/OTA/deploy con el mismo patrón) |
| Android PackageInstaller | https://developer.android.com/reference/android/content/pm/PackageInstaller |
| FileProvider Guide | https://developer.android.com/reference/androidx/core/content/FileProvider |
| OkHttp Downloads | https://square.github.io/okhttp/recipes/ |
