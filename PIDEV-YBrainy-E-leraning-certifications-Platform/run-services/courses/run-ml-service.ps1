# ML Service (course recommendations & analytics, Flask) — :5000
param([int]$Port = 5000, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-courses-ml.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
