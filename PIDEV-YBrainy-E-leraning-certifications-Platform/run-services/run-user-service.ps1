param([int]$Port = 8899, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "User Service" `
    -ProjectPath "user" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "user" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
