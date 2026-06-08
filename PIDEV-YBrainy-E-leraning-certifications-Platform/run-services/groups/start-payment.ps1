# ─────────────────────────────────────────────────────────────────────────────
#  start-payment.ps1 — Payment stack
#
#  Services started:
#    1. Eureka                  :8761
#    2. Cart Service            :8954
#    3. Payment Service         :8095
#    4. Finance Service         :8995
#    5. Scraper Service         :8093
#    6. Payment Angular         :4201  (optional, pass -SkipFrontend to skip)
#
#  NOTE: The payment stack has no API gateway in the source code.
#        Services are accessed directly by port.
#
#  Docker containers expected to already be running:
#    ✔ Keycloak   (JWT validation)
#    ✔ RabbitMQ   (payment / cart events)
#    ✔ MySQL      (cart, payment, finance persistence)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort    = 8761,
    [int]$CartPort      = 8954,
    [int]$PaymentPort   = 8095,
    [int]$FinancePort   = 8995,
    [int]$ScraperPort   = 8093,
    [int]$FrontendPort  = 4201,
    [switch]$SkipFrontend,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting Payment stack ===" -ForegroundColor Cyan
Write-Host ""

# 1. Eureka
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 2-5. Payment microservices
& "$rs\run-payment-cart.ps1"    -Port $CartPort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-payment-service.ps1" -Port $PaymentPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-payment-finance.ps1" -Port $FinancePort -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-payment-scraper.ps1" -Port $ScraperPort -SkipWait:$SkipWait -DryRun:$DryRun

# 6. Angular frontend
if ($SkipFrontend) {
    Write-Host "[SKIP] Payment Angular frontend" -ForegroundColor Yellow
} else {
    & "$rs\run-payment-angular.ps1" -Port $FrontendPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== Payment stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
Write-Host "  Cart Service        http://localhost:$CartPort"
Write-Host "  Payment Service     http://localhost:$PaymentPort"
Write-Host "  Finance Service     http://localhost:$FinancePort"
Write-Host "  Scraper Service     http://localhost:$ScraperPort"
if (-not $SkipFrontend) { Write-Host "  Angular Frontend    http://localhost:$FrontendPort" }
Write-Host ""
