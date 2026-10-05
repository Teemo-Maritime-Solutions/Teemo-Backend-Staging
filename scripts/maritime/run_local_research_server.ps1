param([string]$LogPath)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $projectRoot
$env:MAVEN_OPTS = '-Xmx256m'
$localJava17 = Join-Path $env:USERPROFILE 'AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.16+8'
if (Test-Path -LiteralPath (Join-Path $localJava17 'bin\java.exe')) {
  $env:JAVA_HOME = $localJava17
  $env:PATH = (Join-Path $localJava17 'bin') + ';' + $env:PATH
}
$localMavenRepository = Join-Path $env:USERPROFILE '.m2\repository'
if (-not $LogPath) { $LogPath = Join-Path $projectRoot ('target\local-research-server-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $PID + '.log') }
& mvn.cmd -o -q "-Dmaven.repo.local=$localMavenRepository" '-DskipTests' '-Dspring-boot.run.jvmArguments=-Xmx2560m -Dspring.devtools.restart.enabled=false' spring-boot:run *> $LogPath
exit $LASTEXITCODE
