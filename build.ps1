# Builds ClassPulse: compiles the app, generates the brand assets, runs the
# headless network self-test and creates dist\ClassPulse.jar.
# Run from anywhere:  powershell -ExecutionPolicy Bypass -File .\build.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$javaHome = $env:JAVA_HOME
if ($javaHome) {
    $javac = Join-Path $javaHome 'bin\javac.exe'
    $java = Join-Path $javaHome 'bin\java.exe'
    $jar = Join-Path $javaHome 'bin\jar.exe'
} else {
    $javac = 'javac'
    $java = 'java'
    $jar = 'jar'
}

Write-Host '== Cleaning build output ==' -ForegroundColor Cyan
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item -ItemType Directory -Force -Path build\classes | Out-Null
New-Item -ItemType Directory -Force -Path build\test-classes | Out-Null
New-Item -ItemType Directory -Force -Path dist | Out-Null

Write-Host '== Compiling application ==' -ForegroundColor Cyan
$mainSources = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -d build\classes $mainSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

Write-Host '== Generating logo and icon assets ==' -ForegroundColor Cyan
& $java -cp build\classes classpulse.brand.Brand assets
if ($LASTEXITCODE -ne 0) { throw 'Asset generation failed.' }

Write-Host '== Running network self-test ==' -ForegroundColor Cyan
$testSources = Get-ChildItem -Recurse -Filter *.java src\test\java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -cp build\classes -d build\test-classes $testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& $java '-Djava.awt.headless=true' -cp "build\classes;build\test-classes" classpulse.NetworkSelfTest
if ($LASTEXITCODE -ne 0) { throw 'Network self-test failed.' }

Write-Host '== Packaging ClassPulse.jar ==' -ForegroundColor Cyan
if (Test-Path dist\ClassPulse.jar) { Remove-Item dist\ClassPulse.jar -Force }
& $jar --create --file dist\ClassPulse.jar --main-class classpulse.Main -C build\classes .
if ($LASTEXITCODE -ne 0) { throw 'Jar packaging failed.' }

Write-Host ''
Write-Host ('Build complete: ' + (Join-Path $root 'dist\ClassPulse.jar')) -ForegroundColor Green
Write-Host 'Run it with:  java -jar dist\ClassPulse.jar'
