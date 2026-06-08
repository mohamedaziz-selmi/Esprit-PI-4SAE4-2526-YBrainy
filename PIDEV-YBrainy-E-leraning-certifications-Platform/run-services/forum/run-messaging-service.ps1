# Forum Messaging Service — :8086
param([int]$Port = 8086, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-messaging.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
