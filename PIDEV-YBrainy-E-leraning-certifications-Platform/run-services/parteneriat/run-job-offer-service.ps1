# Job Offer Service — :8182
param([int]$Port = 8182, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-new-parteneriat-job-offer.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
