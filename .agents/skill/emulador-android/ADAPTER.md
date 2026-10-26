# ADAPTER — emulador-android @ bibliotube

Instalado 2026-10-25. Skill estandar v1.0.0 (copia sin editar).

| Variable | Valor en bibliotube |
|----------|---------------------|
| `EMU_AVD` | `Medium_Phone_API_35` (default; alternativo `Ona_Tester_2`) |
| `EMU_PKG` | `com.bibliotube.app` (confirmado 2026-10-25) |
| `EMU_APK` | `BiblioTube-Android/app/build/outputs/apk/release/app-release.apk` (pendiente crear app) |
| `EMU_RES` | `1080x2400` (verificar contra el AVD real al primer `dump`) |

SDK: `%LOCALAPPDATA%\Android\Sdk` (`C:\Users\angel\AppData\Local\Android\Sdk`).
`ANDROID_HOME` / `ANDROID_SDK_ROOT` no estan definidos a nivel sistema — ver `scripts/setup-env.ps1`.
adb: `<sdk>\platform-tools\adb.exe` (no esta en PATH; el script lo agrega a la sesion).
