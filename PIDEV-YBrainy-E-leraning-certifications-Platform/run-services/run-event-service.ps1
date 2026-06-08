param([int]$Port = 9001, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Event Service" `
    -ProjectPath "ybrainy events/event-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "platform" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
