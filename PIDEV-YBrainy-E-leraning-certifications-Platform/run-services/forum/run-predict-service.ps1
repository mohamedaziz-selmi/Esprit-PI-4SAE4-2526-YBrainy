# Forum Predict Service (post quality classifier, Flask) — :5001
param([int]$Port = 5001, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-forum-predict.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
