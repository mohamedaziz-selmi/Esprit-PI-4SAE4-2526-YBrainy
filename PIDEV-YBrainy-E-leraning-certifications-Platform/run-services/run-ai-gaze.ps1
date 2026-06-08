param(
    [int]$Port = 5002,
    [switch]$SkipWait,
    [switch]$DryRun
)

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$gazeRoot = (Resolve-Path -LiteralPath (Join-Path $repoRoot "user\ai-gaze-package")).Path

$command = @"
# Try the ybrainy-gaze conda env first (numpy<2 pinned, required for mediapipe)
`$condaBase = & conda info --base 2>`$null
`$condaPython = if (`$condaBase) { Join-Path `$condaBase.Trim() 'envs\ybrainy-gaze\python.exe' } else { `$null }
if (`$condaPython -and (Test-Path -LiteralPath `$condaPython)) {
    `$python = `$condaPython
} else {
    `$python = Get-Command python -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
    if (-not `$python) { `$python = Get-Command py -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1 }
    if (-not `$python) { throw 'Python was not found in PATH.' }
}
Write-Host "Using Python: `$python"
& `$python eye_tracking_service.py --port $Port
"@

& "$PSScriptRoot\_run-service.ps1" `
    -Name "AI Gaze Tracking" `
    -ProjectPath $gazeRoot `
    -Kind Command `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/health" `
    -Command $command `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
