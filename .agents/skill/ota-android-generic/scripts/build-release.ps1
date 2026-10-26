# build-release.ps1 — Compila APK release firmado (skill ota-android-generic v1.0.0)
param(
    [string]$AppDir = ".",
    [string]$KeystorePath = "",        # Ruta al keystore (.jks)
    [string]$KeystorePassword = "",    # Password del keystore
    [string]$KeyAlias = "",            # Alias de la clave
    [string]$KeyPassword = ""          # Password de la clave
)

Write-Host "[build] Release APK..." -ForegroundColor Cyan

Push-Location $AppDir

try {
    Write-Host "  Limpiando..." -ForegroundColor Gray
    & ./gradlew clean 2>&1 | Out-Null

    Write-Host "  Compilando release..." -ForegroundColor Yellow

    if ($KeystorePath -and $KeystorePassword -and $KeyAlias -and $KeyPassword) {
        & ./gradlew assembleRelease `
            -Pandroid.injected.signing.store.file=$KeystorePath `
            -Pandroid.injected.signing.store.password=$KeystorePassword `
            -Pandroid.injected.signing.key.alias=$KeyAlias `
            -Pandroid.injected.signing.key.password=$KeyPassword
    } else {
        Write-Host "  [WARN] Usando firma configurada en build.gradle" -ForegroundColor Yellow
        & ./gradlew assembleRelease
    }

    if ($LASTEXITCODE -eq 0) {
        $apk = Get-ChildItem -Path "app/build/outputs/apk/release/*.apk" | Select-Object -First 1
        Write-Host "  [OK] APK firmado: $($apk.FullName)" -ForegroundColor Green
        Write-Host "  Size: $([math]::Round($apk.Length / 1MB, 2)) MB" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] Build fallo" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}
