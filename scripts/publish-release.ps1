#Requires -Version 5.1
# Pipeline release BiblioTube: version -> build -> Supabase -> git push -> Render hook -> verify.
# Uso: .\scripts\publish-release.ps1 -VersionCode 3 -VersionName "1.0.2"
# Requiere: .agents/publish.local con SUPABASE_SERVICE_KEY=<service_role> (gitignored).
#   La publishable key solo lee; para escribir la version hace falta service_role
#   (Supabase Dashboard -> Project Settings -> API) o el SQL del DEPLOY.md.

param(
  [Parameter(Mandatory = $true)][int]$VersionCode,
  [Parameter(Mandatory = $true)][string]$VersionName,
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$appDir = Join-Path $root 'BiblioTube-Android'
$fail = 0
function Step($msg) { Write-Host ""; Write-Host "=== $msg ===" -ForegroundColor Cyan }
function Die($msg) { Write-Host "[publish] ERROR: $msg" -ForegroundColor Red; exit 1 }

# 0. Entorno
Step '0/7 Entorno'
$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$Jbr = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path (Join-Path $Sdk 'platform-tools\adb.exe'))) { Die('sin Android SDK en ' + $Sdk) }
$env:JAVA_HOME = $Jbr; $env:ANDROID_HOME = $Sdk; $env:ANDROID_SDK_ROOT = $Sdk
$env:PATH = "$Sdk\platform-tools;$env:PATH"
$bt = Join-Path $Sdk 'build-tools\35.0.0'
$aapt = Join-Path $bt 'aapt.exe'; $apksigner = Join-Path $bt 'apksigner.bat'
if (-not (Test-Path $aapt)) { Die('falta ' + $aapt) }

# Config local (service key)
$cfgFile = Join-Path $root '.agents\publish.local'
$ServiceKey = $env:SUPABASE_SERVICE_KEY
if ((Test-Path $cfgFile) -and -not $ServiceKey) {
  foreach ($line in (Get-Content $cfgFile)) {
    if ($line -match '^\s*SUPABASE_SERVICE_KEY\s*=\s*(.+?)\s*$') { $ServiceKey = $Matches[1] }
  }
}
$SupaUrl = 'https://buccrasmylvkzymbhfwx.supabase.co'

