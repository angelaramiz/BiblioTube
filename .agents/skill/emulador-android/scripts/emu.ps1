<# emu.ps1 — Operación del emulador Android por adb directo (skill estándar v1.0.0).
   Uso desde la raíz del proyecto (ajustar ruta según instalación):
     skills/emulador-android/scripts/emu.ps1 <accion> [args]
   Configuración por variables de entorno (ver ADAPTER.md del proyecto):
     EMU_AVD = nombre del AVD            (default: Medium_Phone_API_35)
     EMU_PKG = package ID de la app      (requerido para launch sin argumento)
     EMU_APK = ruta al APK release       (requerido para install sin argumento)
   Ver SKILL.md en la skill para el workflow completo. #>
param(
    [Parameter(Position = 0)][string]$Action = "status",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest
)

$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$Adb = Join-Path $Sdk "platform-tools\adb.exe"
$EmuExe = Join-Path $Sdk "emulator\emulator.exe"
$Avd = if ($env:EMU_AVD) { $env:EMU_AVD } else { "Medium_Phone_API_35" }
$Pkg = $env:EMU_PKG
$DefaultApk = $env:EMU_APK

function AdbOf([string[]]$a) { & $Adb @a }

switch ($Action) {
    "status" {
        AdbOf @("devices")
    }
    "boot" {
        $running = (& $Adb devices) -match "emulator-.*device"
        if (-not $running) {
            Start-Process -FilePath $EmuExe -ArgumentList "-avd $Avd" -WindowStyle Minimized
            Write-Output "AVD lanzado, esperando device..."
        }
        for ($i = 0; $i -lt 24; $i++) {
            Start-Sleep -Seconds 5
            $running = (& $Adb devices) -match "emulator-.*device"
            if ($running) { Write-Output "Emulador listo."; break }
        }
        AdbOf @("devices")
    }
    "install" {
        $apk = if ($Rest.Count -ge 1) { $Rest[0] } else { $DefaultApk }
        if (-not $apk) { throw "Sin APK: pasa la ruta como argumento o define EMU_APK (ver ADAPTER.md)." }
        AdbOf @("install", "-r", $apk)
    }
    "launch" {
        $pkg = if ($Rest.Count -ge 1) { $Rest[0] } else { $Pkg }
        if (-not $pkg) { throw "Sin paquete: pasa el package como argumento o define EMU_PKG (ver ADAPTER.md)." }
        AdbOf @("shell", "monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    }
    "shot" {
        $out = if ($Rest.Count -ge 1) { $Rest[0] } else { ".opencode/emu-shot.png" }
        AdbOf @("shell", "screencap -p /sdcard/emu_preview.png")
        AdbOf @("pull", "/sdcard/emu_preview.png", $out)
    }
    "dump" {
        $out = if ($Rest.Count -ge 1) { $Rest[0] } else { ".opencode/emu-ui.xml" }
        AdbOf @("shell", "uiautomator dump /sdcard/ui.xml")
        AdbOf @("pull", "/sdcard/ui.xml", $out)
    }
    "tap" {
        if ($Rest.Count -lt 2) { throw "Uso: emu.ps1 tap <x> <y>" }
        AdbOf @("shell", "input tap $($Rest[0]) $($Rest[1])")
    }
    "type" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 type <texto...>" }
        $text = ($Rest -join " ").Replace(" ", "%s")
        AdbOf @("shell", "input text $text")
    }
    "swipe" {
        if ($Rest.Count -lt 4) { throw "Uso: emu.ps1 swipe <x1> <y1> <x2> <y2> [ms]" }
        $ms = if ($Rest.Count -ge 5) { $Rest[4] } else { "500" }
        AdbOf @("shell", "input swipe $($Rest[0]) $($Rest[1]) $($Rest[2]) $($Rest[3]) $ms")
    }
    "key" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 key <codigo>  (4=Back 66=Enter 67=Borrar 123=Fin)" }
        AdbOf @("shell", "input keyevent $($Rest[0])")
    }
    "tile" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 tile <componente>  (ver componente en ADAPTER.md)" }
        AdbOf @("shell", "cmd statusbar click-tile $($Rest[0])")
    }
    "panel" {
        AdbOf @("shell", "cmd statusbar expand-settings")
    }
    "net" {
        if ($Rest.Count -lt 1) { throw "Uso: emu.ps1 net <on|off>" }
        if ($Rest[0] -eq "off") {
            AdbOf @("shell", "svc wifi disable"); AdbOf @("shell", "svc data disable")
            Write-Output "Red cortada (usar 'net on' para restaurar)."
        } else {
            AdbOf @("shell", "svc wifi enable"); AdbOf @("shell", "svc data enable")
            Write-Output "Red restaurada."
        }
    }
    default { throw "Accion desconocida: $Action (status|boot|install|launch|shot|dump|tap|type|swipe|key|tile|panel|net)" }
}
