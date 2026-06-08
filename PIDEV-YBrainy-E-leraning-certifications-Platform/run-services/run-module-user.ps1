param(
    [int]$EurekaPort = 8761,
    [int]$UserPort = 8899,
    [int]$GatewayPort = 8088,
    [int]$FrontendPort = 4200,
    [switch]$SkipFrontend,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-user-service.ps1" -Port $UserPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-user-gateway.ps1" -Port $GatewayPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipFrontend) {
    & "$PSScriptRoot\run-user-angular.ps1" -Port $FrontendPort -SkipWait:$SkipWait -DryRun:$DryRun
}
