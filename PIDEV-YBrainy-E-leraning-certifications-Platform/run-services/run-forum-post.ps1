param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Post Service" `
    -ProjectPath "forum\post-service" `
    -Kind Maven `
    -Port $Port `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_POST_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
