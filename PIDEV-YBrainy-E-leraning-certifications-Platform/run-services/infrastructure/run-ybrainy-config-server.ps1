# YBRAINY Config Server (courses/enrollment/lesson/quiz stack) — :8888
param([int]$Port = 8888, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-ybrainy-config-server.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
