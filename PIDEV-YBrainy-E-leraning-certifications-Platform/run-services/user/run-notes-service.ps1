# Notes Service (FastAPI / Python) — default :8084
# NOTE: port 8084 conflicts with Personality Behavior Service when both are running.
# The group script (groups/start-user.ps1) automatically starts this on :8087 instead.
# If running standalone alongside Personality, pass -Port 8087 (or any free port).
param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-notes-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
