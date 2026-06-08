# Forum Category Service — :8082
# NOTE: port 8082 conflicts with the Course Service. Do not run both stacks simultaneously
# on the same machine unless you pass a custom -Port.
param([int]$Port = 8082, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-category.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
