param(
    [switch]$NoNewWindows
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Resolve-ExistingDir {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        $full = Join-Path $root $candidate
        if (Test-Path $full -PathType Container) {
            return (Resolve-Path $full).Path
        }
    }

    return $null
}

function Require-ProjectDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string[]]$Candidates
    )

    $dir = Resolve-ExistingDir -Candidates $Candidates
    if (-not $dir) {
        throw "Could not locate $Name project. Tried: $($Candidates -join ', ')"
    }
    return $dir
}

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

function Wait-Step {
    param(
        [int]$Seconds = 6,
        [string]$Reason = ""
    )

    if ($Seconds -gt 0) {
        if ($Reason) {
            Write-Host "Waiting $Seconds s ($Reason)..."
        } else {
            Write-Host "Waiting $Seconds s..."
        }
        Start-Sleep -Seconds $Seconds
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
        # Any HTTP response (even 4xx/5xx) means the process is listening.
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

$userServiceDir = $root
$eurekaDir = Require-ProjectDir -Name "Eureka" -Candidates @(
    "p-r-k\Eureka",
    "p-r-k\Eureka\Eureka"
)
$gatewayDir = Require-ProjectDir -Name "API Gateway" -Candidates @(
    "p-r-k\ApiGateway\ApiGateway",
    "p-r-k\ApiGateway"
)
$angularDir = Require-ProjectDir -Name "Angular" -Candidates @(
    "angular\angular-app"
)
$angularNodeModulesDir = Join-Path $angularDir "node_modules"

Write-Host "Root: $root"
Write-Host "Detected projects:"
Write-Host " - Eureka:      $eurekaDir"
Write-Host " - API Gateway: $gatewayDir"
Write-Host " - User Service:$userServiceDir"
Write-Host " - Angular:     $angularDir"
Write-Host ""
Write-Host "Notes:"
Write-Host " - Ensure MySQL and Keycloak are already running."
Write-Host " - Optional but recommended: set KEYCLOAK_ADMIN_CLIENT_SECRET for the bb-user-admin client."
Write-Host " - Login uses keycloak.auth client (default: angular-client) and goes through Gateway/Eureka."
Write-Host " - For Google sign-in, set KEYCLOAK_GOOGLE_CLIENT_ID and KEYCLOAK_GOOGLE_CLIENT_SECRET."
Write-Host ""

if (-not (Test-Path $angularNodeModulesDir -PathType Container)) {
    throw "Angular dependencies are missing in $angularDir. Run 'npm.cmd install' there first."
}

$googleIdpScript = Join-Path $root "scripts\configure-keycloak-google-idp.ps1"
if ($env:KEYCLOAK_GOOGLE_CLIENT_ID -and $env:KEYCLOAK_GOOGLE_CLIENT_SECRET) {
    try {
        & $googleIdpScript
    } catch {
        Write-Warning "[Keycloak] Google IdP provisioning failed: $($_.Exception.Message)"
    }
} else {
    Write-Host "[Keycloak] Google sign-in provisioning skipped because KEYCLOAK_GOOGLE_CLIENT_ID / KEYCLOAK_GOOGLE_CLIENT_SECRET are not set."
}

Start-Module -Name "Eureka" -WorkingDir $eurekaDir -Command ".\mvnw.cmd spring-boot:run"
Wait-HttpReady -Name "Eureka" -Url "http://localhost:8071/eureka/apps" -TimeoutSeconds 120

Start-Module -Name "User Service" -WorkingDir $userServiceDir -Command ".\mvnw.cmd spring-boot:run"
Wait-HttpReady -Name "User Service" -Url "http://localhost:8899/actuator/health" -TimeoutSeconds 180
Wait-EurekaRegistration -AppName "breadandbutteruser" -TimeoutSeconds 180

Start-Module -Name "API Gateway" -WorkingDir $gatewayDir -Command ".\mvnw.cmd spring-boot:run"
Wait-HttpReady -Name "API Gateway" -Url "http://localhost:8088/" -TimeoutSeconds 180
Wait-EurekaRegistration -AppName "api-gateway" -TimeoutSeconds 120

Start-Module -Name "Angular" -WorkingDir $angularDir -Command "npm.cmd run start"

Write-Host ""
Write-Host "All launch commands were started in separate PowerShell windows."
Write-Host "Use Ctrl+C in each window to stop the modules."
