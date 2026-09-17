# Start everything needed to hit the Prompt Portal URLs locally.
# - Ensures MongoDB Windows service is running
# - Starts API (port 8080) in a new window
# - Starts Next.js frontend (port 3000) in a new window
# Does not install services or open firewall ports.

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$Root = Split-Path (Split-Path $ScriptDir -Parent) -Parent

$ApiPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$UiPort = 3000
$ApiUrl = "http://localhost:$ApiPort"
$UiUrl = "http://localhost:$UiPort"
$HealthUrl = "$ApiUrl/actuator/health"

function Test-PortListening {
  param([int]$Port)
  $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  return $null -ne $conn
}

function Wait-HttpOk {
  param(
    [string]$Url,
    [int]$TimeoutSec = 90,
    [string]$Label = "service"
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  Write-Host "Waiting for $Label ($Url)..."
  while ((Get-Date) -lt $deadline) {
    try {
      $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
      if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
        Write-Host "  $Label is up (HTTP $($resp.StatusCode))."
        return $true
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  }
  return $false
}

Write-Host "=== Prompt Portal - start all ==="
Write-Host "Project root: $Root"
Write-Host ""

# 1) Environment (uploads/logs dirs, Mongo URI, ports, dev flags)
. (Join-Path $ScriptDir "env.sample.ps1")
Write-Host ""

# 2) MongoDB service
$mongo = Get-Service -Name "MongoDB" -ErrorAction SilentlyContinue
if (-not $mongo) {
  Write-Warning "Windows service 'MongoDB' not found. Ensure mongod is running on localhost:27017."
} elseif ($mongo.Status -eq "Running") {
  Write-Host "MongoDB service: already running."
} else {
  Write-Host "MongoDB service: starting (Status was $($mongo.Status))..."
  try {
    Start-Service -Name "MongoDB"
    $mongo.WaitForStatus("Running", [TimeSpan]::FromSeconds(30))
    Write-Host "MongoDB service: running."
  } catch {
    Write-Warning "Could not start MongoDB service (may need Administrator). Error: $($_.Exception.Message)"
    Write-Warning "Start it manually: Start-Service MongoDB  (elevated PowerShell)"
  }
}

# 3) API
if (Test-PortListening -Port $ApiPort) {
  Write-Host "API port $ApiPort already in use - skipping start-api (assuming already running)."
} else {
  $apiScript = Join-Path $ScriptDir "start-api.ps1"
  Write-Host "Starting API in a new window..."
  Start-Process -FilePath "powershell.exe" -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $apiScript
  ) -WorkingDirectory $Root
}

if (-not (Wait-HttpOk -Url $HealthUrl -TimeoutSec 120 -Label "API")) {
  Write-Warning "API health check timed out at $HealthUrl."
  Write-Warning "Check the API window for errors (MongoDB, missing jar, Java)."
} else {
  Write-Host "API ready: $ApiUrl"
}

# 4) Frontend
if (Test-PortListening -Port $UiPort) {
  Write-Host "UI port $UiPort already in use - skipping start-frontend (assuming already running)."
} else {
  $uiScript = Join-Path $ScriptDir "start-frontend.ps1"
  Write-Host "Starting frontend in a new window..."
  Start-Process -FilePath "powershell.exe" -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $uiScript
  ) -WorkingDirectory $Root
}

if (-not (Wait-HttpOk -Url $UiUrl -TimeoutSec 120 -Label "Frontend")) {
  Write-Warning "Frontend did not respond at $UiUrl within the timeout."
  Write-Warning "Check the frontend window (npm install / next dev)."
} else {
  Write-Host "Frontend ready: $UiUrl"
}

Write-Host ""
Write-Host "=== Ready ==="
Write-Host "  UI:     $UiUrl"
Write-Host "  API:    $ApiUrl"
Write-Host "  Health: $HealthUrl"
Write-Host "  Dev login email form uses header X-Dev-User-Email"
Write-Host ""
Write-Host "To stop: .\deploy\windows\stop-all.ps1"
Write-Host "(API and frontend run in separate windows - close those windows or use stop-all.)"
