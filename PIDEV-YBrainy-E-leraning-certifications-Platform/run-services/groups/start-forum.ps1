# ─────────────────────────────────────────────────────────────────────────────
#  start-forum.ps1 — Forum stack
#
#  Services started:
#    1. Eureka                  :8761
#    2. Forum Predict (Flask)   :5001
#    3. Category Service        :8082
#    4. Thread Service          :8083
#    5. Post Service            :8084
#    6. Comment Service         :8085
#    7. Messaging Service       :8086
#
#  NOTE: The forum stack has no config server or API gateway in the source code.
#        Services connect to Eureka directly.
#
#  The forum User Service (forum/user-service) is intentionally skipped by
#  default — start-user.ps1 covers auth. Pass -StartForumUserService to
#  include it if you need to run the forum completely standalone.
#
#  Docker containers expected to already be running:
#    ✔ MongoDB    (thread / post / comment / messaging persistence)
#    ✔ Keycloak   (JWT validation)
#    ✔ RabbitMQ   (async events between forum services)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort      = 8761,
    [int]$CategoryPort    = 8082,
    [int]$ThreadPort      = 8083,
    [int]$PostPort        = 8084,
    [int]$CommentPort     = 8085,
    [int]$MessagingPort   = 8086,
    [int]$PredictPort     = 5001,
    [switch]$SkipPredict,
    [switch]$StartForumUserService,
    [int]$ForumUserPort   = 8191,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting Forum stack ===" -ForegroundColor Cyan
Write-Host ""

$env:ML_PREDICT_URL = "http://localhost:$PredictPort/predict"

# 1. Eureka
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 2. Predict (AI post-quality classifier)
if ($SkipPredict) {
    Write-Host "[SKIP] Forum Predict Service" -ForegroundColor Yellow
} else {
    & "$rs\run-forum-predict.ps1" -Port $PredictPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 3. Optional standalone forum user service
if ($StartForumUserService) {
    & "$rs\run-forum-user.ps1" -Port $ForumUserPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 4-8. Core forum microservices
& "$rs\run-forum-category.ps1"   -Port $CategoryPort  -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-forum-thread.ps1"     -Port $ThreadPort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-forum-post.ps1"       -Port $PostPort      -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-forum-comment.ps1"    -Port $CommentPort   -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-forum-messaging.ps1"  -Port $MessagingPort -SkipWait:$SkipWait -DryRun:$DryRun

Write-Host ""
Write-Host "=== Forum stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
if (-not $SkipPredict)      { Write-Host "  Predict Service     http://localhost:$PredictPort" }
if ($StartForumUserService) { Write-Host "  Forum User Service  http://localhost:$ForumUserPort" }
Write-Host "  Category Service    http://localhost:$CategoryPort"
Write-Host "  Thread Service      http://localhost:$ThreadPort"
Write-Host "  Post Service        http://localhost:$PostPort"
Write-Host "  Comment Service     http://localhost:$CommentPort"
Write-Host "  Messaging Service   http://localhost:$MessagingPort"
Write-Host ""
