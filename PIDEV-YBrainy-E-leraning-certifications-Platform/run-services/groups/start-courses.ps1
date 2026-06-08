# ─────────────────────────────────────────────────────────────────────────────
#  start-courses.ps1 — Courses / Learning stack
#
#  Services started:
#    1. Eureka                  :8761
#    2. YBRAINY Config Server   :8888
#    3. Course Service          :8082
#    4. Quiz Service            :8083
#    5. Lesson Service          :8183  (not 8084 — taken by Personality in user stack)
#    6. Enrollment Service      :8085
#    7. ML Service (Flask)      :5000
#
#  The User Service + API Gateway are NOT started here.
#  The front-end (user Angular) is also NOT started here.
#  Run start-user.ps1 first (or with -SkipPersonality -SkipWarningBan -SkipNotes
#  if you only need auth) then run this script.
#
#  Docker containers expected to already be running:
#    ✔ Keycloak   (JWT validation)
#    ✔ RabbitMQ   (course / enrollment events)
#    ✔ MySQL      (course, quiz, lesson, enrollment persistence)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort      = 8761,
    [int]$ConfigPort      = 8888,
    [int]$CoursePort      = 8082,
    [int]$QuizPort        = 8083,
    [int]$LessonPort      = 8183,
    [int]$EnrollmentPort  = 8085,
    [int]$MlPort          = 5000,
    [switch]$SkipMl,
    [switch]$SkipEureka,
    [switch]$SkipConfig,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting Courses stack ===" -ForegroundColor Cyan
Write-Host ""

# 1. Eureka
if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
    Write-Host "[SKIP] Eureka (assuming already on :$EurekaPort)" -ForegroundColor Yellow
} else {
    & "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 2. Config Server (required — course services bootstrap from it)
if ($SkipConfig) {
    Write-Host "[SKIP] YBRAINY Config Server" -ForegroundColor Yellow
} else {
    & "$rs\run-ybrainy-config-server.ps1" -Port $ConfigPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 3-6. Core microservices
& "$rs\run-courses-service.ps1"    -Port $CoursePort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-courses-quiz.ps1"       -Port $QuizPort      -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-ybrainy-lesson.ps1"     -Port $LessonPort    -SkipWait:$SkipWait -DryRun:$DryRun
& "$rs\run-ybrainy-enrollment.ps1" -Port $EnrollmentPort -SkipWait:$SkipWait -DryRun:$DryRun

# 7. ML Service (course recommendations & analytics)
if ($SkipMl) {
    Write-Host "[SKIP] ML Service" -ForegroundColor Yellow
} else {
    & "$rs\run-courses-ml.ps1" -Port $MlPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== Courses stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
Write-Host "  Config Server       http://localhost:$ConfigPort"
Write-Host "  Course Service      http://localhost:$CoursePort"
Write-Host "  Quiz Service        http://localhost:$QuizPort"
Write-Host "  Lesson Service      http://localhost:$LessonPort"
Write-Host "  Enrollment Service  http://localhost:$EnrollmentPort"
if (-not $SkipMl) { Write-Host "  ML Service          http://localhost:$MlPort" }
Write-Host ""
