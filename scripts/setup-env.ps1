#Requires -Version 5.1
# Setup de entorno Android para bibliotube (sesion actual).
# Uso: . .\scripts\setup-env.ps1  (con punto = aplica a la sesion)

$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$Jbr = 'C:\Program Files\Android\Android Studio\jbr'
$err = 0

if (-not (Test-Path (Join-Path $Sdk 'platform-tools\adb.exe'))) {
  Write-Host "[setup-env] ERROR: no existe $Sdk\platform-tools\adb.exe" -ForegroundColor Red
  $err = 1
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:JAVA_HOME = $Jbr
$env:PATH = "$Sdk\platform-tools;$Sdk\emulator;$env:PATH"
# Gradle/AGP debe correr con Java 17 (JBR). El java 25 del sistema rompe el build.
$env:ORG_GRADLE_JAVA_HOME = $Jbr
$env:JAVA17 = Join-Path $Jbr 'bin\java.exe'

Write-Host "[setup-env] ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "[setup-env] JAVA_HOME=$env:JAVA_HOME"
& (Join-Path $Jbr 'bin\java.exe') -version 2>&1 | Select-Object -First 1
& (Join-Path $Sdk 'platform-tools\adb.exe') version 2>&1 | Select-Object -First 1
& (Join-Path $Sdk 'emulator\emulator.exe') -list-avds 2>&1
if ($err -eq 1) { exit 1 }
Write-Host '[setup-env] OK. Para persistirlo, define ANDROID_HOME/JAVA_HOME en variables de sistema.'
