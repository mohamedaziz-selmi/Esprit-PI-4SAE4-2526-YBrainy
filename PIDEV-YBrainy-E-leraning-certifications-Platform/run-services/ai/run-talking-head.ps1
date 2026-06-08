# AI Talking Head / Voice Cloning (Coqui TTS, Python) — :8765
# Requires the ybrainy-tts conda environment (Python <= 3.11).
# Heavy model — start only when needed.
# Optional flags: -SpeakerWav <path>   clone a specific voice from a .wav file
#                 -Cpu                  force CPU inference (slower, no GPU required)
param(
    [int]$Port          = 8765,
    [string]$SpeakerWav = "",
    [switch]$Cpu,
    [switch]$SkipWait,
    [switch]$DryRun
)

$splat = @{ Port = $Port; SkipWait = $SkipWait; DryRun = $DryRun }
if ($SpeakerWav) { $splat['SpeakerWav'] = $SpeakerWav }
if ($Cpu)        { $splat['Cpu']        = $true        }

& "$PSScriptRoot\..\run-ai-talking-head.ps1" @splat
