# Forum Post Service — :8084
param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-post.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
