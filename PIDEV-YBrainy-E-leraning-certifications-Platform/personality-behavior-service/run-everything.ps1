param(
    [switch]$NoNewWindows,
    [switch]$SkipEureka
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$parentRoot = Split-Path -Parent $root

function Start-Module {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDir,
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    Write-Host "[$Name] $WorkingDir"
    Write-Host "[$Name] $Command"

    if ($NoNewWindows) {
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-NoExit", "-Command", "Set-Location -LiteralPath '$WorkingDir'; $Command" `
            -WorkingDirectory $WorkingDir
    } else {
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-NoExit", "-Command", "Set-Location -LiteralPath '$WorkingDir'; `$host.UI.RawUI.WindowTitle = '$Name'; $Command" `
            -WorkingDirectory $WorkingDir
    }
}

function Test-HttpReachable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    try {
        Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3 | Out-Null
        return $true
    } catch {
        if ($_.Exception.Response) {
            return $true
        }
        return $false
    }
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpReachable -Url $Url) {
            Write-Host "[$Name] Ready at $Url"
            return
        }
        Start-Sleep -Seconds 2
    }

    Write-Warning "[$Name] Timed out waiting for $Url. Startup may still be in progress."
}

function Wait-EurekaRegistration {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AppName,
        [string]$EurekaUrl = "http://localhost:8071/eureka/apps",
        [int]$TimeoutSeconds = 120
    )

    $target = $AppName.ToUpperInvariant()
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $registry = Invoke-RestMethod -Headers @{ Accept = "application/json" } -Uri $EurekaUrl -TimeoutSec 5
            $apps = @($registry.applications.application)

            foreach ($app in $apps) {
                if (-not $app) { continue }
                $name = [string]$app.name
                if ($name.ToUpperInvariant() -ne $target) { continue }

                $instances = @($app.instance)
                if ($instances.Count -gt 0) {
                    $up = @($instances | Where-Object { [string]$_.status -eq "UP" })
                    if ($up.Count -gt 0) {
                        Write-Host "[Eureka] $target registered (UP)"
                        return
                    }
                }
            }
        } catch {
            # Eureka may still be starting.
        }

        Start-Sleep -Seconds 2
    }

    Write-Warning "[Eureka] Timed out waiting for $target registration."
}

# Locate Eureka
$eurekaDir = Join-Path $parentRoot "p-r-k\Eureka"
if (-not (Test-Path $eurekaDir -PathType Container)) {
    $eurekaDir = Join-Path $parentRoot "p-r-k\Eureka\Eureka"
}

Write-Host "Root: $root"
Write-Host "Parent: $parentRoot"
Write-Host ""
Write-Host "Notes:"
Write-Host " - Ensure MySQL is running."
Write-Host " - Personality-Behavior Service requires:"
Write-Host "   - Port 8084 available"
Write-Host "   - Eureka at port 8071"
Write-Host ""

# Start Eureka if not skipped
if (-not $SkipEureka) {
    if (Test-Path $eurekaDir -PathType Container) {
        Start-Module -Name "Eureka" -WorkingDir $eurekaDir -Command ".\mvnw.cmd spring-boot:run"
        Wait-HttpReady -Name "Eureka" -Url "http://localhost:8071/eureka/apps" -TimeoutSeconds 120
    } else {
        Write-Warning "Eureka directory not found. Assuming Eureka is already running."
    }
} else {
    Write-Host "[Eureka] Skipped (assuming already running)"
}

# Start Personality-Behavior Service
Start-Module -Name "Personality-Behavior Service" -WorkingDir $root -Command ".\mvnw.cmd spring-boot:run"
Wait-HttpReady -Name "Personality-Behavior Service" -Url "http://localhost:8084/actuator/health" -TimeoutSeconds 180
Wait-EurekaRegistration -AppName "personality-behavior-service" -TimeoutSeconds 120

Write-Host ""
Write-Host "Personality-Behavior Service is running!"
Write-Host "Service URL: http://localhost:8084"
Write-Host "API Endpoints:"
Write-Host "  - POST   /api/personalities"
Write-Host "  - GET    /api/personalities/{id}"
Write-Host "  - GET    /api/personalities/user/{userId}"
Write-Host "  - GET    /api/personalities"
Write-Host "  - PUT    /api/personalities/{id}"
Write-Host "  - DELETE /api/personalities/{id}"
Write-Host "  - POST   /api/behaviors"
Write-Host "  - GET    /api/behaviors/{id}"
Write-Host "  - GET    /api/behaviors/user/{userId}"
Write-Host "  - GET    /api/behaviors"
Write-Host "  - PUT    /api/behaviors/{id}"
Write-Host "  - DELETE /api/behaviors/{id}"
Write-Host ""
Write-Host "Use Ctrl+C in the window to stop the service."
