@echo off
title OrbisNet - Publication et Deploiement Automatique
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-release.ps1" %*
echo.
pause
