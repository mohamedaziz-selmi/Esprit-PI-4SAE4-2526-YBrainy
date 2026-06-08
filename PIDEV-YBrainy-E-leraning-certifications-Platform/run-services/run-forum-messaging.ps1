param([int]$Port = 8086, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Messaging Service" `
    -ProjectPath "forum\messaging-service" `
    -Kind Maven `
    -Port $Port `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_MESSAGING_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
