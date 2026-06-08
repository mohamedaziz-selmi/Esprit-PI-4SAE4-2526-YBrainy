# User Service — :8899
param([int]$Port = 8899, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-user-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
