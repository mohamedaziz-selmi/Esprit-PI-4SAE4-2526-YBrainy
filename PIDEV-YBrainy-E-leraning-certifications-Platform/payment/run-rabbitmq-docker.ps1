# RabbitMQ Docker Launcher
# Management UI: http://localhost:15672
# Credentials: guest / guest

param(
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Logs
)

$containerName = "rabbitmq-pidev"

function Start-RabbitMQ {
    Write-Host "Starting RabbitMQ in Docker..." -ForegroundColor Green
    
    # Check if Docker is running
    try {
        $dockerInfo = docker info 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Docker is not running. Please start Docker Desktop first."
            exit 1
        }
    } catch {
        Write-Error "Docker is not running. Please start Docker Desktop first."
        exit 1
    }
    
    # Check if container already exists
    $existingContainer = docker ps -a --filter "name=$containerName" --format "{{.Names}}"
    
    if ($existingContainer -eq $containerName) {
        Write-Host "Container '$containerName' already exists. Starting it..." -ForegroundColor Yellow
        docker start $containerName
    } else {
        Write-Host "Creating new RabbitMQ container..." -ForegroundColor Green
        docker run -d `
            --name $containerName `
            -p 5672:5672 `
            -p 15672:15672 `
            -e RABBITMQ_DEFAULT_USER=guest `
            -e RABBITMQ_DEFAULT_PASS=guest `
            rabbitmq:3.12-management
    }
    
    Write-Host ""
    Write-Host "Waiting for RabbitMQ to be ready..." -ForegroundColor Cyan
    $retries = 30
    $ready = $false
    
    for ($i = 1; $i -le $retries; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:15672" -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            Write-Host "  Attempt $i/$retries..." -ForegroundColor Gray
            Start-Sleep -Seconds 2
        }
    }
    
    if ($ready) {
        Write-Host ""
        Write-Host "RabbitMQ is ready!" -ForegroundColor Green
        Write-Host "==================================" -ForegroundColor Green
        Write-Host "Management UI: http://localhost:15672" -ForegroundColor White
        Write-Host "Username: guest" -ForegroundColor White
        Write-Host "Password: guest" -ForegroundColor White
        Write-Host "AMQP Port: 5672" -ForegroundColor White
        Write-Host "==================================" -ForegroundColor Green
    } else {
        Write-Warning "RabbitMQ may still be starting. Check logs with: docker logs $containerName"
    }
}

function Stop-RabbitMQ {
    Write-Host "Stopping RabbitMQ container..." -ForegroundColor Yellow
    docker stop $containerName 2>$null
    Write-Host "RabbitMQ stopped." -ForegroundColor Green
}

function Show-Logs {
    Write-Host "Showing RabbitMQ logs (Ctrl+C to exit)..." -ForegroundColor Cyan
    docker logs -f $containerName
}

# Main execution
if ($Logs) {
    Show-Logs
} elseif ($Stop) {
    Stop-RabbitMQ
} elseif ($Restart) {
    Stop-RabbitMQ
    Start-Sleep -Seconds 2
    Start-RabbitMQ
} else {
    Start-RabbitMQ
}
