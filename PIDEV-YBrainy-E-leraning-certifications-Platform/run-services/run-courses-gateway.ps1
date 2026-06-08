param([int]$Port = 8088, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Courses API Gateway" `
    -ProjectPath "user\p-r-k\ApiGateway\ApiGateway" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port" `
    -MavenRepoGroup "courses" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
