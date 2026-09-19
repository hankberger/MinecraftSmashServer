@echo off
cd /d "%~dp0"
C:\Python312\python.exe launch_local.py %*
set "smash_exit=%errorlevel%"
if not "%smash_exit%"=="0" pause
exit /b %smash_exit%
