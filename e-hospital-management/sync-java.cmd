@echo off
rem Verified copy of reviewed Java sources from D:\ehospital-review into this project (see docs\upgrade-report.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-java.ps1" %*
exit /b %errorlevel%
