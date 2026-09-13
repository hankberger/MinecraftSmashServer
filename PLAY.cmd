@echo off
cd /d "%~dp0"
C:\Python312\python.exe launch_local.py
if errorlevel 1 pause
