param(
    [int]$EurekaPort = 8761,
    [int]$CartPort = 8954,
    [int]$PaymentPort = 8095,
    [int]$FinancePort = 8995,
    [int]$ScraperPort = 8093,
    [int]$FrontendPort = 4201,
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
& "$PSScriptRoot\run-payment-cart.ps1" -Port $CartPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-payment-service.ps1" -Port $PaymentPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-payment-finance.ps1" -Port $FinancePort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-payment-scraper.ps1" -Port $ScraperPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipFrontend) {
    & "$PSScriptRoot\run-payment-angular.ps1" -Port $FrontendPort -SkipWait:$SkipWait -DryRun:$DryRun
}
