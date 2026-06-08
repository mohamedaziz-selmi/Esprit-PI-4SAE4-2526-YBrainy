# Unified Eureka (p-r-k Eureka server) — :8761
# Used by all stacks (user, courses, forum, payment, events, parteneriat).
# Sets $env:YBRAINY_EUREKA_URL so downstream services pick it up automatically.
param([int]$Port = 8761, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-eureka.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
