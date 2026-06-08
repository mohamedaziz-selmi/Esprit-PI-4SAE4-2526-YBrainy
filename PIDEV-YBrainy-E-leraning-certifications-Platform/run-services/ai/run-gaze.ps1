# AI Gaze Tracking (eye tracking / face detection, Python) — :5002
# Requires the ybrainy-gaze conda environment (numpy < 2 pinned for mediapipe).
# Heavy model — start only when needed.
param([int]$Port = 5002, [switch]$SkipWait, [switch]$DryRun)
& "$PSScriptRoot\..\run-ai-gaze.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
