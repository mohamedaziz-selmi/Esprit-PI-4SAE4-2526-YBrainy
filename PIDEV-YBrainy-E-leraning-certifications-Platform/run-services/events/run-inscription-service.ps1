# Inscription Service (event registrations) — :9002
param([int]$Port = 9002, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-inscription-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
