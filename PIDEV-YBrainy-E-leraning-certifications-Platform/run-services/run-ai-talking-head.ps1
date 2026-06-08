param(
    [int]$Port = 8765,
    [string]$SpeakerWav = "",
    [switch]$Cpu,
    [switch]$SkipWait,
    [switch]$DryRun
)

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$fairFaceRoot = (Resolve-Path -LiteralPath (Join-Path $repoRoot "user\ai-talking-head-package")).Path

$cpuFlag = if ($Cpu) { " --cpu" } else { "" }
$speakerFlag = ""
if ($SpeakerWav -and (Test-Path -LiteralPath $SpeakerWav)) {
    $speakerFlag = " --speaker-wav `"$SpeakerWav`""
}

$command = @"
`$env:COQUI_TOS_AGREED = '1'
# Try the ybrainy-tts conda env first (required for Python <= 3.11 / TTS package)
`$condaBase = & conda info --base 2>`$null
`$condaPython = if (`$condaBase) { Join-Path `$condaBase.Trim() 'envs\ybrainy-tts\python.exe' } else { `$null }
if (`$condaPython -and (Test-Path -LiteralPath `$condaPython)) {
    `$python = `$condaPython
} else {
    `$python = Get-Command python -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
    if (-not `$python) { `$python = Get-Command py -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1 }
    if (-not `$python) { throw 'Python was not found in PATH.' }
}
Write-Host "Using Python: `$python"
& `$python talking_head_tts_server.py --port $Port --speech-root speechclonning --tts-python `$python$speakerFlag$cpuFlag
"@

& "$PSScriptRoot\_run-service.ps1" `
    -Name "AI Talking Head TTS" `
    -ProjectPath $fairFaceRoot `
    -Kind Command `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/" `
    -Command $command `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
