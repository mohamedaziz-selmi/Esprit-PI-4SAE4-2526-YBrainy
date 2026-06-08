param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Notes Service (FastAPI)" `
    -ProjectPath "notes-service" `
    -Kind Python `
    -Port $Port `
    -PythonScript "start.py" `
    -MavenRepoGroup "notes" `
    -EnvVars @{} `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
