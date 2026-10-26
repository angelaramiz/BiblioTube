---
name: android-kotlin-app
version: 1.0.0
description: Build, release, versionado, OTA (auto-update), instalación y publicación de apps Android Kotlin (Jetpack Compose) conectadas a un backend propio + Supabase. Usar cuando haya que crear, corregir, compilar o publicar una app móvil en su módulo Android, cuando el OTA no descarga/instala, cuando falle el build (gradle/CMake/NDK), al versionar (versionCode/versionName), al sincronizar (SSE/Realtime) o al desplegar el APK a producción.
---

# Skill: Apps Móviles Android Kotlin — pipeline completo

Guía replicable para construir y mantener apps móviles Android (Kotlin + Jetpack Compose) en un
proyecto con backend propio + Supabase, con **OTA (auto-update)**, **release**, **versionamiento**,
**auto-deploy** e **instalación en el propio directorio de la app**. Incluye las trampas reales
encontradas en producción.

## Variables del proyecto

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `{{dir_app}}` | Carpeta de la app Android en el repo | `MiApp-Android/` |
| `{{paquete}}` | Package ID (`applicationId` y `namespace`) | `com.ejemplo.miapp` |
| `{{tabla_versiones}}` | Tabla Supabase que anuncia versiones publicadas | `warehouse_settings` |
| `{{clave_version}}` | Patrón de clave por app en esa tabla | `android_version_<app>` |
| `{{endpoint_version}}` | Endpoint que sirve la versión publicada | `GET /api/android-version?app=<app>` |
| `{{endpoint_catalogo}}` | Catálogo de apps (lee la tabla dinámicamente) | `GET /api/app-catalog` |
| `{{stream_realtime}}` | Canal de tiempo real del backend | `/api/events/stream` (SSE) |
| `{{dir_public_apk}}` | Dónde se publica el APK descargable | `public/<app>.apk` |
| `{{deploy}}` | Cómo se despliega el backend/APK | push a git → hosting |

Los valores concretos de cada proyecto viven en su `ADAPTER.md`, nunca aquí.

## 1. Arquitectura de la app

```
{{dir_app}}/
  ├── settings.gradle.kts, gradle/libs.versions.toml, gradle/wrapper/
  ├── local.properties  # sdk.dir (NO commitear)
  ├── debug.keystore    # firma de release/debug (misma key = OTA encima funciona)
  └── app/
      ├── build.gradle.kts
      └── src/main/
          ├── AndroidManifest.xml
          ├── java/<paquete con `/`>/   # código Kotlin
          └── res/xml/file_paths.xml       # FileProvider
```

- Una sola `MainActivity : FragmentActivity` con `setContent { App() }`.
- Backend único sirve REST + tiempo real; Supabase es la DB.
- Las apps NUNCA escriben directo a Supabase (todo vía el backend).

## 2. Build y pipeline de compilación

```powershell
# Compilar solo Kotlin (verificación rápida sin APK):
.\gradlew.bat compileDebugKotlin --console=plain

# APK debug + release:
.\gradlew.bat assembleDebug assembleRelease --console=plain

# Si el build "se cae" con ChildProcess.kill: reintentar (resource churn).
```

`build.gradle.kts` esencial:

```kotlin
defaultConfig {
  versionCode = N            // entero creciente, lo compara el OTA
  versionName = "x.y.z"
  ndk { abiFilters += listOf("arm64-v8a") }   // mantiene el APK pequeño
}
signingConfigs {
  create("release") {
    storeFile = file("${rootDir}/debug.keystore")  // MISMA key para debug y release
    storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
  }
}
buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
```

**Reglas críticas:**
- **Firma única**: debug y release firman con el mismo keystore. Si un dispositivo tiene la app
  instalada con firma A, solo la actualiza un APK con firma A. Verificar con:
  `apksigner verify --print-certs app-release.apk` (comparar SHA-256).
