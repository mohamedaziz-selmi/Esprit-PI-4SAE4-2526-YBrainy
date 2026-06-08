param([int]$Port = 8995, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Payment Finance Service" `
    -ProjectPath "payment\finance" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "payment" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
