# Start Spring Boot API (does not install Windows service).
# Prerequisites: MongoDB service running, JDK on PATH, built jar or Maven.

$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Backend = Join-Path $Root "backend"

# Load sample env if present session vars empty
if (-not $env:MONGODB_URI) {
  . (Join-Path $PSScriptRoot "env.sample.ps1")
}

Set-Location $Backend

$jar = Get-ChildItem -Path (Join-Path $Backend "target") -Filter "prompt-portal-api-*.jar" -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if ($jar) {
  Write-Host "Starting $($jar.FullName)"
  java -jar $jar.FullName
} else {
  Write-Host "No jar found; running mvn spring-boot:run"
  mvn spring-boot:run
}
