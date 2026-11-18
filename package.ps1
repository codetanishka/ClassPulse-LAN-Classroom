# Builds the Windows installer with jpackage.
# Requires the build output (run .\build.ps1 first) and WiX 3.x.
# WiX is detected on PATH, under tools\wix, or via the WIX environment variable.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not (Test-Path 'dist\ClassPulse.jar')) {
    throw 'dist\ClassPulse.jar is missing. Run .\build.ps1 first.'
}
if (-not (Test-Path 'assets\ClassPulse.ico')) {
    throw 'assets\ClassPulse.ico is missing. Run .\build.ps1 first.'
}

$wixCandidates = @()
if ($env:WIX) { $wixCandidates += $env:WIX }
$wixCandidates += (Join-Path $root 'tools\wix')
if (Get-Command candle.exe -ErrorAction SilentlyContinue) {
    $wixCandidates += (Split-Path (Get-Command candle.exe).Source -Parent)
}
$wixHome = $wixCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'candle.exe')) } | Select-Object -First 1
if (-not $wixHome) {
    throw 'WiX 3.x was not found. Put candle.exe/light.exe on PATH or extract WiX into tools\wix.'
}
$env:PATH = $wixHome + ';' + $env:PATH
Write-Host "Using WiX from $wixHome" -ForegroundColor Cyan

if ($env:JAVA_HOME) { $jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe' } else { $jpackage = 'jpackage' }

Write-Host '== Building Windows installer ==' -ForegroundColor Cyan
if (Test-Path dist\installer) { Remove-Item dist\installer -Recurse -Force }
New-Item -ItemType Directory -Force -Path dist\installer | Out-Null
if (Test-Path dist\app-image) { Remove-Item dist\app-image -Recurse -Force }

& $jpackage `
    --type exe `
    --name ClassPulse `
    --app-version 1.0.0 `
    --vendor 'ClassPulse' `
    --description 'LAN Classroom Interaction System' `
    --input dist `
    --main-jar ClassPulse.jar `
    --main-class classpulse.Main `
    --icon assets\ClassPulse.ico `
    --dest dist\installer `
    --java-options '-Dfile.encoding=UTF-8' `
    --win-menu `
    --win-menu-group ClassPulse `
    --win-shortcut `
    --win-dir-chooser `
    --win-per-user-install
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

Write-Host ''
Get-ChildItem dist\installer | Select-Object Name, Length
Write-Host ('Installer complete: ' + (Join-Path $root 'dist\installer')) -ForegroundColor Green
