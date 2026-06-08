param([int]$Port = 9010, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Events ML Service (FastAPI)" `
    -ProjectPath "ybrainy events/ml-events-service" `
    -Kind Python `
    -Port $Port `
    -PythonScript "start.py" `
    -MavenRepoGroup "events-ml" `
    -EnvVars @{ SERVICE_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
