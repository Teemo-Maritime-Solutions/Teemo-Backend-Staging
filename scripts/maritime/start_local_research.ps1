param([switch]$Restart)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'config\maritime-local.properties'))) { throw 'Local research configuration missing.' }
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
foreach ($processIdToRestart in ($listeners.OwningProcess | Sort-Object -Unique)) {
  $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $processIdToRestart"
  if ($processInfo.Name -ne 'java.exe' -or $processInfo.CommandLine -notmatch 'Teemo-Backend-Staging|upcpre202501cc1asi07324441teemosolutionsbackend') { throw 'Port 8080 belongs to a different process. No process stopped.' }
  if (-not $Restart) { throw 'Backend already running. Use -Restart explicitly.' }
  Stop-Process -Id $processIdToRestart
  Wait-Process -Id $processIdToRestart -Timeout 20 -ErrorAction SilentlyContinue
}
$runner = Join-Path $PSScriptRoot 'run_local_research_server.ps1'
$logPath = Join-Path $projectRoot ('target\local-research-server-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $PID + '.log')
$arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $runner + '" -LogPath "' + $logPath + '"'
$process = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru
@{ launcherPid = $process.Id; logPath = $logPath } | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $projectRoot 'target\local-research-server-latest.json')
Write-Output "Local research backend launcher PID: $($process.Id). Log: $logPath"
