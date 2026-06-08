param([int]$Port = 4200, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Payment Angular App" `
    -ProjectPath "payment\angular-app" `
    -Kind Npm `
    -Port $Port `
    -WaitUrl "http://localhost:$Port" `
    -NpmScript "start" `
    -NpmArgs @("--", "--port", "$Port") `
    -EnvVars @{ PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
