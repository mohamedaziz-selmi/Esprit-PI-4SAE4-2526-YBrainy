# ─────────────────────────────────────────────────────────────────────────────
#  start-parteneriat.ps1 — Partnership stack
#
#  Services started:
#    1. Eureka                  :8761  (unified — also used by all other stacks)
#    2. Partnership Service     :8181
#    3. Job Offer Service       :8182
#    4. React Frontend          :5173  (optional, pass -SkipFrontend to skip)
#
#  API Gateway: use the unified gateway at :8088 (started by start-user.ps1).
#  Routes /api/partnerships/**, /api/offers/**, /api/applications/** are
#  already wired in user/p-r-k/ApiGateway/ApiGateway/application.properties.
#
#  Docker containers expected to already be running:
#    ✔ MySQL      (partnership / job-offer persistence)
#    ✔ Keycloak   (JWT validation)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort       = 8761,
    [int]$PartnershipPort  = 8181,
    [int]$JobOfferPort     = 8182,
    [int]$ReactPort        = 5173,
    [switch]$SkipFrontend,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting Parteneriat stack ===" -ForegroundColor Cyan
Write-Host ""

# 1. Eureka
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 2-3. Core microservices
& "$rs\run-new-parteneriat-partnership.ps1" -Port $PartnershipPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-new-parteneriat-job-offer.ps1"   -Port $JobOfferPort    -SkipWait:$SkipWait -DryRun:$DryRun

# 4. React frontend
if ($SkipFrontend) {
    Write-Host "[SKIP] Parteneriat React frontend" -ForegroundColor Yellow
} else {
    & "$rs\run-parteneriat-react.ps1" -Port $ReactPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== Parteneriat stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
Write-Host "  Partnership Service http://localhost:$PartnershipPort"
Write-Host "  Job Offer Service   http://localhost:$JobOfferPort"
Write-Host "  API Gateway         http://localhost:8088  (unified — start-user.ps1)"
if (-not $SkipFrontend) { Write-Host "  React Frontend      http://localhost:$ReactPort" }
Write-Host ""
