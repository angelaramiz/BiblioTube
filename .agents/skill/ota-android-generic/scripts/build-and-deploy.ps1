# build-and-deploy.ps1 — Build release y copia al directorio del servidor (skill ota-android-generic v1.0.0)
param(
    [string]$AppDir = ".",
    [string]$ServerDir = "../server/public",
    [string]$ApkName = "app.apk",
    [string]$KeystorePath = "",
    [string]$KeystorePassword = "",
    [string]$KeyAlias = "",
    [string]$KeyPassword = ""
)

Write-Host "[release] Build + Deploy APK" -ForegroundColor Cyan

& "$PSScriptRoot\build-release.ps1" -AppDir $AppDir `
    -KeystorePath $KeystorePath `
    -KeystorePassword $KeystorePassword `
    -KeyAlias $KeyAlias `
    -KeyPassword $KeyPassword

if ($LASTEXITCODE -ne 0) { exit 1 }

$apk = Get-ChildItem -Path "$AppDir/app/build/outputs/apk/release/*.apk" | Select-Object -First 1
if (-not $apk) {
    Write-Host "[FAIL] No se encontro APK release" -ForegroundColor Red
    exit 1
}

$destDir = Join-Path $ServerDir "public"
if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
}

$destFile = Join-Path $destDir $ApkName
Copy-Item $apk.FullName $destFile -Force

Write-Host "[OK] APK copiado a: $destFile" -ForegroundColor Green
Write-Host "Size: $([math]::Round((Get-Item $destFile).Length / 1MB, 2)) MB" -ForegroundColor Gray
