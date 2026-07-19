# Retention purge CLI (for Task Scheduler later). Does not schedule anything now.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Backend = Join-Path $Root "backend"
$jar = Get-ChildItem -Path (Join-Path $Backend "target") -Filter "prompt-portal-api-*.jar" -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notlike "*sources*" } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  throw "Build the API jar first (deploy\windows\build-all.ps1)."
}

if (-not $env:MONGODB_URI) {
  . (Join-Path $PSScriptRoot "env.sample.ps1")
}

java -jar $jar.FullName --purge-stale
