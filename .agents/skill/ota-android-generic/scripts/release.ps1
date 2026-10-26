# release.ps1 — Pipeline unificado multi-app: build → deploy → DB → verify (skill ota-android-generic v1.0.0)
# Uso: ./release.ps1 -AppsJson ./apps.json -App miapp -VersionCode 15 -VersionName "1.0.15" -ServerBaseUrl "https://mi-servidor.com" -SupabaseKey "eyJ..."
# Uso: ./release.ps1 -AppsJson ./apps.json -App all -VersionCode 15 -VersionName "1.0.15" -SkipBuild
# La configuración de apps vive en el JSON (una entrada por app: Name, Package,
# SourceDir, ApkName, DbKey, VersionEndpoint). Ver apps.example.json.

param(
    [Parameter(Mandatory=$true)]
    [string]$AppsJson,

    [Parameter(Mandatory=$true)]
    [string]$App,              # clave en apps.json, o "all"

    [Parameter(Mandatory=$true)]
    [int]$VersionCode,

    [Parameter(Mandatory=$true)]
    [string]$VersionName,

    # Build options
    [string]$BuildType = "release",  # release o debug
    [switch]$SkipBuild,               # No compilar, solo deploy

    # Firma
    [string]$KeystorePath = "",
    [string]$KeystorePassword = "",
    [string]$KeyAlias = "",
    [string]$KeyPassword = "",

    # Server
    [string]$ServerDir = "../../server/public",
    [string]$ServerBaseUrl = "",      # ej: https://mi-servidor.com (para verify)

    # Supabase
    [string]$DbTable = "app_versions",
    [string]$SupabaseKey = ""
)

# ══════════════════════════════════════════════════════════
# CONFIGURACION (del JSON del proyecto, no hardcodeada)
# ══════════════════════════════════════════════════════════

if (-not (Test-Path -LiteralPath $AppsJson)) {
    Write-Host "  [FAIL] No existe $AppsJson (ver apps.example.json)" -ForegroundColor Red
    exit 1
}
$Apps = (Get-Content -LiteralPath $AppsJson -Raw | ConvertFrom-Json).apps

# ══════════════════════════════════════════════════════════
# FUNCIONES
# ══════════════════════════════════════════════════════════

function Write-Step {
    param([string]$Step, [string]$Message)
    Write-Host ""
    Write-Host "=== $Step ===" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Gray
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  [OK] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "  [FAIL] $Message" -ForegroundColor Red
}

function Build-App {
    param(
        [string]$AppKey,
        $Config
    )

    $sourceDir = $Config.SourceDir
    $buildType = $BuildType

    Write-Step "BUILD: $($Config.Name)" "Compilando $buildType en $sourceDir"

    if (-not (Test-Path $sourceDir)) {
        Write-Fail "Directorio no encontrado: $sourceDir"
        return $false
    }

    Push-Location $sourceDir
    try {
        Write-Host "  Limpiando..." -ForegroundColor Gray
        & ./gradlew clean 2>&1 | Out-Null

        $task = if ($buildType -eq "debug") { "assembleDebug" } else { "assembleRelease" }
        Write-Host "  Ejecutando $task..." -ForegroundColor Yellow

        $gradleArgs = @($task)
        if ($buildType -eq "release" -and $KeystorePath) {
            $gradleArgs += "-Pandroid.injected.signing.store.file=$KeystorePath"
            $gradleArgs += "-Pandroid.injected.signing.store.password=$KeystorePassword"
            $gradleArgs += "-Pandroid.injected.signing.key.alias=$KeyAlias"
            $gradleArgs += "-Pandroid.injected.signing.key.password=$KeyPassword"
        }

        & ./gradlew @gradleArgs

        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Build fallo para $AppKey"
            return $false
        }

        $apkDir = "app/build/outputs/apk/$buildType"
        $apk = Get-ChildItem -Path "$apkDir/*.apk" | Select-Object -First 1
        if (-not $apk) {
            Write-Fail "No se encontro APK en $apkDir"
            return $false
        }

        Write-Ok "APK generado: $($apk.Name) ($([math]::Round($apk.Length / 1MB, 2)) MB)"
        return $apk.FullName
    } finally {
        Pop-Location
    }
}

function Deploy-App {
    param(
        [string]$ApkSource,
        [string]$ApkName
    )

    $destDir = Join-Path $ServerDir "public"
    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }

    $destFile = Join-Path $destDir $ApkName
    Copy-Item $ApkSource $destFile -Force

    $size = [math]::Round((Get-Item $destFile).Length / 1MB, 2)
    Write-Ok "Copiado a: $destFile ($size MB)"
    return $true
}

