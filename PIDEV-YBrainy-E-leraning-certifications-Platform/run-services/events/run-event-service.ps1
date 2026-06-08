# Event Service — :9001
param([int]$Port = 9001, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-event-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
