# Events ML Service — :9010
param([int]$Port = 9010, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-events-ml-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
