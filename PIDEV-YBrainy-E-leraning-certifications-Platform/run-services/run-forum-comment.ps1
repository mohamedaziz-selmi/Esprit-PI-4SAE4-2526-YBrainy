param([int]$Port = 8085, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Comment Service" `
    -ProjectPath "forum\comment-service" `
    -Kind Maven `
    -Port $Port `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_COMMENT_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
