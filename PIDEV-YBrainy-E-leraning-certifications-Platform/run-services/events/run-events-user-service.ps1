# Events User Service (user data for the events stack) — :9003
param([int]$Port = 9003, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-events-user-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
