# Personality Behavior Service — :8084
# NOTE: port 8084 also happens to be the default for Lesson and Notes services.
# The group script (groups/start-user.ps1) assigns 8084 to Personality, 8087 to Notes,
# and 8183 to Lesson so all three can coexist. If running this service standalone
# alongside those others, pass a custom -Port.
param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-personality-behavior-service.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
