# Forum Comment Service — :8085
param([int]$Port = 8085, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-comment.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
