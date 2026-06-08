param([int]$Port = 9333, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Parteneriat Job Offer Service" `
    -ProjectPath "Parteneriat\backend\job-offer-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "parteneriat" `
    -EnvVars @{ SERVER_PORT = "$Port"; JOB_OFFER_SERVICE_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
