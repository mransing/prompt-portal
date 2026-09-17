# Stop local Prompt Portal API and frontend processes (by listening ports).
# Does not stop the MongoDB Windows service.

$ErrorActionPreference = "Continue"
$ApiPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$UiPort = 3000

function Stop-ListenersOnPort {
  param(
    [int]$Port,
    [string]$Label
  )
  $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $conns) {
    Write-Host ("{0} (port {1}): nothing listening." -f $Label, $Port)
    return
  }
  $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($procId in $pids) {
    try {
      $p = Get-Process -Id $procId -ErrorAction Stop
      Write-Host ("{0} (port {1}): stopping PID {2} ({3})..." -f $Label, $Port, $procId, $p.ProcessName)
      Stop-Process -Id $procId -Force -ErrorAction Stop
    } catch {
      Write-Warning ("Could not stop PID {0} on port {1}: {2}" -f $procId, $Port, $_.Exception.Message)
    }
  }
}

Write-Host "=== Prompt Portal - stop API + frontend ==="
Stop-ListenersOnPort -Port $ApiPort -Label "API"
Stop-ListenersOnPort -Port $UiPort -Label "Frontend"
Write-Host "Done. MongoDB service left running."
