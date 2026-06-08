param(
    [string]$BackendRoot = "..\Parteneriat\backend",
    [int]$GatewayPort = 8096,
    [int]$PartnershipPort = 8181,
    [int]$JobOfferPort = 8182,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Resolve-ExistingPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $candidate = $Path
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $candidate = Join-Path $scriptRoot $candidate
    }

    if (-not (Test-Path -LiteralPath $candidate)) {
        throw "$Label not found: $candidate"
    }

    return (Resolve-Path -LiteralPath $candidate).Path
}

function Require-ProjectDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ParentDir,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$ChildDir
    )

    $dir = Join-Path $ParentDir $ChildDir
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        throw "$Name directory not found: $dir"
    }

    return (Resolve-Path -LiteralPath $dir).Path
}

function Escape-SingleQuotes {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return $Value -replace "'", "''"
}

function Get-SafeFolderName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return (($Value -replace '[^A-Za-z0-9._-]', '-') -replace '-+', '-').Trim('-')
}

function Get-MavenCommand {
    foreach ($candidate in @("mvn.cmd", "mvn")) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            return $command.Source
        }
    }

    $localWrapper = Join-Path $scriptRoot "mvnw.cmd"
    if (Test-Path -LiteralPath $localWrapper -PathType Leaf) {
        return (Resolve-Path -LiteralPath $localWrapper).Path
    }

    throw "Maven was not found in PATH and no local mvnw.cmd wrapper was found in $scriptRoot."
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [string]$HostName = "127.0.0.1",
        [int]$TimeoutMs = 1200
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }

        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-HttpReachable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            if ($statusCode -eq 401 -or $statusCode -eq 403) {
                return $true
            }
            return $false
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
        [int]$TimeoutSeconds = 180
    )

    if ($DryRun) {
        Write-Host "[$Name] Dry run: would wait for $Url"
        return
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpReachable -Url $Url) {
            Write-Host "[$Name] Ready at $Url"
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "[$Name] Timed out waiting for $Url"
}

function Wait-EurekaRegistration {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AppName,
        [string]$EurekaUrl = "http://localhost:8770/eureka/apps",
        [int]$TimeoutSeconds = 180
    )

    if ($DryRun) {
        Write-Host "[Eureka] Dry run: would wait for $AppName at $EurekaUrl"
        return
    }

    $target = $AppName.ToUpperInvariant()
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $registry = Invoke-RestMethod -Headers @{ Accept = "application/json" } -Uri $EurekaUrl -TimeoutSec 5
            $apps = @($registry.applications.application)

            foreach ($app in $apps) {
                if (-not $app) {
                    continue
                }

                if ([string]$app.name -ne $target) {
                    continue
                }

                $instances = @($app.instance)
                $upInstances = @($instances | Where-Object { [string]$_.status -eq "UP" })
                if ($upInstances.Count -gt 0) {
                    Write-Host "[Eureka] $target registered"
                    return
                }
            }
        } catch {
            # Discovery server may still be starting.
        }

        Start-Sleep -Seconds 2
    }

    throw "[Eureka] Timed out waiting for $target registration"
}

function Start-SpringBootModule {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDir,
        [Parameter(Mandatory = $true)]
        [string]$MavenCommand,
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [string[]]$Arguments = @(),
        [hashtable]$EnvironmentVariables = @{}
    )

    Write-Host "[$Name] $WorkingDir"

    if (Test-TcpPort -Port $Port) {
        Write-Host "[$Name] Port $Port is already listening. Skipping startup."
        return
    }

    $escapedDir = Escape-SingleQuotes -Value $WorkingDir
    $escapedName = Escape-SingleQuotes -Value $Name
    $escapedMaven = Escape-SingleQuotes -Value $MavenCommand
    $quotedArguments = @($Arguments | ForEach-Object { "'$(Escape-SingleQuotes -Value $_)'" }) + @("spring-boot:run")
    $argumentText = $quotedArguments -join " "

    $environmentSetup = ""
    if ($EnvironmentVariables.Count -gt 0) {
        $parts = foreach ($pair in $EnvironmentVariables.GetEnumerator()) {
            $escapedValue = Escape-SingleQuotes -Value ([string]$pair.Value)
            "`$env:$($pair.Key) = '$escapedValue'"
        }
        $environmentSetup = ($parts -join "; ") + "; "
    }

    $command = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = '$escapedName'; $environmentSetup& '$escapedMaven' $argumentText"

    if ($DryRun) {
        Write-Host "[$Name] Dry run: powershell.exe -NoExit -Command $command"
        return
    }

    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoExit", "-Command", $command `
        -WorkingDirectory $WorkingDir | Out-Null

    Write-Host "[$Name] Launch command started in a new PowerShell window."
}

