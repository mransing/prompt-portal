# Build backend jar + frontend production bundle. Does not start services.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

Write-Host "=== Backend (Maven) ==="
Set-Location (Join-Path $Root "backend")
mvn -DskipTests package

Write-Host "=== Frontend (Next.js) ==="
Set-Location (Join-Path $Root "frontend")
if (-not (Test-Path "node_modules")) { npm.cmd install }
npm.cmd run build

Write-Host "Build complete. API jar under backend\target\, frontend .next under frontend\."
