# Warning-Ban-Appeal Service — :8086
param([int]$Port = 8086, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-warning-ban-appeal-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
