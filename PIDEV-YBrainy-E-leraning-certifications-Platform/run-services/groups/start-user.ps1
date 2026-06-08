# ─────────────────────────────────────────────────────────────────────────────
#  start-user.ps1 — User stack
#
#  Services started:
#    1. Eureka (p-r-k)          :8761
#    2. User API Gateway        :8088
#    3. User Service            :8899
#    4. Personality Behavior    :8084
#    5. Warning-Ban-Appeal      :8086
#    6. Notes Service (FastAPI) :8087   <- overridden from 8084 to avoid conflict
#
#  NOT included (heavy AI — run separately):
#    - AI Gaze Tracking         → start-ai-gaze.ps1
#    - AI Talking Head / TTS    → start-ai-talking-head.ps1
#    - Face biometric endpoint is built into the User Service itself
#
#  Docker containers expected to already be running:
#    ✔ Keycloak   (auth)
#    ✔ RabbitMQ   (messaging / events)
#    ✔ MongoDB    (notes-service persistence)
#    ✔ MySQL      (user-service persistence)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [int]$EurekaPort       = 8761,
    [int]$GatewayPort      = 8088,
    [int]$UserPort         = 8899,
    [int]$PersonalityPort  = 8084,
    [int]$WarningBanPort   = 8086,
    [int]$NotesPort        = 8087,   # 8084 conflicts with Personality; kept at 8087
    [switch]$SkipPersonality,
    [switch]$SkipWarningBan,
    [switch]$SkipNotes,
    [switch]$SkipWait,
    [switch]$DryRun
)

$rs = "$PSScriptRoot\.."

Write-Host ""
Write-Host "=== Starting User stack ===" -ForegroundColor Cyan
Write-Host ""

# 1. Eureka
& "$rs\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun

# 2. API Gateway
& "$rs\run-user-gateway.ps1" -Port $GatewayPort -SkipWait:$SkipWait -DryRun:$DryRun

# 3. User Service
& "$rs\run-user-service.ps1" -Port $UserPort -SkipWait:$SkipWait -DryRun:$DryRun

# 4. Personality Behavior
if ($SkipPersonality) {
    Write-Host "[SKIP] Personality Behavior Service" -ForegroundColor Yellow
} else {
    & "$rs\run-personality-behavior-service.ps1" -Port $PersonalityPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 5. Warning-Ban-Appeal
if ($SkipWarningBan) {
    Write-Host "[SKIP] Warning-Ban-Appeal Service" -ForegroundColor Yellow
} else {
    & "$rs\run-warning-ban-appeal-service.ps1" -Port $WarningBanPort -SkipWait:$SkipWait -DryRun:$DryRun
}

# 6. Notes Service (port overridden to 8087 — 8084 is taken by Personality)
if ($SkipNotes) {
    Write-Host "[SKIP] Notes Service" -ForegroundColor Yellow
} else {
    & "$rs\run-notes-service.ps1" -Port $NotesPort -SkipWait:$SkipWait -DryRun:$DryRun
}

Write-Host ""
Write-Host "=== User stack launched ===" -ForegroundColor Green
Write-Host ""
Write-Host "  Eureka              http://localhost:$EurekaPort"
Write-Host "  API Gateway         http://localhost:$GatewayPort"
Write-Host "  User Service        http://localhost:$UserPort"
if (-not $SkipPersonality) { Write-Host "  Personality         http://localhost:$PersonalityPort" }
if (-not $SkipWarningBan)  { Write-Host "  Warning-Ban-Appeal  http://localhost:$WarningBanPort"  }
if (-not $SkipNotes)       { Write-Host "  Notes Service       http://localhost:$NotesPort"       }
Write-Host ""
Write-Host "  Tip: run start-ai-gaze.ps1 / start-ai-talking-head.ps1 separately when needed." -ForegroundColor DarkGray
Write-Host ""
