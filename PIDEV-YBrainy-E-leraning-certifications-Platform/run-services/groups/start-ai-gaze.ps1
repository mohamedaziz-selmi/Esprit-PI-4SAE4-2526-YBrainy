# ─────────────────────────────────────────────────────────────────────────────
#  start-ai-gaze.ps1 — AI Gaze Tracking (eye tracking / face detection)
#
#  Starts the Python gaze-tracking service.
#  This is a heavy AI model — run it only when you specifically need it.
#  It uses MediaPipe, which requires the ybrainy-gaze conda environment
#  (numpy < 2 pinned). The script will auto-detect that env via conda.
#
#  Default port: 5002
#  Health check:  http://localhost:5002/health
#
#  No Docker containers required for this service alone.
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$Port = 5002,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting AI Gaze Tracking ===" -ForegroundColor Cyan
Write-Host ""

& "$rs\run-ai-gaze.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun

Write-Host ""
Write-Host "=== AI Gaze Tracking launched ===" -ForegroundColor Green
Write-Host "  Gaze Service  http://localhost:$Port"
Write-Host "  Health check  http://localhost:$Port/health"
Write-Host ""