# 1. Versionado
Step '1/7 Versionado'
$gradleFile = Join-Path $appDir 'app\build.gradle.kts'
$txt = [System.IO.File]::ReadAllText($gradleFile)
$txt2 = $txt -replace 'versionCode = \d+', "versionCode = $VersionCode"
$txt2 = $txt2 -replace 'versionName = "[^"]+"', "versionName = `"$VersionName`""
if ($txt2 -eq $txt) { Die('no se pudo actualizar version en build.gradle.kts') }
[System.IO.File]::WriteAllText($gradleFile, $txt2)
Write-Host "[publish] version -> $VersionCode / $VersionName"

# 2. Build
if (-not $SkipBuild) {
  Step '2/7 Build release'
  Push-Location $appDir
  try {
    & .\gradlew.bat assembleRelease --console=plain 2>&1 | Select-Object -Last 3
    if ($LASTEXITCODE -ne 0) { Die('assembleRelease fallo') }
  } finally { Pop-Location }
} else { Write-Host '[publish] build omitido (-SkipBuild)' }

$apk = Join-Path $appDir 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { Die('no existe ' + $apk) }

# 3. Verificar APK (firma + version)
Step '3/7 Verificar APK'
& $apksigner verify --print-certs $apk 2>&1 | Select-Object -First 2
if ($LASTEXITCODE -ne 0) { Die('firma invalida') }
$badging = & $aapt dump badging $apk 2>&1 | Select-Object -First 1
Write-Host "[publish] $badging"
if ($badging -notmatch "versionCode='$VersionCode'" -or $badging -notmatch "versionName='$VersionName'") {
  Die('el APK no trae la version esperada: ' + $badging)
}

# 4. Copiar APK al servidor
Step '4/7 Copiar APK'
Copy-Item $apk (Join-Path $root 'server\public\bibliotube.apk') -Force
Write-Host '[publish] server/public/bibliotube.apk actualizado'

# 5. Supabase: anunciar version
Step '5/7 Supabase'
if (-not $ServiceKey) {
  Die('falta SUPABASE_SERVICE_KEY (.agents/publish.local o env). Alternativa: SQL manual del DEPLOY.md')
}
$valor = @{ versionCode = $VersionCode; versionName = $VersionName; apkUrl = '/public/bibliotube.apk' } | ConvertTo-Json -Compress
$body = @{ valor = ($valor | ConvertFrom-Json) } | ConvertTo-Json -Compress -Depth 5
$H = @{ apikey = $ServiceKey; Authorization = "Bearer $ServiceKey"; 'Content-Type' = 'application/json'; Prefer = 'return=representation' }
$UA = 'BiblioTube-publish/1.0'
try {
  $r = Invoke-RestMethod -Method Patch -Uri "$SupaUrl/rest/v1/app_versions?clave=eq.app_version_bibliotube" -Headers $H -Body $body -UserAgent $UA
  Write-Host ("[publish] supabase OK: " + ($r.valor | ConvertTo-Json -Compress))
} catch {
  Die('PATCH supabase: ' + $_.Exception.Message)
}

# 6. Git push + hook Render
Step '6/7 Git push + Render'
Push-Location $root
try {
  git add BiblioTube-Android/app/build.gradle.kts server/public/bibliotube.apk
  git -c core.safecrlf=false commit -m "release: v$VersionName ($VersionCode)" --quiet 2>$null
  git push origin main 2>&1 | Select-Object -Last 1
  if ($LASTEXITCODE -ne 0) { Die('git push fallo') }
  & (Join-Path $root 'scripts\deploy-render.ps1')
  if ($LASTEXITCODE -ne 0) { Die('deploy hook fallo') }
} finally { Pop-Location }

# 7. Verificar publicacion (version + APK descargable)
Step '7/7 Verificar publicacion'
$render = 'https://bibliotube-server.onrender.com'
$deadline = (Get-Date).AddMinutes(12)
$okVer = $false
while ((Get-Date) -lt $deadline) {
  try {
    $v = Invoke-RestMethod -Uri "$render/api/app-version?app=bibliotube&t=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())" -UseBasicParsing -TimeoutSec 30
    if ($v.success -and $v.data.versionCode -eq $VersionCode) { $okVer = $true; break }
    Write-Host ("[publish] version remota: " + $v.data.versionCode + " (espera " + $VersionCode + ")")
  } catch { Write-Host '[publish] API aun no responde, reintento...' }
  Start-Sleep -Seconds 30
}
if (-not $okVer) { Die('timeout esperando version en Render (revisa dashboard Deploys)') }
Write-Host "[publish] version confirmada: $($v.data.versionName) ($($v.data.versionCode))"

$apkUrl = $v.data.apkUrl
if ($apkUrl.StartsWith('/')) { $apkUrl = $render + $apkUrl }
$okApk = $false
$deadline2 = (Get-Date).AddMinutes(5)
while ((Get-Date) -lt $deadline2) {
  try {
    $h = Invoke-WebRequest -Uri $apkUrl -Method Head -UseBasicParsing -TimeoutSec 60
    $len = [int64]$h.Headers['Content-Length']
    if ($h.StatusCode -eq 200 -and $len -gt 10MB) { $okApk = $true; break }
    Write-Host ("[publish] APK HTTP " + $h.StatusCode + " len=" + $len)
  } catch { Write-Host '[publish] APK aun no disponible, reintento...' }
  Start-Sleep -Seconds 30
}
if (-not $okApk) { Die('timeout esperando APK descargable') }
Write-Host ''
Write-Host ("[publish] PUBLICADO v$VersionName ($VersionCode). APK OK.") -ForegroundColor Green
