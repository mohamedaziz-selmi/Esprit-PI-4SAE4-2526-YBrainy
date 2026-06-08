param([int]$Port = 8191, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum User Service" `
    -ProjectPath "forum\user-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_USER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
