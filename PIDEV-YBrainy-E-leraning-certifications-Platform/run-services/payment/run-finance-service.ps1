# Finance Service — :8995
param([int]$Port = 8995, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-payment-finance.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
