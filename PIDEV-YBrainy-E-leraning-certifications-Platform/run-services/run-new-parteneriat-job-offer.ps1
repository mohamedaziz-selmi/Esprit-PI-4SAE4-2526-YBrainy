param([int]$Port = 8187, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "New Parteneriat Job Offer Service" `
    -ProjectPath "new parteneriat\backend\job-offer-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "parteneriat" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun

