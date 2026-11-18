@echo off
rem Runs ClassPulse from the packaged jar.
rem Usage: run.bat
setlocal
cd /d "%~dp0"
if exist "dist\ClassPulse.jar" (
    java -jar "dist\ClassPulse.jar"
) else (
    echo dist\ClassPulse.jar was not found. Run build.ps1 first.
    pause
)