$backendRootPath = Resolve-ExistingPath -Path $BackendRoot -Label "Backend root"
$discoveryDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Discovery Server" -ChildDir "discovery-server"
$gatewayDir = Require-ProjectDir -ParentDir $backendRootPath -Name "API Gateway" -ChildDir "api-gateway"
$partnershipDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Partnership Service" -ChildDir "partnership-service"
$jobOfferDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Job Offer Service" -ChildDir "job-offer-service"
$mavenCommand = Get-MavenCommand
$mavenRepoRoot = Join-Path $scriptRoot ".m2-parteneriat"

if (-not (Test-Path -LiteralPath $mavenRepoRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $mavenRepoRoot | Out-Null
}

Write-Host "Partner backend root: $backendRootPath"
Write-Host "Using Maven: $mavenCommand"
Write-Host "Using Maven repo root: $mavenRepoRoot"
Write-Host ""
Write-Host "Services to start for the integrated Angular recruitment/partner features:"
Write-Host " - Discovery Server     http://localhost:8770"
Write-Host " - Partnership Service  http://localhost:$PartnershipPort"
Write-Host " - Job Offer Service    http://localhost:$JobOfferPort"
Write-Host " - API Gateway          http://localhost:$GatewayPort"
Write-Host ""

if (-not (Test-TcpPort -Port 3306)) {
    Write-Warning "MySQL is not listening on 127.0.0.1:3306. partnership-service and job-offer-service will fail until MySQL is running."
}

Write-Host "Notes:"
Write-Host " - Default DBs are partnershipdb and jobofferdb on local MySQL."
Write-Host " - The gateway must be up because Angular calls http://localhost:$GatewayPort/api/..."
Write-Host " - AI generation in job-offer-service may still depend on external Gemini/HuggingFace access."
Write-Host " - Service ports are configurable via parameters/environment for conflict-free local integration."
Write-Host " - Each service gets its own local Maven repo to avoid Windows file-lock conflicts."
Write-Host ""

$discoveryRepoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value "discovery-server")
$partnershipRepoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value "partnership-service")
$jobOfferRepoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value "job-offer-service")
$gatewayRepoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value "api-gateway")

foreach ($repoDir in @($discoveryRepoDir, $partnershipRepoDir, $jobOfferRepoDir, $gatewayRepoDir)) {
    if (-not (Test-Path -LiteralPath $repoDir -PathType Container)) {
        New-Item -ItemType Directory -Path $repoDir | Out-Null
    }
}

Start-SpringBootModule -Name "Discovery Server" -WorkingDir $discoveryDir -MavenCommand $mavenCommand -Port 8770 -Arguments @("-Dmaven.repo.local=$discoveryRepoDir")
Wait-HttpReady -Name "Discovery Server" -Url "http://localhost:8770/actuator/health" -TimeoutSeconds 180

Start-SpringBootModule -Name "Partnership Service" -WorkingDir $partnershipDir -MavenCommand $mavenCommand -Port $PartnershipPort -Arguments @("-Dmaven.repo.local=$partnershipRepoDir") -EnvironmentVariables @{ SERVER_PORT = "$PartnershipPort"; PARTNERSHIP_SERVICE_PORT = "$PartnershipPort" }
Start-SpringBootModule -Name "Job Offer Service" -WorkingDir $jobOfferDir -MavenCommand $mavenCommand -Port $JobOfferPort -Arguments @("-Dmaven.repo.local=$jobOfferRepoDir") -EnvironmentVariables @{ SERVER_PORT = "$JobOfferPort"; JOB_OFFER_SERVICE_PORT = "$JobOfferPort" }

Wait-HttpReady -Name "Partnership Service" -Url "http://localhost:$PartnershipPort/actuator/health" -TimeoutSeconds 300
Wait-EurekaRegistration -AppName "partnership-service" -TimeoutSeconds 180

Wait-HttpReady -Name "Job Offer Service" -Url "http://localhost:$JobOfferPort/actuator/health" -TimeoutSeconds 420
Wait-EurekaRegistration -AppName "job-offer-service" -TimeoutSeconds 240

Start-SpringBootModule -Name "API Gateway" -WorkingDir $gatewayDir -MavenCommand $mavenCommand -Port $GatewayPort -Arguments @("-Dmaven.repo.local=$gatewayRepoDir") -EnvironmentVariables @{ SERVER_PORT = "$GatewayPort"; PARTNERSHIP_GATEWAY_PORT = "$GatewayPort" }
Wait-HttpReady -Name "API Gateway" -Url "http://localhost:$GatewayPort/actuator/health" -TimeoutSeconds 180
Wait-EurekaRegistration -AppName "api-gateway" -TimeoutSeconds 180

Write-Host ""
Write-Host "Partner backend launch sequence finished."
Write-Host "Angular can now use:"
Write-Host " - http://localhost:$GatewayPort/api/partnerships"
Write-Host " - http://localhost:$GatewayPort/api/offers"
Write-Host " - http://localhost:$GatewayPort/api/applications"
Write-Host " - http://localhost:$GatewayPort/api/generate-application"
Write-Host ""
Write-Host "Use Ctrl+C in each service window to stop the services."
