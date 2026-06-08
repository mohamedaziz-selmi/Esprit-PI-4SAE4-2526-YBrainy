# start-ybrainy.ps1
# Stops any running YBrainy services then starts them fresh.
# Assumes Keycloak (9190), RabbitMQ (5672), MongoDB (27017), Zipkin (9411)
# are already running as Docker containers.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── Find Maven ────────────────────────────────────────────────────────────────
function Get-Maven {
    foreach ($cmd in @("mvn.cmd", "mvn")) {
        $found = Get-Command $cmd -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.Source }
    }
    $intellij = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $intellij) { return $intellij }
    throw "Maven not found. Install it or add it to PATH."
}

# ── Check if a TCP port is in use ─────────────────────────────────────────────
function Test-Port {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(800, $false)
        if ($ok) { try { $client.EndConnect($async) } catch {} }
        return $ok
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# ── Kill whatever is listening on a port ─────────────────────────────────────
function Stop-OnPort {
    param([int]$Port, [string]$Name)
    $lines = netstat -ano 2>$null | Where-Object { $_ -match "[:.]$Port\s+\S+\s+LISTENING" }
    $pids = $lines | ForEach-Object {
        if ($_ -match '\s+(\d+)\s*$') { [int]$Matches[1] }
    } | Sort-Object -Unique | Where-Object { $_ -gt 0 }

    foreach ($p in $pids) {
        $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "  [Stop] $Name PID=$p ($($proc.Name)) on port $Port"
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}

# ── Wait for an HTTP endpoint to respond ──────────────────────────────────────
function Wait-Http {
    param([string]$Name, [string]$Url, [int]$Secs = 180)
    $deadline = (Get-Date).AddSeconds($Secs)
    Write-Host "  [Wait] $Name at $Url ..." -NoNewline
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3 -ErrorAction Stop | Out-Null
            Write-Host " Ready"
            return
        } catch {
            if ($_.Exception.Response) { Write-Host " Ready"; return }
        }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 3
    }
    Write-Host " TIMEOUT"
    Write-Warning "  $Name did not respond within ${Secs}s -- may still be booting."
}

# ── Launch a Spring Boot service in its own PowerShell window ─────────────────
function Start-Service {
    param(
        [string]$Name,
        [string]$Dir,
        [int]$Port,
        [string]$Maven,
        [hashtable]$Env = @{}
    )

    $absDir = Join-Path $root $Dir
    if (-not (Test-Path $absDir)) {
        Write-Warning "  [$Name] Directory not found: $absDir -- skipping."
        return
    }

    $envSetup = ""
    foreach ($kv in $Env.GetEnumerator()) {
        $v = $kv.Value -replace "'", "''"
        $envSetup += "`$env:$($kv.Key) = '$v'; "
    }

    $escapedDir   = $absDir  -replace "'", "''"
    $escapedMaven = $Maven   -replace "'", "''"
    $escapedName  = $Name    -replace "'", "''"

    $cmd = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = '$escapedName'; ${envSetup}& '$escapedMaven' spring-boot:run -DskipTests"

    Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", $cmd -WorkingDirectory $absDir | Out-Null
    Write-Host "  [Start] $Name on port $Port"
}

# ─────────────────────────────────────────────────────────────────────────────
#  Service definitions
# ─────────────────────────────────────────────────────────────────────────────
$EUREKA_URL = "http://localhost:8761/eureka/"

