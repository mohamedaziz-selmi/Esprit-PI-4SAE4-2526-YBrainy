# Feedback Service — :9004
param([int]$Port = 9004, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-feedback-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
