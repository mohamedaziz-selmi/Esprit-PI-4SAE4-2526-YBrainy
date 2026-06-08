# Quiz Service — :8083
param([int]$Port = 8083, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-courses-quiz.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
