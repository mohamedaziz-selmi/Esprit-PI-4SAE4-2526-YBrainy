# Partnership Service — :8181
param([int]$Port = 8181, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-new-parteneriat-partnership.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