function Update-Db {
    param(
        [string]$DbKey,
        [string]$ApkName
    )

    if (-not $SupabaseKey) {
        Write-Host "  [WARN] Saltando DB (no se proporciono SupabaseKey)" -ForegroundColor Yellow
        return $true
    }

    & "$PSScriptRoot\update-version.ps1" -SupabaseUrl $ServerBaseUrl -SupabaseKey $SupabaseKey `
        -DbTable $DbTable -DbKey $DbKey -VersionCode $VersionCode -VersionName $VersionName `
        -ApkUrl "/public/$ApkName"

    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Error actualizando DB"
        return $false
    }
    Write-Ok "DB actualizada: $DbKey = v$VersionName (code=$VersionCode)"
    return $true
}

function Verify-Endpoint {
    param(
        [string]$AppKey,
        $Config
    )

    if (-not $ServerBaseUrl -or -not $Config.VersionEndpoint) {
        Write-Host "  [WARN] Saltando verify (sin ServerBaseUrl o VersionEndpoint)" -ForegroundColor Yellow
        return $true
    }

    try {
        $url = "$ServerBaseUrl$($Config.VersionEndpoint)"
        $response = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 10
        if ($response.versionCode -eq $VersionCode) {
            Write-Ok "Endpoint verificado: v$($response.versionName) (code=$($response.versionCode))"
        } else {
            Write-Host "  [WARN] Endpoint devuelve code=$($response.versionCode), esperado $VersionCode" -ForegroundColor Yellow
        }
        return $true
    } catch {
        Write-Host "  [WARN] No se pudo verificar endpoint: $($_.Exception.Message)" -ForegroundColor Yellow
        return $true
    }
}

# ══════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════

$appsToProcess = if ($App -eq "all") { @($Apps.PSObject.Properties.Name) } else { @($App) }
$results = @{}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Magenta
Write-Host "  PIPELINE DE RELEASE" -ForegroundColor Magenta
Write-Host "  Version: $VersionName (code=$VersionCode)" -ForegroundColor Magenta
Write-Host "  Apps: $($appsToProcess -join ', ')" -ForegroundColor Magenta
Write-Host "===============================================" -ForegroundColor Magenta

foreach ($appKey in $appsToProcess) {
    $config = $Apps.$appKey
    if (-not $config) {
        Write-Fail "App '$appKey' no esta en $AppsJson"
        $results[$appKey] = "FALLO"
        continue
    }
    Write-Host ""
    Write-Host "---------------------------------------------" -ForegroundColor White
    Write-Host "  $($config.Name)" -ForegroundColor White
    Write-Host "---------------------------------------------" -ForegroundColor White

    $failed = $false

    if (-not $SkipBuild) {
        $apkPath = Build-App -AppKey $appKey -Config $config
        if (-not $apkPath) { $failed = $true }
    } else {
        $apkDir = "$($config.SourceDir)/app/build/outputs/apk/$BuildType"
        $apkPath = (Get-ChildItem -Path "$apkDir/*.apk" | Select-Object -First 1).FullName
        if (-not $apkPath) {
            Write-Fail "No se encontro APK existente en $apkDir"
            $failed = $true
        }
    }

    if (-not $failed) {
        $deployed = Deploy-App -ApkSource $apkPath -ApkName $config.ApkName
        if (-not $deployed) { $failed = $true }
    }

    if (-not $failed) {
        $dbUpdated = Update-Db -DbKey $config.DbKey -ApkName $config.ApkName
        if (-not $dbUpdated) { $failed = $true }
    }

    if (-not $failed) {
        Start-Sleep -Seconds 2
        Verify-Endpoint -AppKey $appKey -Config $config | Out-Null
    }

    $results[$appKey] = if ($failed) { "FALLO" } else { "OK" }
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Green
Write-Host "  RESUMEN DEL RELEASE" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green

foreach ($appKey in $appsToProcess) {
    $name = if ($Apps.$appKey) { $Apps.$appKey.Name } else { $appKey }
    $status = $results[$appKey]
    Write-Host "  $($name.PadRight(25)) $status" -ForegroundColor $(if ($status -eq "OK") { "Green" } else { "Red" })
}

Write-Host ""
Write-Host "  Version: $VersionName (code=$VersionCode)" -ForegroundColor Gray
Write-Host "=======================================" -ForegroundColor Green
