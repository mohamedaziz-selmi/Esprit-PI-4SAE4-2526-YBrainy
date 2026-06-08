# Course Service — :8082
param([int]$Port = 8082, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-courses-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
