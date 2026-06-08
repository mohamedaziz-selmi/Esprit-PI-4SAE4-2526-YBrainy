<#
.SYNOPSIS
    Starts the full YBrainy platform: infrastructure, user module, courses module,
    and payment module (cart, payment, finance, scraper).

.PARAMETER SkipInfra
    Skip Eureka + Config Server (assume already running).

.PARAMETER SkipMl
    Skip the Python ML service (port 5000).

.PARAMETER StatusOnly
    Print port/health status of all services and exit.
#>
param(
    [switch]$SkipInfra,
    [switch]$SkipMl,
    [switch]$StatusOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ROOT = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

# ─────────────────────────────────────────────────────────────────────────────
# Service registry  (name, port, health-url, run-script)
# ─────────────────────────────────────────────────────────────────────────────
$INFRA = @(
    @{ Name="Eureka";        Port=8761; Health="http://localhost:8761/eureka/apps";    Script="run-eureka.ps1" }
    @{ Name="Config Server"; Port=8888; Health="http://localhost:8888/actuator/health"; Script="run-ybrainy-config-server.ps1" }
)

$CORE = @(
    @{ Name="API Gateway";   Port=8088; Health="http://localhost:8088";               Script="run-user-gateway.ps1" }
    @{ Name="User Service";  Port=8899; Health="http://localhost:8899/actuator/health"; Script="run-user-service.ps1" }
)

$COURSES = @(
    @{ Name="Course Service";     Port=8082; Health="http://localhost:8082/actuator/health"; Script="run-courses-service.ps1" }
    @{ Name="Quiz Service";       Port=8083; Health="http://localhost:8083/actuator/health"; Script="run-courses-quiz.ps1" }
    @{ Name="Lesson Service";     Port=8084; Health="http://localhost:8084/actuator/health"; Script="run-ybrainy-lesson.ps1" }
    @{ Name="Enrollment Service"; Port=8085; Health="http://localhost:8085/actuator/health"; Script="run-ybrainy-enrollment.ps1" }
)

$PAYMENT = @(
    @{ Name="Cart Service";    Port=8954; Health="http://localhost:8954/actuator/health"; Script="run-payment-cart.ps1" }
    @{ Name="Payment Service"; Port=8095; Health="http://localhost:8095/actuator/health"; Script="run-payment-service.ps1" }
    @{ Name="Finance Service"; Port=8995; Health="http://localhost:8995/actuator/health"; Script="run-payment-finance.ps1" }
    @{ Name="Scraper Service"; Port=8093; Health="http://localhost:8093/actuator/health"; Script="run-payment-scraper.ps1" }
)

$ML = @(
    @{ Name="ML Service"; Port=5000; Health="http://localhost:5000/health"; Script="run-courses-ml.ps1" }
)

$ALL_SERVICES = $INFRA + $CORE + $COURSES + $PAYMENT + $ML

# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────
function Write-Header([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan
}

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "  >> $Text" -ForegroundColor Yellow
}

function Test-PortListening([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(800, $false)
        if ($ok) { $client.EndConnect($async) }
        return $ok
    } catch { return $false } finally { $client.Close() }
}

function Test-HttpReady([string]$Url) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4 -ErrorAction Stop
        return $r.StatusCode -lt 500
    } catch {
        return ($null -ne $_.Exception.Response)
    }
}

function Wait-ServiceReady([string]$Name, [string]$Url, [int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpReady $Url) {
            Write-Host "    [OK] $Name ready" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 4
    }
    Write-Host "    [TIMEOUT] $Name did not become ready within ${TimeoutSec}s" -ForegroundColor Red
    return $false
}

function Launch-Service([hashtable]$Svc) {
    if (Test-PortListening $Svc.Port) {
        Write-Host "    [SKIP] $($Svc.Name) already on port $($Svc.Port)" -ForegroundColor DarkGray
        return
    }
    $script = Join-Path $PSScriptRoot $Svc.Script
    Write-Host "    [START] $($Svc.Name) (port $($Svc.Port))..." -ForegroundColor White
    & $script -SkipWait 2>&1 | Out-Null
}

function Show-Status {
    Write-Header "Platform Status"
    $col1 = 22; $col2 = 8; $col3 = 12
    Write-Host ("  {0,-$col1} {1,-$col2} {2,-$col3}" -f "Service", "Port", "Status") -ForegroundColor Cyan
    Write-Host ("  " + ("-" * ($col1 + $col2 + $col3 + 4)))

    foreach ($svc in $ALL_SERVICES) {
        $listening = Test-PortListening $svc.Port
        $healthy   = if ($listening) { Test-HttpReady $svc.Health } else { $false }
        $status    = if ($healthy) { "UP" } elseif ($listening) { "STARTING" } else { "DOWN" }
        $color     = switch ($status) { "UP" { "Green" } "STARTING" { "Yellow" } default { "Red" } }
        Write-Host ("  {0,-$col1} {1,-$col2} " -f $svc.Name, $svc.Port) -NoNewline
        Write-Host $status -ForegroundColor $color
    }
    Write-Host ""
}

