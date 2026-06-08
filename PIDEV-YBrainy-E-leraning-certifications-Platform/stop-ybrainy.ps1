# stop-ybrainy.ps1
# Kills all YBrainy services started by start-ybrainy.ps1

$ports = @(8888, 8761, 8899, 8082, 8085, 8084, 8083, 8095, 8954, 8088)
$names = @{
    8888 = "Config Server"
    8761 = "Eureka"
    8899 = "User Service"
    8082 = "Course Service"
    8085 = "Enrollment Service"
    8084 = "Lesson Service"
    8083 = "Quiz Service"
    8095 = "Payment Service"
    8954 = "Cart Service"
    8088 = "API Gateway"
}

Write-Host ""
Write-Host "=== Stopping YBrainy services ==="

foreach ($port in $ports) {
    $lines = netstat -ano 2>$null | Where-Object { $_ -match "[:.]$port\s+\S+\s+LISTENING" }
    $pids = $lines | ForEach-Object {
        if ($_ -match '\s+(\d+)\s*$') { [int]$Matches[1] }
    } | Sort-Object -Unique | Where-Object { $_ -gt 0 }

    if ($pids.Count -eq 0) {
        Write-Host "  [Free]    $($names[$port]) port $port"
        continue
    }

    foreach ($p in $pids) {
        $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "  [Kill]    $($names[$port]) PID=$p ($($proc.Name)) on port $port"
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}

# Also kill the start-ybrainy.ps1 process itself if still running
$scriptProcs = Get-WmiObject Win32_Process | Where-Object {
    $_.CommandLine -like "*start-ybrainy.ps1*"
}
foreach ($p in $scriptProcs) {
    Write-Host "  [Kill]    start-ybrainy.ps1 (PID=$($p.ProcessId))"
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=== Done. All YBrainy service ports cleared. ==="
Write-Host ""
