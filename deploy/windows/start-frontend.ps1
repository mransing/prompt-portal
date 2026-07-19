# Start Next.js frontend (dev server). Does not deploy.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Frontend = Join-Path $Root "frontend"

if (-not $env:NEXT_PUBLIC_API_URL) {
  $env:NEXT_PUBLIC_API_URL = "http://localhost:8080"
}
if (-not $env:NEXT_PUBLIC_DEV_USER_EMAIL) {
  $env:NEXT_PUBLIC_DEV_USER_EMAIL = "dev@local.test"
}

Set-Location $Frontend
if (-not (Test-Path "node_modules")) {
  Write-Host "Installing npm dependencies..."
  npm.cmd install
}
npm.cmd run dev
