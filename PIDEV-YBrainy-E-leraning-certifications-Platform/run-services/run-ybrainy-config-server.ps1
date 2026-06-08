param([int]$Port = 8888, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\_run-service.ps1" `
    -Name "YBRAINY Config Server" `
    -ProjectPath "YBRAINY\ConfigServer\config-server" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "ybrainy" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -EurekaUrl "" `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun

