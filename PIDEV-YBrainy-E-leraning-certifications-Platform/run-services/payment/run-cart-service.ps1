# Cart Service — :8954
param([int]$Port = 8954, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-payment-cart.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
