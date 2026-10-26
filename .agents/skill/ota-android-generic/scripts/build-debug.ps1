# build-debug.ps1 — Compila APK debug para pruebas (skill ota-android-generic v1.0.0)
param(
    [string]$AppDir = "."
)

Write-Host "[build] Debug APK..." -ForegroundColor Cyan

Push-Location $AppDir

try {
    Write-Host "  Limpiando..." -ForegroundColor Gray
    & ./gradlew clean 2>&1 | Out-Null

    Write-Host "  Compilando debug..." -ForegroundColor Yellow
    & ./gradlew assembleDebug

    if ($LASTEXITCODE -eq 0) {
        $apk = Get-ChildItem -Path "app/build/outputs/apk/debug/*.apk" | Select-Object -First 1
        Write-Host "  [OK] APK generado: $($apk.FullName)" -ForegroundColor Green
        Write-Host "  Size: $([math]::Round($apk.Length / 1MB, 2)) MB" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] Build fallo" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}
