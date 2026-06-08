param([int]$Port = 5173, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Parteneriat React App" `
    -ProjectPath "Parteneriat\react-ai-application" `
    -Kind Npm `
    -Port $Port `
    -WaitUrl "http://localhost:$Port" `
    -NpmScript "dev" `
    -NpmArgs @("--", "--port", "$Port") `
    -EnvVars @{ PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
