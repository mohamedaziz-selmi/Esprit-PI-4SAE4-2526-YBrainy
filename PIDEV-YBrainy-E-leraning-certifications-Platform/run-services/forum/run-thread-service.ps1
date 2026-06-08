# Forum Thread Service — :8083
param([int]$Port = 8083, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-thread.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
