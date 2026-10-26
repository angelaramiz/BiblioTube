---
name: emulador-android
version: 1.0.0
description: Opera el emulador Android por adb directo para QA de apps Kotlin (instalar, lanzar, capturar, tocar, escribir, verificar flujos) sin herramienta gráfica. Usar al probar una app Android en emulador.
---

# SKILL: Emulador Android para QA

## Resumen

Opera un emulador Android (AVD) por `adb` directo: instala el APK, lanza la
app, captura pantalla, vuelca la jerarquía UI e interactúa (tap/type/swipe).
No requiere Bun ni herramientas gráficas: funciona en cualquier agente con
shell + PowerShell.

## Variables del proyecto

El script `scripts/emu.ps1` lee estas variables de entorno. Cualquier acción
acepta el valor como argumento explícito, que tiene prioridad sobre la env var.

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `EMU_AVD` | Nombre del AVD a arrancar | `Medium_Phone_API_35` |
| `EMU_PKG` | Package ID de la app (requerida para `launch` sin argumento) | `com.ejemplo.app` |
| `EMU_APK` | Ruta al APK release para `install` sin argumento | `MiApp/app/build/outputs/apk/release/app-release.apk` |
| `EMU_RES` | Resolución del dispositivo (para interpretar coordenadas) | `1080x2400` |

El SDK se resuelve vía `$env:ANDROID_HOME` o `%LOCALAPPDATA%\Android\Sdk`.
Los valores concretos de cada proyecto viven en su `ADAPTER.md`, nunca aquí.

## Herramienta: `scripts/emu.ps1`

Uso desde la raíz del proyecto (ajustar ruta según dónde se instaló la skill):

```powershell
skills/emulador-android/scripts/emu.ps1 <accion> [args]
```

| Acción | Args | Qué hace |
|---|---|---|
| `status` | — | `adb devices` (¿hay emulador vivo?) |
| `boot` | — | Lanza el AVD minimizado y espera hasta `device` |
| `install` | `[apk]` | `install -r` (default: `$env:EMU_APK`) |
| `launch` | `[pkg]` | Abre la app (default: `$env:EMU_PKG`) |
| `shot` | `[salida]` | `screencap` + `pull` (default: `.opencode/emu-shot.png`) |
| `dump` | `[salida]` | `uiautomator dump` + `pull` (default: `.opencode/emu-ui.xml`) |
| `tap` | `x y` | Toque en coordenadas del dispositivo |
| `type` | `texto…` | Escribe (espacios → `%s`) en el campo con foco |
| `swipe` | `x1 y1 x2 y2 [ms]` | Arrastre (default 500 ms) |
| `key` | `código` | `keyevent` (4=Back, 66=Enter, 67=Borrar, 123=Fin) |
| `tile` | `componente` | `cmd statusbar click-tile` (ver ADAPTER del proyecto) |
| `panel` | — | Expande el panel de ajustes rápidos |
| `net` | `on\|off` | Corta/restaura wifi+datos (prueba sin conexión) |

## Flujo de prueba (agentes)

1. `status` → si no hay `device`, `boot` (tarda ~1 min).
2. `install` tras cada build release.
3. `launch` → `shot` (leer el PNG) o `dump` (bounds exactos).
4. `tap`/`type`/`swipe` según bounds del dump. Para texto: tocar campo → `type`.
5. Re-verificar con `shot`/`dump`. Borrar datos de prueba creados.
6. Casos especiales: Tile (`panel` + `tile`), sin-red (`net off` … `net on`).

## Reglas

- Capturas **solo** vía `screencap` en el equipo + `pull`. Nunca `exec-out` redirigido en PowerShell (corrompe el PNG a UTF-16).
- Coordenadas = píxeles del dispositivo (`EMU_RES`), no de la imagen miniatura.
- Si el foco cae mal al escribir: `key 123` (fin) + borrados `key 67…` + reescribir.
- `input text` necesita `%s` en vez de espacios (la acción `type` ya lo hace).
- El broadcast `AIRPLANE_MODE` está denegado en Android moderno: para sin-red usar `net off`.
- Tras `install -r` los datos locales (DataStore) se conservan; la sesión puede haber expirado (ver ADAPTER).
- Limpiar al terminar: eliminar transacciones/cuentas de prueba.

## Lo que va al ADAPTER.md (nunca aquí)

Credenciales demo, pantallas/tabs de la app, componente del Tile, gestos
propios (p. ej. pull-to-refresh), notas de sesión/login. Ver plantilla en el
ADAPTER de cada proyecto instalado.

## Adaptadores conocidos

- Gestor inventory → `skills/emulador-android/ADAPTER.md` (v1.0.0, 2026-10-04)

## Referencias

| Recurso | Ruta |
|---------|------|
| Changelog | `CHANGELOG.md` (junto a este archivo) |
| Ciclo de vida | `.agents/rules/ciclo-vida-skills.md` en BiblioSkills |
