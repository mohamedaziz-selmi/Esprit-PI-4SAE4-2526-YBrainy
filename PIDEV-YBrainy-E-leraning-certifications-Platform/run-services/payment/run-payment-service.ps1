# Payment Service — :8095
param([int]$Port = 8095, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-payment-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
