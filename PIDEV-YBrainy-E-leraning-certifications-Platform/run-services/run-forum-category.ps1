param([int]$Port = 8082, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Forum Category Service" `
    -ProjectPath "forum\category-service" `
    -Kind Maven `
    -Port $Port `
    -MavenRepoGroup "forum" `
    -EnvVars @{ SERVER_PORT = "$Port"; FORUM_CATEGORY_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