# ─────────────────────────────────────────────────────────────────────────────
# Status-only mode
# ─────────────────────────────────────────────────────────────────────────────
if ($StatusOnly) { Show-Status; exit 0 }

# ─────────────────────────────────────────────────────────────────────────────
# Banner
# ─────────────────────────────────────────────────────────────────────────────
Write-Header "YBrainy Platform — Full Startup"
Write-Host "  Root: $ROOT" -ForegroundColor DarkGray
Write-Host "  Time: $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor DarkGray

# ─────────────────────────────────────────────────────────────────────────────
# Phase 1 — Infrastructure (Eureka + Config Server)
# ─────────────────────────────────────────────────────────────────────────────
if (-not $SkipInfra) {
    Write-Step "Phase 1 — Infrastructure (Eureka + Config Server)"
    foreach ($svc in $INFRA) { Launch-Service $svc }

    Write-Host "  Waiting for infrastructure..." -ForegroundColor DarkGray
    $eurekaOk = Wait-ServiceReady "Eureka"        "http://localhost:8761/eureka/apps" 180
    $configOk = Wait-ServiceReady "Config Server" "http://localhost:8888/actuator/health" 180

    if (-not $eurekaOk) {
        Write-Host "  [WARN] Eureka is not ready — downstream services may fail to register." -ForegroundColor Red
    }
} else {
    Write-Host "  [SKIP] Infrastructure (--SkipInfra)" -ForegroundColor DarkGray
}

# ─────────────────────────────────────────────────────────────────────────────
# Phase 2 — Core (API Gateway + User Service)
# ─────────────────────────────────────────────────────────────────────────────
Write-Step "Phase 2 — Core (API Gateway + User Service)"
foreach ($svc in $CORE) { Launch-Service $svc }

# ─────────────────────────────────────────────────────────────────────────────
# Phase 3 — Course Module
# ─────────────────────────────────────────────────────────────────────────────
Write-Step "Phase 3 — Course Module (Course, Quiz, Lesson, Enrollment)"
foreach ($svc in $COURSES) { Launch-Service $svc }

# ─────────────────────────────────────────────────────────────────────────────
# Phase 4 — Payment Module
# ─────────────────────────────────────────────────────────────────────────────
Write-Step "Phase 4 — Payment Module (Cart, Payment, Finance, Scraper)"
foreach ($svc in $PAYMENT) { Launch-Service $svc }

# ─────────────────────────────────────────────────────────────────────────────
# Phase 5 — ML Service (optional Python)
# ─────────────────────────────────────────────────────────────────────────────
if (-not $SkipMl) {
    Write-Step "Phase 5 — ML Service (Python / port 5000)"
    foreach ($svc in $ML) { Launch-Service $svc }
} else {
    Write-Host "  [SKIP] ML Service (--SkipMl)" -ForegroundColor DarkGray
}

# ─────────────────────────────────────────────────────────────────────────────
# Wait for all non-infra services (up to 3 minutes)
# ─────────────────────────────────────────────────────────────────────────────
Write-Step "Waiting for all services to become ready (up to 3 min)..."
$deadline = (Get-Date).AddSeconds(180)
$toCheck  = $CORE + $COURSES + $PAYMENT
if (-not $SkipMl) { $toCheck += $ML }

$remaining = [System.Collections.Generic.List[hashtable]]::new()
foreach ($s in $toCheck) { $remaining.Add($s) }

while ($remaining.Count -gt 0 -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 6
    $stillWaiting = [System.Collections.Generic.List[hashtable]]::new()
    foreach ($s in $remaining) {
        if (Test-HttpReady $s.Health) {
            Write-Host "    [OK] $($s.Name) (port $($s.Port))" -ForegroundColor Green
        } else {
            $stillWaiting.Add($s)
        }
    }
    $remaining = $stillWaiting
}

if ($remaining.Count -gt 0) {
    Write-Host ""
    Write-Host "  [WARN] The following services have not responded yet:" -ForegroundColor Yellow
    foreach ($s in $remaining) { Write-Host "    - $($s.Name) (port $($s.Port))" -ForegroundColor Yellow }
    Write-Host "  They may still be starting. Check their terminal windows." -ForegroundColor DarkGray
}

# ─────────────────────────────────────────────────────────────────────────────
# Final status dashboard
# ─────────────────────────────────────────────────────────────────────────────
Show-Status

Write-Host "  Frontend: http://localhost:4200  (run 'ng serve' separately)" -ForegroundColor Cyan
Write-Host "  Gateway:  http://localhost:8088" -ForegroundColor Cyan
Write-Host "  Eureka:   http://localhost:8761" -ForegroundColor Cyan
Write-Host "  Swagger:  http://localhost:8088/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""
