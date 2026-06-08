param([int]$Port = 8761, [switch]$SkipWait, [switch]$DryRun)

$env:YBRAINY_EUREKA_URL = "http://localhost:$Port/eureka/"

& "$PSScriptRoot\_run-service.ps1" `
    -Name "Unified Eureka" `
    -ProjectPath "user\p-r-k\Eureka\Eureka" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/eureka/apps" `
    -MavenRepoGroup "user" `
    -EnvVars @{ SERVER_PORT = "$Port" } `
    -EurekaUrl "" `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun
