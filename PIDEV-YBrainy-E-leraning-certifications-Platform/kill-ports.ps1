# kill-ports.ps1 — Kill all processes using YBrainy project ports

$ports = @(
    # Infrastructure
    3306,   # MySQL
    5432,   # PostgreSQL
    27017,  # MongoDB
    5672,   # RabbitMQ AMQP
    15672,  # RabbitMQ Management UI
    9411,   # Zipkin
    8761,   # Eureka Discovery Server
    8888,   # Config Server

    # API Gateway / User
    8071,   # API Gateway (alt)
    8088,   # API Gateway
    8899,   # User Service (breadandbutteruser)

    # YBRAINY academic services
    8082,   # Course Service
    8083,   # Quiz Service
    8084,   # Lesson Service
    8085,   # Enrollment Service
    8086,   # Warning/Ban Appeal Service

    # Forum services
    8180,   # Category Service
    8181,   # Thread Service
    8182,   # Post Service
    8183,   # Comment Service
    8184,   # Messaging Service
    8185,   # Forum User Service
    8187,   # Job Offer Service
    8188,   # Personality Behavior Service
    8189,   # (reserved)

    # Events services
    9001,   # Event Service
    9002,   # Inscription Service
    9003,   # Events User Service
    9004,   # Feedback Service

    # Payment services
    8081,   # Partnership Service
    8095,   # Payment Service
    8954,   # Cart Service
    8995,   # Finance Service

    # Frontend
    4200,   # Angular dev server
    5000,   # Angular SSR / alt frontend
    5001    # Alt frontend
)

$killed = 0
$notFound = @()

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $pid = $conn.OwningProcess
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "Killing port $port -> PID $pid ($($proc.ProcessName))" -ForegroundColor Yellow
                Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
                $killed++
            }
        }
    } else {
        $notFound += $port
    }
}

Write-Host ""
Write-Host "Done. Killed $killed process(es)." -ForegroundColor Green
if ($notFound.Count -gt 0) {
    Write-Host "Not in use: $($notFound -join ', ')" -ForegroundColor DarkGray
}
