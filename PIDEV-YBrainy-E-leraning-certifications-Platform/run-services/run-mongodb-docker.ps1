# MongoDB Docker Launcher (for personality-behavior-service and any Mongo-backed services)
# Connection: mongodb://localhost:27017

param(
    [string]$ContainerName = "ybrainy-mongodb",
    [int]$Port = 27017,
    [string]$Image = "mongo:7",
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Logs
)

$ErrorActionPreference = "Stop"

function Start-Mongo {
    Write-Host "Starting MongoDB in Docker..." -ForegroundColor Green

    try {
        docker info 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Docker not running" }
    } catch {
        throw "Docker is not running. Start Docker Desktop first."
    }

    $existing = docker ps -a --filter "name=$ContainerName" --format "{{.Names}}"
    if ($existing -contains $ContainerName) {
        $running = docker ps --filter "name=$ContainerName" --format "{{.Names}}"
        if ($running -contains $ContainerName) {
            Write-Host "MongoDB is already running on localhost:$Port" -ForegroundColor Yellow
            return
        }
        docker start $ContainerName | Out-Null
        Write-Host "MongoDB container started." -ForegroundColor Green
        return
    }

    docker run -d `
        --name $ContainerName `
        -p "$Port`:27017" `
        $Image | Out-Null

    Write-Host "MongoDB container created and started." -ForegroundColor Green
    Write-Host "MongoDB: localhost:$Port" -ForegroundColor White
}

function Stop-Mongo {
    Write-Host "Stopping MongoDB container..." -ForegroundColor Yellow
    docker stop $ContainerName 2>$null | Out-Null
    Write-Host "MongoDB stopped." -ForegroundColor Green
}

function Show-Logs {
    docker logs -f $ContainerName
}

if ($Logs) {
    Show-Logs
} elseif ($Stop) {
    Stop-Mongo
} elseif ($Restart) {
    Stop-Mongo
    Start-Sleep -Seconds 2
    Start-Mongo
} else {
    Start-Mongo
}

