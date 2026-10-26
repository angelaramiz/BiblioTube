# update-version.ps1 — Actualiza la versión en Supabase vía PostgREST (skill ota-android-generic v1.0.0)
param(
    [string]$SupabaseUrl,              # URL del proyecto (ej: https://xxx.supabase.co)
    [string]$SupabaseKey,              # Service role key
    [string]$DbTable = "app_versions", # Tabla de versiones (ver ADAPTER)
    [string]$DbKey = "app_version",    # Key de la app en la tabla
    [int]$VersionCode,                 # Nuevo versionCode (entero)
    [string]$VersionName,              # Nuevo versionName (ej: "1.0.5")
    [string]$ApkUrl = "/public/app.apk"
)

Write-Host "[db] Actualizando version en Supabase..." -ForegroundColor Cyan

$valor = @{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = $ApkUrl
} | ConvertTo-Json -Compress

$body = @{
    valor = $valor
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod `
        -Uri "$SupabaseUrl/rest/v1/$DbTable?clave=eq.$DbKey" `
        -Method PATCH `
        -Headers @{
            "apikey" = $SupabaseKey
            "Authorization" = "Bearer $SupabaseKey"
            "Content-Type" = "application/json"
            "Prefer" = "return=minimal"
        } `
        -Body $body

    Write-Host "[OK] Version actualizada: $VersionName (code=$VersionCode)" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
