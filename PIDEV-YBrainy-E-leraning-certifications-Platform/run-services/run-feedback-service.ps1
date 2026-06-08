param([int]$Port = 9004, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Feedback Service" `
    -ProjectPath "ybrainy events/feedback-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "platform" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
