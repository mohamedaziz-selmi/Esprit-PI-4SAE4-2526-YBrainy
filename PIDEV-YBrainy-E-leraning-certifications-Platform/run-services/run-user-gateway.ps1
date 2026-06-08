param([int]$Port = 8088, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "User API Gateway" `
    -ProjectPath "user\p-r-k\ApiGateway\ApiGateway" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port" `
    -MavenRepoGroup "user" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
