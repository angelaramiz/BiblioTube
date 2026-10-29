# bibliotube — AGENTS.md

Boveda de videos (`com.bibliotube.app`, Compose + Material3) + backend Express + Supabase. Auth con login, carpetas, videos (preview oEmbed, prioridad, recordatorios), descargas yt-dlp (`bt-downloader`), share target con guardado rapido, OTA con auto-update.

## Estructura

- `BiblioTube-Android/` — app (`MainActivity: FragmentActivity` → `App()`). Entrypoints: `App.kt` (OTA + navegacion), `ShareActivity.kt` (share target, tarea propia translucida), `api/ApiClient.kt` + `api/ServerConfig.kt` (Render interno, sin UI de servidor), `data/SessionManager.kt` (sesion StateFlow unica), `data/VaultRepository.kt`, `data/local/` (Room, `exportSchema=true` + `schemas/`), `ui/` (Auth + boveda + `theme/`), `work/` (Sync + recordatorios), `downloader/DownloaderProvider.kt` (singleton yt-dlp).
- `BiblioTube-Android/bt-downloader/` — acople yt-dlp (`com.bibliotube.downloader`). Ojo: youtubedl-android 0.17.3 solo expone `addOption` de 2 args; `BtUpdate` devuelve String (no exponer tipos de la lib a la app). **`jniLibs.useLegacyPackaging = true` + `android:extractNativeLibs="true"` son obligatorios**: la libreria busca `libpython.zip.so` en `nativeLibraryDir` y con AGP 8 (default `false`) la carpeta queda vacia -> `YoutubeDLException: failed to initialize`. **YouTube exige yt-dlp al dia**: se actualiza nightly 1/dia (`DownloaderProvider.updateIfStale`) y se descarga con `--extractor-args youtube:player_client=tv,web_safari` (fallback `android_vr` -> default). Cookies Netscape opcionales en Ajustes para edad/privados.
- `server/` — Express ESM (`server.js` thin; logica en `src/`): version, catalog, SSE, boveda (JWT→RLS), preview oEmbed. Tests `npm test` (node:test). APKs en `server/public/`.
- `.agents/skill/<nombre>/` — skills + `ADAPTER.md` local. `SKILL.md` no se edita.
- `scripts/`: `setup-env.ps1`, `deploy-render.ps1` (hook gitignored), `publish-release.ps1` (pipeline 7 pasos).
- Deploy: `render.yaml` (Blueprint, Node 22) + `DEPLOY.md`. APK se commitea en `server/public/bibliotube.apk`.
- Diseño: `DESIGN.md` (contrato) + `ui/theme/` (`dynamicColor=false`).

## Setup (Windows + PowerShell 5.1, sesion nueva = repetir)

```powershell
. .\scripts\setup-env.ps1   # ANDROID_HOME, JAVA_HOME=JBR17, platform-tools al PATH
```

- Gradle SOLO via `.\gradlew.bat` dentro de `BiblioTube-Android/` (no hay gradle en PATH; wrapper 8.7, descarga la distro en el primer build).
- Room usa KSP (`2.0.20-1.0.25`): el root `build.gradle.kts` debe declarar TODOS los plugins con `apply false` (si falta uno, error `already on the classpath`).
- Gradle/AGP DEBE correr con JBR 17. El Java 25 del sistema rompe el build. `app/build.gradle.kts` ya fija `compileOptions` + `jvmTarget` a 17 (AGP usa 8 por defecto → error `Inconsistent JVM-target`).
- `local.properties` (sdk.dir) y `debug.keystore` ya existen y son locales. `server/.env` tiene secretos reales: no commitear.
- `adb` no esta en PATH global. Emulador: `Medium_Phone_API_35` (alt: `Ona_Tester_2`).

## Comandos

```powershell
cd BiblioTube-Android; .\gradlew.bat compileDebugKotlin --console=plain  # check rapido
.\gradlew.bat assembleRelease --console=plain                             # APK firmado en app/build/outputs/apk/release/
cd server; npm install; node server.js                                     # backend :3000
# Emulador (EMU_AVD/EMU_PKG/EMU_APK via env o args; crear .opencode/ antes de shot):
.\.agents\skill\emulador-android\scripts\emu.ps1 status|boot|install|launch|shot|dump|tap|type
```

- Capturas SOLO `screencap` + `pull` (el script ya lo hace). `exec-out` redirigido en PowerShell corrompe el PNG. `>` de PowerShell corrompe binarios (UTF-16): para `git show` a archivo usar `cmd /c` con redireccion.
- Release SOLO via `.\scripts\publish-release.ps1 -VersionCode N -VersionName "x.y.z"` (versiona, build, verifica firma por contenido —`apksigner.bat` devuelve -1 aun en exito—, copia APK, PATCH Supabase con service key, push, hook, verifica hasta confirmar). Requiere `.agents/publish.local` con `SUPABASE_SERVICE_KEY`. Feature code se commitea ANTES (el pipeline solo agrega version + APK).
- Firma unica debug+release (mismo `debug.keystore`): obligatoria para OTA e `install -r` entre build types.
- Supabase: tabla `app_versions(clave, valor JSONB)` + `profiles/categories/videos` con RLS (`server/supabase.sql`). Publishable solo lee; escrituras con `service_role` + `-UserAgent 'BiblioTube-publish/1.0'` (PS 5.1 envia UA de navegador y Supabase rechaza secret keys).
- `$ErrorActionPreference='Continue'` en scripts con nativos: con `Stop`, el stderr redirigido (`2>&1`) se vuelve error terminal (muerde a `git push` "Everything up-to-date").
- OTA end-to-end: version vieja → detectar → descargar (streaming a disco + resume `Range` + 3 reintentos + gate `getPackageArchiveInfo`) → instalador (origenes + Play Protect) → abre nueva con sesion intacta. Limpieza de `updates/` al abrir ya actualizado.
- Emulador: `force-stop` antes de verificar cambios de tema/modo (proceso obsoleto miente); tras `tap` en dialogos del sistema usar `dump` (no estimar); Gboard puede secuestrar `input text` (panel stylus → Back/Cancel y reintentar); `monkey launch` falla recien arrancado (reintentar).

## Convenciones

- Responder siempre en espanol.
- Apps nunca escriben directo a Supabase: todo via backend. Errores de API: mostrar el `error` real del servidor.
- `scripts/*.ps1` en ASCII puro (PS 5.1 rompe con emojis/acentos en strings).
- Compose: `Text` sin color hereda negro siempre → `LocalContentColor provides onBackground` en cada raiz + fondo explicito `colorScheme.background` (sistema dark + tema claro = capa negra si no). `DESIGN.md` manda; sin `dynamicColor`.
- Prueba OTA real = instalar APK viejo → detectar → descargar → instalar encima.
