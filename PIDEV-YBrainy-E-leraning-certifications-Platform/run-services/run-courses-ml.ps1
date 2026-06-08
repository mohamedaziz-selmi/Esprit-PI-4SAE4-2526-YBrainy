param([int]$Port = 5000, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Courses ML Service" `
    -ProjectPath "YBRAINY\ML-Service" `
    -Kind Python `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/health" `
    -PythonScript "app.py" `
    -EnvVars @{ PYTHONIOENCODING = "utf-8"; PYTHONUTF8 = "1" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
