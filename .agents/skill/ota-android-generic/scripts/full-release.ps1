# full-release.ps1 — Release completo de UNA app: build + deploy + DB (skill ota-android-generic v1.0.0)
param(
    [string]$AppDir = ".",
    [string]$ServerDir = "../server/public",
    [string]$ApkName = "app.apk",
    [int]$VersionCode,
    [string]$VersionName,
    [string]$SupabaseUrl,
    [string]$SupabaseKey,
    [string]$DbTable = "app_versions",
    [string]$DbKey = "app_version",
    [string]$KeystorePath = "",
    [string]$KeystorePassword = "",
    [string]$KeyAlias = "",
    [string]$KeyPassword = ""
)

Write-Host "[release] Completo: v$VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host ""

Write-Host "=== Paso 1: Build + Deploy ===" -ForegroundColor Cyan
& "$PSScriptRoot\build-and-deploy.ps1" -AppDir $AppDir -ServerDir $ServerDir -ApkName $ApkName `
    -KeystorePath $KeystorePath -KeystorePassword $KeystorePassword `
    -KeyAlias $KeyAlias -KeyPassword $KeyPassword

if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host ""
Write-Host "=== Paso 2: Actualizar Version en DB ===" -ForegroundColor Cyan
& "$PSScriptRoot\update-version.ps1" -SupabaseUrl $SupabaseUrl -SupabaseKey $SupabaseKey `
    -DbTable $DbTable -DbKey $DbKey -VersionCode $VersionCode -VersionName $VersionName -ApkUrl "/public/$ApkName"

if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host ""
Write-Host "=======================================" -ForegroundColor Green
Write-Host "[OK] Release completado: v$VersionName" -ForegroundColor Green
Write-Host "APK: $ServerDir/public/$ApkName" -ForegroundColor Gray
Write-Host "DB Key: $DbKey" -ForegroundColor Gray
Write-Host "=======================================" -ForegroundColor Green
