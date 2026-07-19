# Sample environment for local run (NOT deployed automatically).
# Usage: . .\deploy\windows\env.sample.ps1

$env:MONGODB_URI = "mongodb://localhost:27017/promptportal"
$env:SERVER_PORT = "8080"
$env:APP_URL = "http://localhost:3000"
$env:APP_DEV_MODE = "true"
$env:ALLOWED_EMAILS = ""
$env:AUTH_SECRET = "change-me-local-only"
$env:UPLOAD_DIR = "C:\prompt-portal-data\uploads"
$env:LOG_DIR = "C:\prompt-portal-data\logs"
$env:LOG_LEVEL = "INFO"
$env:NEXT_PUBLIC_API_URL = "http://localhost:8080"
$env:NEXT_PUBLIC_DEV_USER_EMAIL = "dev@local.test"

New-Item -ItemType Directory -Force -Path $env:UPLOAD_DIR, $env:LOG_DIR | Out-Null

Write-Host "Environment loaded for this PowerShell session."
Write-Host "UPLOAD_DIR=$env:UPLOAD_DIR"
Write-Host "LOG_DIR=$env:LOG_DIR"