- **No dejar `externalNativeBuild` (CMake/JNI) si es vestigio**: si la app migró (p. ej. de
  llama.cpp a ML Kit) y el submódulo C++ quedó desactualizado, el build rompe con errores del
  estilo `no member named 'use_mmap'` o `undefined symbol: vtable for ...`. Solución: quitar el
  bloque `externalNativeBuild { cmake { ... } }` del gradle (y el `ndk{}` si solo era para eso).
- `buildFeatures { compose = true; buildConfig = true }` (BuildConfig.VERSION_NAME/CODE para el OTA).

## 3. Versionamiento (fuente única en Supabase)

- `versionCode`/`versionName` viven en `build.gradle.kts`.
- El servidor anuncia la versión publicada en la tabla **`{{tabla_versiones}}`**:
  - Una fila por app con clave `{{clave_version}}`.
  - Cada fila: `clave` + `valor` JSON `{"versionCode":N,"versionName":"x.y.z","apkUrl":"<url o ruta del apk>"}`.
- Endpoints: `{{endpoint_version}}` y `{{endpoint_catalogo}}`
  (el catálogo lee la tabla dinámicamente — nunca hardcodear versiones en el catálogo).

### Actualizar la tabla de versiones (Management API — sin CLI)
La CLI de Supabase puede rechazar tokens `sbp_v0_*`. Usar la Management API directamente:

```powershell
$token = "<sbp_v0_...>"; $tmp = Join-Path $env:TEMP "opencode\sq.json"
$sql = "DELETE FROM {{tabla_versiones}} WHERE clave = '{{clave_version}}';
        INSERT INTO {{tabla_versiones}} (clave, valor) VALUES ('{{clave_version}}',
        '{""versionCode"":14,""versionName"":""1.0.14"",""apkUrl"":""/public/miapp.apk""}');"
$esc = $sql.Replace('\','\\').Replace('"','\"').Replace("`r`n","\n").Replace("`n","\n")
$json = '{"query":"' + $esc + '"}'
[System.IO.File]::WriteAllText($tmp, $json, (New-Object System.Text.UTF8Encoding($false)))
curl.exe -s -X POST "https://api.supabase.com/v1/projects/<REF>/database/query" `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" --data-binary "@$tmp"
```

**Gotchas PowerShell 5.1**: comillas comidas por curl.exe (usar `--data-binary @archivo`),
`ConvertTo-Json` envuelve strings multilínea en `{"value":...}` (construir el JSON a mano),
leer archivos SQL como UTF-8 con `[System.IO.File]::ReadAllText`.

## 4. OTA (auto-update) — patrón completo

### AndroidManifest (obligatorio en Android 11+)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />  <!-- SIN ESTO NO INSTALA -->
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="{{paquete}}.fileprovider"
    android:exported="false" android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```

### file_paths.xml (instalación en el propio directorio de la app)
```xml
<paths>
  <external-files-path name="downloads" path="Download/" />  <!-- matchea getExternalFilesDir(DIRECTORY_DOWNLOADS) -->
