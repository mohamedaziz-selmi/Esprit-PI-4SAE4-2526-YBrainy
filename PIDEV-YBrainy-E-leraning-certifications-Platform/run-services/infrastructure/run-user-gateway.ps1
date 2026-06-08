# User / p-r-k API Gateway — :8088
# NOTE: port 8088 conflicts with run-courses-gateway.ps1 (same binary, different MavenRepoGroup).
# Only one gateway should run on :8088 at a time; pass -Port to change if needed.
param([int]$Port = 8088, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-user-gateway.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
