$ErrorActionPreference = "Stop"

$containerName = "ybrainy-rabbitmq"
$imageName = "rabbitmq:3-management"

$existing = docker ps -a --filter "name=$containerName" --format "{{.Names}}"
if ($existing -contains $containerName) {
    $running = docker ps --filter "name=$containerName" --format "{{.Names}}"
    if ($running -contains $containerName) {
        Write-Host "RabbitMQ is already running at http://localhost:15672"
        exit 0
    }

    docker start $containerName | Out-Null
    Write-Host "RabbitMQ container started at http://localhost:15672"
    exit 0
}

docker run -d --hostname ybrainy-rabbit --name $containerName -p 5672:5672 -p 15672:15672 $imageName | Out-Null
Write-Host "RabbitMQ container created and started."
Write-Host "AMQP: localhost:5672"
Write-Host "Management UI: http://localhost:15672"
Write-Host "Login: guest / guest"