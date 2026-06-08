# Scraper Service — :8093
param([int]$Port = 8093, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-payment-scraper.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
