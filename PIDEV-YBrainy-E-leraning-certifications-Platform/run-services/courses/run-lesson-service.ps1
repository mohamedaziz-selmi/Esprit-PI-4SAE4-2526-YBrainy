# Lesson Service — default :8084
# NOTE: port 8084 conflicts with Personality Behavior when running alongside the user stack.
# The group script (groups/start-courses.ps1) automatically uses :8183 to avoid the conflict.
# If you're starting this standalone while the user stack is also running, pass -Port 8183.
param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-ybrainy-lesson.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