$services = @(
    [ordered]@{ Name = "Config Server";      Dir = "YBRAINY\ConfigServer\config-server";    Port = 8888; Env = @{} }
    [ordered]@{ Name = "Eureka";             Dir = "user\p-r-k\Eureka\Eureka";              Port = 8761; Env = @{ EUREKA_PORT = "8761" } }
    [ordered]@{ Name = "User Service";       Dir = "user";                                   Port = 8899; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Course Service";     Dir = "YBRAINY\Course\tp-foyer";               Port = 8082; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Enrollment Service"; Dir = "YBRAINY\Enrollment\enrollment-service"; Port = 8085; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Lesson Service";     Dir = "YBRAINY\Lesson";                        Port = 8084; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Quiz Service";       Dir = "YBRAINY\Quiz\quiz-service";             Port = 8083; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Payment Service";    Dir = "payment\Payment";                       Port = 8095; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "Cart Service";       Dir = "payment\cart";                          Port = 8954; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    [ordered]@{ Name = "API Gateway";        Dir = "user\p-r-k\ApiGateway\ApiGateway";      Port = 8088; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
)

# ─────────────────────────────────────────────────────────────────────────────
#  Step 1 -- Check Docker containers
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Checking Docker containers ==="
$containers = @(
    @{ Name = "RabbitMQ"; Port = 5672  }
    @{ Name = "MongoDB";  Port = 27017 }
    @{ Name = "Zipkin";   Port = 9411  }
    @{ Name = "Keycloak"; Port = 9190  }
)
# Also check MySQL
$containers += @{ Name = "MySQL"; Port = 3306 }

$missing = @()
foreach ($c in $containers) {
    if (Test-Port -Port $c.Port) {
        Write-Host "  [OK]      $($c.Name) on port $($c.Port)"
    } else {
        Write-Warning "  [MISSING] $($c.Name) not detected on port $($c.Port)"
        $missing += $c.Name
    }
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "  Missing containers: $($missing -join ', ')"
    $answer = Read-Host "  Continue anyway? (y/N)"
    if ($answer -notmatch '^[Yy]') {
        Write-Host "Aborted."
        exit 1
    }
}

# ─────────────────────────────────────────────────────────────────────────────
#  Step 2 -- Stop any services already on those ports
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Stopping existing services ==="
foreach ($svc in $services) {
    if (Test-Port -Port $svc.Port) {
        Stop-OnPort -Port $svc.Port -Name $svc.Name
    } else {
        Write-Host "  [Free]  $($svc.Name) port $($svc.Port)"
    }
}
Write-Host "  Waiting 3s for ports to free..."
Start-Sleep -Seconds 3

# ─────────────────────────────────────────────────────────────────────────────
#  Step 3 -- Find Maven
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Locating Maven ==="
$mvn = Get-Maven
Write-Host "  Maven: $mvn"

# ─────────────────────────────────────────────────────────────────────────────
#  Step 4 -- Config Server (must be first)
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Starting Config Server ==="
$svc = $services | Where-Object { $_.Name -eq "Config Server" }
Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -Maven $mvn -Env $svc.Env
Wait-Http -Name "Config Server" -Url "http://localhost:8888/actuator/health" -Secs 120

# ─────────────────────────────────────────────────────────────────────────────
#  Step 5 -- Eureka (second)
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Starting Eureka ==="
$svc = $services | Where-Object { $_.Name -eq "Eureka" }
Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -Maven $mvn -Env $svc.Env
Wait-Http -Name "Eureka" -Url "http://localhost:8761/eureka/apps" -Secs 180

# ─────────────────────────────────────────────────────────────────────────────
#  Step 6 -- Core services (all at once)
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Starting core services ==="
$coreNames = @("User Service", "Course Service", "Enrollment Service", "Lesson Service", "Quiz Service", "Payment Service", "Cart Service")
foreach ($name in $coreNames) {
    $svc = $services | Where-Object { $_.Name -eq $name }
    Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -Maven $mvn -Env $svc.Env
}

# ─────────────────────────────────────────────────────────────────────────────
#  Step 7 -- Wait for core services, then start Gateway
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Waiting for core services ==="
$healthChecks = @(
    @{ Name = "User Service";       Url = "http://localhost:8899/actuator/health" }
    @{ Name = "Course Service";     Url = "http://localhost:8082/actuator/health" }
    @{ Name = "Enrollment Service"; Url = "http://localhost:8085/actuator/health" }
    @{ Name = "Lesson Service";     Url = "http://localhost:8084/actuator/health" }
    @{ Name = "Quiz Service";       Url = "http://localhost:8083/actuator/health" }
    @{ Name = "Payment Service";    Url = "http://localhost:8095/actuator/health" }
    @{ Name = "Cart Service";       Url = "http://localhost:8954/actuator/health" }
)
foreach ($hc in $healthChecks) {
    Wait-Http -Name $hc.Name -Url $hc.Url -Secs 240
}

# ─────────────────────────────────────────────────────────────────────────────
#  Step 8 -- API Gateway (last)
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Starting API Gateway ==="
$svc = $services | Where-Object { $_.Name -eq "API Gateway" }
Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -Maven $mvn -Env $svc.Env
Wait-Http -Name "API Gateway" -Url "http://localhost:8088/actuator/health" -Secs 180

# ─────────────────────────────────────────────────────────────────────────────
#  Done
# ─────────────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=== All services launched ==="
Write-Host ""
Write-Host "  Config Server   http://localhost:8888"
Write-Host "  Eureka          http://localhost:8761"
Write-Host "  User Service    http://localhost:8899"
Write-Host "  Course Service  http://localhost:8082"
Write-Host "  Enrollment      http://localhost:8085"
Write-Host "  Lesson          http://localhost:8084"
Write-Host "  Quiz            http://localhost:8083"
Write-Host "  Payment         http://localhost:8095"
Write-Host "  Cart            http://localhost:8954"
Write-Host "  API Gateway     http://localhost:8088  <-- Angular calls this"
Write-Host ""
Write-Host "  Keycloak        http://localhost:9190"
Write-Host "  RabbitMQ UI     http://localhost:15672"
Write-Host "  Zipkin UI       http://localhost:9411"
Write-Host ""
Write-Host "  Use Ctrl+C in each service window to stop individually."
