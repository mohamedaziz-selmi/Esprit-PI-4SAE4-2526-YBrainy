# ─────────────────────────────────────────────────────────────────────────────
#  start-events.ps1 — Events stack
#
#  Services started:
#    1. Eureka                  :8761
#    2. Event Service           :9001
#    3. Inscription Service     :9002
#    4. Events-User Service     :9003
#    5. Feedback Service        :9004  (optional, pass -SkipFeedback to skip)
#
#  Docker containers expected to already be running:
#    ✔ MySQL      (event / inscription / feedback persistence)
#    ✔ Keycloak   (JWT validation)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort       = 8761,
    [int]$EventPort        = 9001,
    [int]$InscriptionPort  = 9002,
    [int]$EventsUserPort   = 9003,
    [int]$FeedbackPort     = 9004,
    [switch]$SkipFeedback,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting Events stack ===" -ForegroundColor Cyan
Write-Host ""

# 1. Eureka
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 2-4. Core events microservices
& "$rs\run-event-service.ps1"       -Port $EventPort       -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-inscription-service.ps1" -Port $InscriptionPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-events-user-service.ps1" -Port $EventsUserPort  -SkipWait:$SkipWait -DryRun:$DryRun

# 5. Feedback Service
if ($SkipFeedback) {
    Write-Host "[SKIP] Feedback Service" -ForegroundColor Yellow
} else {
    & "$rs\run-feedback-service.ps1" -Port $FeedbackPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== Events stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
Write-Host "  Event Service       http://localhost:$EventPort"
Write-Host "  Inscription Service http://localhost:$InscriptionPort"
Write-Host "  Events-User Service http://localhost:$EventsUserPort"
if (-not $SkipFeedback) { Write-Host "  Feedback Service    http://localhost:$FeedbackPort" }
Write-Host ""
