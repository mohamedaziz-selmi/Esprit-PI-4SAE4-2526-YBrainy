param(
    # Ports
    [int]$EurekaPort       = 8761,
    [int]$ConfigPort       = 8888,
    [int]$UserPort         = 8899,
    [int]$UserGatewayPort  = 8088,
    [int]$CoursePort       = 8082,
    [int]$QuizPort         = 8083,
    [int]$LessonPort       = 8183,   # 8084 is taken by personality-behavior; use 8183
    [int]$EnrollmentPort   = 8085,
    [int]$MlPort           = 5000,
    [int]$FrontendPort     = 4200,

    # Skip flags
    [switch]$SkipFrontend,
    [switch]$SkipMl,
    [switch]$SkipConfig,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

Write-Host ""
Write-Host "=== Starting User + Courses stack ===" -ForegroundColor Cyan
Write-Host ""

# ── Eureka (shared) ──────────────────────────────────────────────────────────
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# ── YBRAINY Config Server ─────────────────────────────────────────────────────
if ($SkipConfig) {
    Write-Host "[SKIP] Config Server" -ForegroundColor Yellow
} else {
    & "$PSScriptRoot\run-ybrainy-config-server.ps1" -Port $ConfigPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# ── User service + gateway ────────────────────────────────────────────────────
& "$PSScriptRoot\run-user-service.ps1"  -Port $UserPort        -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-user-gateway.ps1"  -Port $UserGatewayPort -SkipWait:$SkipWait -DryRun:$DryRun

# ── YBRAINY Course microservices ──────────────────────────────────────────────
& "$PSScriptRoot\run-courses-service.ps1"   -Port $CoursePort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-courses-quiz.ps1"      -Port $QuizPort      -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-ybrainy-lesson.ps1"    -Port $LessonPort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-ybrainy-enrollment.ps1" -Port $EnrollmentPort -SkipWait:$SkipWait -DryRun:$DryRun

# ── ML service ────────────────────────────────────────────────────────────────
if ($SkipMl) {
    Write-Host "[SKIP] ML service" -ForegroundColor Yellow
} else {
    & "$PSScriptRoot\run-courses-ml.ps1" -Port $MlPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# ── Angular frontend ──────────────────────────────────────────────────────────
if ($SkipFrontend) {
    Write-Host "[SKIP] Angular frontend" -ForegroundColor Yellow
} else {
    & "$PSScriptRoot\run-user-angular.ps1" -Port $FrontendPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== All services launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka           http://localhost:$EurekaPort"
Write-Host "  Config Server    http://localhost:$ConfigPort"
Write-Host "  User Service     http://localhost:$UserPort"
Write-Host "  User Gateway     http://localhost:$UserGatewayPort"
Write-Host "  Course Service   http://localhost:$CoursePort"
Write-Host "  Quiz Service     http://localhost:$QuizPort"
Write-Host "  Lesson Service   http://localhost:$LessonPort"
Write-Host "  Enrollment       http://localhost:$EnrollmentPort"
if (-not $SkipMl)       { Write-Host "  ML Service       http://localhost:$MlPort" }
if (-not $SkipFrontend) { Write-Host "  Angular          http://localhost:$FrontendPort" }
Write-Host ""