</paths>
```
El APK se descarga a `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` — carpeta
privada de la app (sin permisos de almacenamiento) y compatible con FileProvider.

### Lógica de check + descarga + instalación
```kotlin
// check: {{endpoint_version}} → info.versionCode > pkgInfo.longVersionCode.toInt()
// descarga con OkHttp + barra de progreso (no usar res.body?.bytes() para apps grandes;
//   leer por stream con ByteArrayOutputStream + progreso)
val uri = FileProvider.getUriForFile(context, "{{paquete}}.fileprovider", file)
val intent = Intent(Intent.ACTION_VIEW).apply {
  setDataAndType(uri, "application/vnd.android.package-archive")
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)   // abre el instalador del sistema
```
- **Anti-caché**: añadir `?t=${System.currentTimeMillis()}` a la URL del APK.
- **Progreso**: `LinearProgressIndicator(progress = { downloadProgress })` + "Descargando… X MB".
- **Errores visibles**: toast con el mensaje real y logs en una sección "Logs" de la app.
- **El OTA solo baja si la instalada tiene versionCode MENOR a la publicada** — si el teléfono ya
  tiene la última, no detecta nada (comportamiento correcto; para probar, instalar una versión vieja).

### Síntoma común: "detecta la versión pero no descarga/no instala"
1. Falta `REQUEST_INSTALL_PACKAGES` (Android 11+) → el instalador del sistema rechaza el intent.
2. Firma distinta a la instalada → "App not installed".
3. `file_paths.xml` no cubre la carpeta del APK → FileProvider lanza.
4. El APK publicado no es el nuevo (no se hizo deploy).

## 5. Publicación / auto-deploy

1. Bump `versionCode`/`versionName` en `build.gradle.kts`.
2. `.\gradlew.bat assembleRelease`.
3. Copiar `app/build/outputs/apk/release/app-release.apk` → `{{dir_public_apk}}`.
4. Actualizar `{{tabla_versiones}}` (Management API, sección 3).
5. Desplegar según `{{deploy}}` (el APK debe quedar descargable).
6. Verificar: `{{endpoint_version}}` responde y el APK descarga (HTTP 200, tamaño correcto).

## 6. Patrones funcionales esenciales (independientes del contenido)

### Sesión persistente (no pedir login en cada apertura)
```kotlin
val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
// guardar: prefs.edit().putString("cliente_ref", ref).apply()
// restaurar en LaunchedEffect(Unit) al arrancar (después de definir las funciones que use)
```
- Restaurar sesión en un `LaunchedEffect` COLOCADO DESPUÉS de las funciones locales que invoca
  (Kotlin no permite forward-reference de funciones locales).

### Biometría (acceso rápido)
- `MainActivity : FragmentActivity` (requisito de `BiometricPrompt`).
- `androidx.biometric:biometric:1.1.0`.
- `BiometricPrompt(activity, executor, callback)` con `BIOMETRIC_STRONG` + `setNegativeButtonText("Cancelar")`.
- Si se permite `DEVICE_CREDENTIAL`, NO usar `setNegativeButtonText` (lanza).
- Verificar disponibilidad con `BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG)`.
- El callback ya corre en el main thread (no envolver en `withContext`).

### QR real (escaneable) — ZXing
```kotlin
implementation("com.google.zxing:core:3.5.3")
fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
  val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx,
    mapOf(EncodeHintType.MARGIN to 1))
  val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
  for (x in 0 until sizePx) for (y in 0 until sizePx)
    bmp.setPixel(x, y, if (matrix[x, y]) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt())
  return bmp
}
```
⚠️ Nunca dibujar un "pseudo-QR" con patrones de hash: ningún lector real lo decodifica y el
flujo del POS muere en silencio.

### Tiempo real + polling fallback
- Cliente de stream (p. ej. `SseClient.kt` contra `{{stream_realtime}}`): hilo daemon OkHttp,
  `readTimeout(0)` (sin timeout), parsear líneas `data: {...}`, reconexión cada 5s,
  listeners `CopyOnWriteArrayList`.
- El listener DEBE llamar a la función de refresco (no solo loguear — bug real encontrado).
- Mantener el polling como fallback (p. ej. 10s) por si el stream cae.
- El backend puede emitir el mismo evento 2 veces (directo + bridge Realtime→stream) →
  las apps deben ser idempotentes (refrescar es seguro).

### Edge-to-edge (targetSdk ≥ 35) — contenido y barras
- Header: `Modifier.fillMaxWidth().background(color).statusBarsPadding().padding(...)`
  (el fondo se extiende tras la status bar; el contenido no se solapa).
- Bottom bar: `Modifier.navigationBarsPadding()`.
- Sin esto: header pegado a la barra de notificaciones y nav inferior oculta tras la barra de gestos.

### Layout Compose: la BottomBar SIEMPRE visible
```kotlin
Column(Modifier.fillMaxSize()) {
  Header(...)
  Box(Modifier.weight(1f)) { when (tab) { /* contenido (LazyColumn con fillMaxSize) */ } }
  BottomBar(...)   // sin el Box(weight), el contenido con fillMaxSize empuja la barra FUERA de pantalla
}
```
Este es el bug clásico #1: la barra existe pero no se ve.

### Imports de Compose que cambian de paquete (versiones recientes)
- `KeyboardOptions` → `androidx.compose.foundation.text.KeyboardOptions`
- `KeyboardType` → `androidx.compose.ui.text.input.KeyboardType`
- `PasswordVisualTransformation` → `androidx.compose.ui.text.input`

### Errores de API reales que muestran al usuario (no genéricos)
OkHttp: leer el body en error y extraer `JSONObject(body).optString("error", ...)`; el cliente
muestra ese mensaje real ("Completa tus datos fiscales…", "Contraseña incorrecta", 409, etc.).

### Datos sensibles
- Nunca exponer hashes/secretos en respuestas (borrar `password_hash` antes de `res.json`).
- Normalizar valores "NULL"/vacío en backend (`cleanFiscalValue`).
- Tarjetas: guardar y mostrar SOLO los últimos 4 dígitos (máscara `•••• •••• •••• XXXX`).

## 7. Checklist de trampas comunes (orden de diagnóstico)

| Síntoma | Causa raíz | Fix |
|---|---|---|
| BottomBar no se ve / footer vacío | Contenido con `fillMaxSize` sin `weight` en el Column | `Box(Modifier.weight(1f))` alrededor del contenido |
| "no member named use_mmap" / undefined symbols mtmd | Submódulo C++ desactualizado (JNI vestigio) | Quitar `externalNativeBuild` del gradle |
| OTA detecta pero no instala | Falta `REQUEST_INSTALL_PACKAGES` o firma distinta | Manifest + apksigner verify |
| `Unresolved reference 'loadClienteData'` | Forward-reference de función local | Mover la definición antes / flag + LaunchedEffect posterior |
| `withContext` solo en coroutine (en callback) | Callback ya corre en main | Quitar withContext |
| Header pegado arriba / barra inferior oculta | Edge-to-edge sin insets (targetSdk ≥35) | `statusBarsPadding()` / `navigationBarsPadding()` |
| El POS no lee el "QR" | Pseudo-QR (hash) | ZXing real |
| Búsqueda "Producto no encontrado" siempre | La vista lee `data.sku` pero el endpoint devuelve `{product:{...}}` | `data?.product ?? data` |
| App pide login en cada apertura | Sin SharedPreferences | Sesión persistente (sección 6) |
| Toast de error genérico sin causa | El cliente descarta el body de error | Mostrar `error` real del servidor |
| El APK pesa el doble | Sin `abiFilters` (todas las ABI) | `ndk { abiFilters += listOf("arm64-v8a") }` |

## 8. Checklist final de verificación

- [ ] `.\gradlew.bat compileDebugKotlin` y `assembleRelease` OK
- [ ] `versionCode` incrementado y `versionName` coherente
- [ ] Firma: `apksigner verify --print-certs` coincide con la app instalada
- [ ] `{{dir_public_apk}}` reemplazado y descargable (200, tamaño correcto)
- [ ] `{{tabla_versiones}}` actualizada y `{{endpoint_version}}` lo refleja
- [ ] Manifest: INTERNET + REQUEST_INSTALL_PACKAGES + FileProvider + file_paths
- [ ] Sesión persistente + biometría funcionando tras reinstalación/actualización
- [ ] Stream realtime conectado y listener refrescando (no solo logueando)
- [ ] Deploy verificado en producción (`{{deploy}}`)
- [ ] Prueba OTA real: versión vieja instalada → detecta → descarga → instala encima

## Adaptadores conocidos

- Gestor inventory → `skills/android-kotlin-app/ADAPTER.md` (v1.0.0, 2026-10-04)

## Referencias

| Recurso | Ruta |
|---------|------|
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Ciclo de vida | `.agents/rules/ciclo-vida-skills.md` en BiblioSkills |
