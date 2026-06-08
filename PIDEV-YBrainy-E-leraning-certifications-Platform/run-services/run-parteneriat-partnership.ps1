param([int]$Port = 9334, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Parteneriat Partnership Service" `
    -ProjectPath "Parteneriat\backend\partnership-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "parteneriat" `
    -EnvVars @{ SERVER_PORT = "$Port"; PARTNERSHIP_SERVICE_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
