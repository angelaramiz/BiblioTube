#Requires -Version 5.1
# Dispara un redeploy en Render via deploy hook (para APK/landing/server).
# El hook vive en .agents/render-deploy-hook.local (gitignored, nunca commitear).
# Uso: .\scripts\deploy-render.ps1

$hookFile = Join-Path $PSScriptRoot '..\.agents\render-deploy-hook.local'
if (-not (Test-Path $hookFile)) {
  Write-Host '[deploy-render] ERROR: falta .agents/render-deploy-hook.local' -ForegroundColor Red
  exit 1
}
$hook = (Get-Content $hookFile -Raw).Trim()
if (-not $hook) { Write-Host '[deploy-render] ERROR: hook vacio' -ForegroundColor Red; exit 1 }

try {
  $r = Invoke-RestMethod -Method Post -Uri $hook -UseBasicParsing
  Write-Host ("[deploy-render] OK deploy disparado: " + ($r | ConvertTo-Json -Compress))
} catch {
  Write-Host ("[deploy-render] ERROR: " + $_.Exception.Message) -ForegroundColor Red
  exit 1
}
