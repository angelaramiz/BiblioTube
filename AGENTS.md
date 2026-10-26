# bibliotube — AGENTS.md

App Android Kotlin (Compose) `com.bibliotube.app` + backend Express + Supabase. Dominio aun vacio (v1 = shell + OTA).

## Estructura

- `BiblioTube-Android/` — app (`MainActivity: FragmentActivity` → `App()`). Entrypoints: `App.kt`, `api/ApiClient.kt` (OTA + boveda con JWT), `data/remote/AuthClient.kt` (GoTrue REST, sin SDK), `data/VaultRepository.kt` (Room verdad + sync), `data/local/` (Room), `ui/` (Auth + boveda), `work/` (Sync + recordatorios).
- `BiblioTube-Android/bt-downloader/` — acople yt-dlp (`com.bibliotube.downloader`). Ojo: youtubedl-android 0.17.3 solo expone `addOption` de 2 args; `BtUpdate` devuelve String (no exponer tipos de la lib a la app).
- `server/` — Express ESM (`server.js`): `GET /api/app-version?app=bibliotube`, `GET /api/app-catalog`, `GET /api/events/stream` (SSE). APKs en `server/public/`.
- `apps.json` — config release OTA (raiz, la lee `release.ps1`). `.agents/skill/<nombre>/` — skills + `ADAPTER.md` local. `SKILL.md` no se edita.
- `scripts/setup-env.ps1` — entorno Android. `.agents/tasks.md` — estado.
- Deploy: `render.yaml` (Blueprint) + `DEPLOY.md` (release flow). APK se commitea en `server/public/bibliotube.apk`.

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

- Capturas SOLO `screencap` + `pull` (el script ya lo hace). `exec-out` redirigido en PowerShell corrompe el PNG.
- Release flow: bump `versionCode`/`versionName` → `assembleRelease` → copiar APK a `server/public/bibliotube.apk` → UPDATE fila `app_version_bibliotube` en tabla `app_versions` → verificar endpoint. Firma debug=release (mismo `debug.keystore`): obligatoria para que el OTA instale encima.
- Supabase: `https://buccrasmylvkzymbhfwx.supabase.co`, tabla `app_versions(clave, valor JSONB)`, schema en `server/supabase.sql` (incluye RLS solo-lectura). La publishable key solo lee; escrituras via dashboard o `service_role`.
- Emulador llega al backend con `http://10.0.2.2:3000` (default ya cableado en `App.kt`).

## Convenciones

- Responder siempre en espanol.
- Apps nunca escriben directo a Supabase: todo via backend. Errores de API: mostrar el `error` real del servidor.
- `scripts/*.ps1` en ASCII puro (PS 5.1 rompe con emojis/acentos en strings).
- Prueba OTA real = instalar APK viejo → detectar → descargar → instalar encima.
