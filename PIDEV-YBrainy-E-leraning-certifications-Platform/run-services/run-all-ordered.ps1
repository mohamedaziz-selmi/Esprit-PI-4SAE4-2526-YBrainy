param(
    [int]$EurekaPort = 8761,
    [int]$GatewayPort = 8088,
    [switch]$SkipRabbitMq,
    [switch]$SkipMongoDb,
    [switch]$SkipConfigServer,
    [switch]$SkipPredict,
    [switch]$SkipGateway,
    [switch]$SkipWait,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

Write-Host "=== Group 1: Infrastructure ===" -ForegroundColor Cyan
& "$PSScriptRoot\run-prk-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipRabbitMq) {
    Write-Host "Starting RabbitMQ (Docker)..." -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host "[DryRun] Would run payment\run-rabbitmq-docker.ps1"
    } else {
        try {
            & (Join-Path $repoRoot "payment\run-rabbitmq-docker.ps1")
        } catch {
            Write-Warning "RabbitMQ failed to start: $_"
            Write-Warning "Make sure Docker Desktop is running. Continuing without RabbitMQ..."
        }
    }
} else {
    Write-Host "Skipping RabbitMQ."
}

if (-not $SkipMongoDb) {
    Write-Host "Starting MongoDB (Docker)..." -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host "[DryRun] Would run run-services\run-mongodb-docker.ps1"
    } else {
        try {
            & "$PSScriptRoot\run-mongodb-docker.ps1"
        } catch {
            Write-Warning "MongoDB failed to start: $_"
            Write-Warning "Make sure Docker Desktop is running. Continuing without MongoDB..."
        }
    }
} else {
    Write-Host "Skipping MongoDB."
}

Write-Host "=== Group 2: Config Server ===" -ForegroundColor Cyan
if (-not $SkipConfigServer) {
    & "$PSScriptRoot\run-ybrainy-config-server.ps1" -Port 8888 -SkipWait:$SkipWait -DryRun:$DryRun
} else {
    Write-Host "Skipping Config Server."
}

Write-Host "=== Group 3: Core user service ===" -ForegroundColor Cyan
& "$PSScriptRoot\run-user-service.ps1" -Port 8899 -SkipWait:$SkipWait -DryRun:$DryRun

Write-Host "=== Group 4: Domain services ===" -ForegroundColor Cyan

# Payment
& "$PSScriptRoot\run-payment-service.ps1"      -Port 8095 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-payment-cart.ps1"         -Port 8954 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-payment-finance.ps1"      -Port 8995 -SkipWait:$SkipWait -DryRun:$DryRun

# YBRAINY core (Course, Quiz, Lesson, Enrollment)
& "$PSScriptRoot\run-courses-service.ps1"      -Port 8082 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-courses-quiz.ps1"         -Port 8083 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-ybrainy-lesson.ps1"       -Port 8084 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-ybrainy-enrollment.ps1"   -Port 8085 -SkipWait:$SkipWait -DryRun:$DryRun

# Notes Service: run manually → cd notes-service && uvicorn main:app --host 0.0.0.0 --port 8084 --reload

# Forum
& "$PSScriptRoot\run-forum-category.ps1"       -Port 8180 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-thread.ps1"         -Port 8181 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-post.ps1"           -Port 8182 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-comment.ps1"        -Port 8183 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-messaging.ps1"      -Port 8184 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-user.ps1"           -Port 8185 -SkipWait:$SkipWait -DryRun:$DryRun

# Partnership / Jobs
& "$PSScriptRoot\run-new-parteneriat-partnership.ps1" -Port 8081 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-new-parteneriat-job-offer.ps1"   -Port 8187 -SkipWait:$SkipWait -DryRun:$DryRun

# Personality / Warning
& "$PSScriptRoot\run-personality-behavior-service.ps1" -Port 8188 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-warning-ban-appeal-service.ps1"   -Port 8189 -SkipWait:$SkipWait -DryRun:$DryRun

# Events (9001-9004)
& "$PSScriptRoot\run-event-service.ps1"        -Port 9001 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-inscription-service.ps1"  -Port 9002 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-events-user-service.ps1"  -Port 9003 -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-feedback-service.ps1"     -Port 9004 -SkipWait:$SkipWait -DryRun:$DryRun

# Forum AI predict (Python/Flask) — run manually if needed: cd forum\predict-service && python app.py

Write-Host "=== Group 5: Gateway ===" -ForegroundColor Cyan
if (-not $SkipGateway) {
    & "$PSScriptRoot\run-user-gateway.ps1" -Port $GatewayPort -SkipWait:$SkipWait -DryRun:$DryRun
} else {
    Write-Host "Skipping API Gateway."
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host " All services launched!" -ForegroundColor Green
Write-Host " Eureka:    http://localhost:$EurekaPort" -ForegroundColor White
Write-Host " Gateway:   http://localhost:$GatewayPort" -ForegroundColor White
Write-Host " RabbitMQ:  http://localhost:15672  (guest/guest)" -ForegroundColor White
Write-Host " Swagger:   http://localhost:$GatewayPort/swagger-ui.html" -ForegroundColor White
Write-Host "======================================" -ForegroundColor Green
