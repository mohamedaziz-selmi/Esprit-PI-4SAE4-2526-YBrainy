@echo off
echo Starting YBrainy Predict Service on port 5001...
cd /d "%~dp0"
py -3.12 app.py
pause
