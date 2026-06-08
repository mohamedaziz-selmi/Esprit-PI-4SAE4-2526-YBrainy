# ─────────────────────────────────────────────────────────────────────────────
#  start-ai-talking-head.ps1 — AI Talking Head / Voice Cloning (TTS)
#
#  Starts the Python TTS + animated talking-head service.
#  This is a heavy AI model (Coqui TTS) — run it only when you specifically
#  need it. It uses the ybrainy-tts conda environment (Python ≤ 3.11).
#  The script will auto-detect that env via conda.
#
#  Default port: 8765
#  Health check:  http://localhost:8765/
#
#  Options:
#    -SpeakerWav  path to a .wav file to clone a specific voice
#    -Cpu         force CPU inference (slower but no GPU required)
#
#  No Docker containers required for this service alone.
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$Port          = 8765,
    [string]$SpeakerWav = "",
    [switch]$Cpu,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting AI Talking Head / Voice Cloning ===" -ForegroundColor Cyan
Write-Host ""

$splat = @{
    Port      = $Port
    SkipWait  = $SkipWait
    DryRun    = $DryRun
}
if ($SpeakerWav) { $splat['SpeakerWav'] = $SpeakerWav }
if ($Cpu)        { $splat['Cpu']        = $true        }

& "$rs\run-ai-talking-head.ps1" @splat

Write-Host ""
Write-Host "=== AI Talking Head launched ===" -ForegroundColor Green
Write-Host "  TTS Service   http://localhost:$Port"
if ($Cpu) { Write-Host "  Mode          CPU (no GPU)" -ForegroundColor Yellow }
Write-Host ""
