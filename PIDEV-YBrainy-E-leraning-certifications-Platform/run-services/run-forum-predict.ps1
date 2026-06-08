param([int]$Port = 5001, [switch]$SkipWait, [switch]$DryRun)

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$modelPath = Join-Path $repoRoot "forum\predict-service\ybrainy_model.pkl"

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Predict Service" `
    -ProjectPath "forum\predict-service" `
    -Kind Python `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/health" `
    -PythonScript "app.py" `
    -EnvVars @{ MODEL_PATH = "$modelPath"; PORT = "$Port"; SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
