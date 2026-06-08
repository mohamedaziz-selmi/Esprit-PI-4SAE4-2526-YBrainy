# launch-with-logs.ps1 — starts all services, captures output to logs\

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$logsDir = Join-Path $root "logs"
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }

function Get-Maven {
    foreach ($cmd in @("mvn.cmd", "mvn")) {
        $found = Get-Command $cmd -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.Source }
    }
    $intellij = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $intellij) { return $intellij }
    throw "Maven not found."
}

function Test-Port {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok    = $async.AsyncWaitHandle.WaitOne(800, $false)
        if ($ok) { try { $client.EndConnect($async) } catch {} }
        return $ok
    } catch { return $false }
    finally  { $client.Close() }
}

function Wait-Http {
    param([string]$Name, [string]$Url, [int]$Secs = 150)
    $deadline = (Get-Date).AddSeconds($Secs)
    Write-Host "  [Wait] $Name ..." -NoNewline
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3 -ErrorAction Stop | Out-Null
            Write-Host " UP"; return $true
        } catch {
            if ($_.Exception.Response) { Write-Host " UP"; return $true }
        }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 3
    }
    Write-Host " TIMEOUT"; return $false
}

function Start-Logged {
    param([string]$Name, [string]$Dir, [string]$Maven, [hashtable]$Env = @{})
    $absDir  = Join-Path $root $Dir
    $logFile = Join-Path $logsDir ($Name.Replace(" ", "-").Replace("/", "-") + ".log")
    "" | Set-Content -Path $logFile -Encoding UTF8

    $envSetup = ""
    foreach ($kv in $Env.GetEnumerator()) {
        $v = $kv.Value -replace "'", "''"
        $envSetup += "`$env:$($kv.Key) = '$v'; "
    }
    $escapedDir   = $absDir  -replace "'", "''"
    $escapedMaven = $Maven   -replace "'", "''"
    $escapedLog   = $logFile -replace "'", "''"
    $escapedName  = $Name    -replace "'", "''"

    $cmd = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = '$escapedName'; ${envSetup}& '$escapedMaven' spring-boot:run -DskipTests *>&1 | Tee-Object -FilePath '$escapedLog'"

    Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", $cmd -WorkingDirectory $absDir | Out-Null
    Write-Host "  [Start] $Name  ->  logs\$($Name.Replace(' ','-')).log"
    return $logFile
}

$mvn        = Get-Maven
$EUREKA_URL = "http://localhost:8761/eureka/"

Write-Host ""
Write-Host "=== Step 1: Config Server ==="
Start-Logged -Name "Config-Server" -Dir "YBRAINY\ConfigServer\config-server" -Maven $mvn
Wait-Http -Name "Config Server" -Url "http://localhost:8888/actuator/health" -Secs 120 | Out-Null

Write-Host ""
Write-Host "=== Step 2: Eureka ==="
Start-Logged -Name "Eureka" -Dir "user\p-r-k\Eureka\Eureka" -Maven $mvn -Env @{ EUREKA_PORT = "8761" }
Wait-Http -Name "Eureka" -Url "http://localhost:8761/eureka/apps" -Secs 180 | Out-Null

Write-Host ""
Write-Host "=== Step 3: Core services ==="
$coreServices = @(
    @{ Name = "User-Service";       Dir = "user";                                   Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Course-Service";     Dir = "YBRAINY\Course\tp-foyer";               Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Enrollment-Service"; Dir = "YBRAINY\Enrollment\enrollment-service"; Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Lesson-Service";     Dir = "YBRAINY\Lesson";                        Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Quiz-Service";       Dir = "YBRAINY\Quiz\quiz-service";             Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Payment-Service";    Dir = "payment\Payment";                       Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
    @{ Name = "Cart-Service";       Dir = "payment\cart";                          Env = @{ YBRAINY_EUREKA_URL = $EUREKA_URL } }
)
foreach ($s in $coreServices) {
    Start-Logged -Name $s.Name -Dir $s.Dir -Maven $mvn -Env $s.Env
}

Write-Host ""
Write-Host "=== Waiting 150s for core services to boot ==="
$ports = @{ "User-Service"=8899; "Course-Service"=8082; "Enrollment-Service"=8085; "Lesson-Service"=8084; "Quiz-Service"=8083; "Payment-Service"=8095; "Cart-Service"=8954 }
$deadline = (Get-Date).AddSeconds(150)
while ((Get-Date) -lt $deadline) {
    $allUp = $true
    foreach ($kv in $ports.GetEnumerator()) {
        if (-not (Test-Port -Port $kv.Value)) { $allUp = $false; break }
    }
    if ($allUp) { Write-Host "  All core services are up!"; break }
    Start-Sleep -Seconds 5
}

Write-Host ""
Write-Host "=== Step 4: API Gateway ==="
Start-Logged -Name "API-Gateway" -Dir "user\p-r-k\ApiGateway\ApiGateway" -Maven $mvn -Env @{ YBRAINY_EUREKA_URL = $EUREKA_URL }
Wait-Http -Name "API Gateway" -Url "http://localhost:8088/actuator/health" -Secs 180 | Out-Null

Write-Host ""
Write-Host "=== All services launched. Logs are in: $logsDir ==="
