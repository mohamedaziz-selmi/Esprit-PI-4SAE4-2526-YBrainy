param([int]$Port = 8083, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Thread Service" `
    -ProjectPath "forum\thread-service" `
    -Kind Maven `
    -Port $Port `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_THREAD_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
