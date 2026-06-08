param(
    [string]$BackendRoot = "..\forum",
    [string]$MavenCommand = "",
    [int]$GatewayPort = 8190,
    [int]$UserPort = 8191,
    [int]$CategoryPort = 8192,
    [int]$ThreadPort = 8193,
    [int]$PostPort = 8194,
    [int]$CommentPort = 8195,
    [int]$MessagingPort = 8196,
    [switch]$FullWait,
    [switch]$SkipPredict,
    [switch]$SkipWait,
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

    if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
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
    param(
        [string]$PreferredCommand = ""
    )

    if ($PreferredCommand) {
        if (Test-Path -LiteralPath $PreferredCommand -PathType Leaf) {
            return (Resolve-Path -LiteralPath $PreferredCommand).Path
        }

        $preferred = Get-Command $PreferredCommand -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($preferred) {
            return $preferred.Source
        }

        throw "Maven command was not found: $PreferredCommand"
    }

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

    $intellijMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path -LiteralPath $intellijMaven -PathType Leaf) {
        return (Resolve-Path -LiteralPath $intellijMaven).Path
    }

    throw "Maven was not found in PATH and no local mvnw.cmd fallback was found. Install Maven or pass -MavenCommand 'C:\path\to\mvn.cmd'."
}

function Get-PythonLaunchCommand {
    foreach ($candidate in @("python", "python3")) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            return "& '$(Escape-SingleQuotes -Value $command.Source)' app.py"
        }
    }

    $py = Get-Command "py" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($py) {
        return "& '$(Escape-SingleQuotes -Value $py.Source)' app.py"
    }

    throw "Python was not found in PATH. Install Python or run with -SkipPredict."
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

    if ($SkipWait -or $DryRun) {
        Write-Host "[$Name] Skipping wait for $Url"
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

    Write-Warning "[$Name] Timed out waiting for $Url. Startup may still be in progress."
}

function Wait-EurekaRegistration {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AppName,
        [string]$EurekaUrl = "http://localhost:8761/eureka/apps",
        [int]$TimeoutSeconds = 180
    )

    if ($SkipWait -or $DryRun) {
        Write-Host "[Eureka] Skipping wait for $AppName"
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
            # Eureka may still be starting.
        }

        Start-Sleep -Seconds 2
    }

    Write-Warning "[Eureka] Timed out waiting for $target registration."
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

function Start-PredictService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDir
    )

    Write-Host "[Predict Service] $WorkingDir"

    if (Test-TcpPort -Port 5001) {
        Write-Host "[Predict Service] Port 5001 is already listening. Skipping startup."
        return
    }

    $pythonLaunch = Get-PythonLaunchCommand
    $escapedDir = Escape-SingleQuotes -Value $WorkingDir
    $modelPath = Join-Path $WorkingDir "ybrainy_model.pkl"
    $escapedModelPath = Escape-SingleQuotes -Value $modelPath
    $command = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = 'Predict Service'; `$env:MODEL_PATH = '$escapedModelPath'; $pythonLaunch"

    if ($DryRun) {
        Write-Host "[Predict Service] Dry run: powershell.exe -NoExit -Command $command"
        return
    }

    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoExit", "-Command", $command `
        -WorkingDirectory $WorkingDir | Out-Null

    Write-Host "[Predict Service] Launch command started in a new PowerShell window."
}

$backendRootPath = Resolve-ExistingPath -Path $BackendRoot -Label "Forum backend root"
$eurekaDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Eureka" -ChildDir "eureka"
$configDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Config Server" -ChildDir "config-server"
$gatewayDir = Require-ProjectDir -ParentDir $backendRootPath -Name "API Gateway" -ChildDir "api-gateway"
$userDir = Require-ProjectDir -ParentDir $backendRootPath -Name "User Service" -ChildDir "user-service"
$categoryDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Category Service" -ChildDir "category-service"
$threadDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Thread Service" -ChildDir "thread-service"
$postDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Post Service" -ChildDir "post-service"
$commentDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Comment Service" -ChildDir "comment-service"
$messagingDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Messaging Service" -ChildDir "messaging-service"
$predictDir = Require-ProjectDir -ParentDir $backendRootPath -Name "Predict Service" -ChildDir "predict-service"

$mavenCommand = Get-MavenCommand -PreferredCommand $MavenCommand
$mavenRepoRoot = Join-Path $scriptRoot ".m2-forum"

if (-not (Test-Path -LiteralPath $mavenRepoRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $mavenRepoRoot | Out-Null
}

Write-Host "Forum backend root: $backendRootPath"
Write-Host "Using Maven: $mavenCommand"
Write-Host "Using Maven repo root: $mavenRepoRoot"
Write-Host ""
Write-Host "Services to start:"
Write-Host " - Eureka             http://localhost:8761"
Write-Host " - Config Server      http://localhost:8888"
Write-Host " - User Service       http://localhost:$UserPort"
Write-Host " - Category Service   http://localhost:$CategoryPort"
Write-Host " - Thread Service     http://localhost:$ThreadPort"
Write-Host " - Post Service       http://localhost:$PostPort"
Write-Host " - Comment Service    http://localhost:$CommentPort"
Write-Host " - Messaging Service  http://localhost:$MessagingPort"
Write-Host " - Predict Service    http://localhost:5001"
Write-Host " - API Gateway        http://localhost:$GatewayPort"
Write-Host ""

if (-not (Test-TcpPort -Port 3306)) {
    Write-Warning "MySQL is not listening on 127.0.0.1:3306. Forum services will fail until MySQL is running."
}

if (-not (Test-TcpPort -Port 5672)) {
    Write-Warning "RabbitMQ is not listening on 127.0.0.1:5672. User/thread/post/comment services may fail until RabbitMQ is running."
}

Write-Host "Notes:"
Write-Host " - The Angular frontend expects the forum gateway at http://localhost:$GatewayPort."
Write-Host " - Local MySQL config uses root with an empty password in the config-server YAML files."
Write-Host " - If your MySQL password is root, update the config YAMLs or use Docker Compose instead."
Write-Host " - Each Spring service gets its own local Maven repo to avoid Windows file-lock conflicts."
Write-Host " - Default mode starts services quickly. Add -FullWait to wait for every health and Eureka check."
Write-Host ""

$serviceDefinitions = @(
    @{ Name = "Eureka"; Dir = $eurekaDir; Port = 8761; Health = "http://localhost:8761/eureka/apps"; EurekaName = $null },
    @{ Name = "Config Server"; Dir = $configDir; Port = 8888; Health = "http://localhost:8888/actuator/health"; EurekaName = $null },
    @{ Name = "User Service"; Dir = $userDir; Port = $UserPort; Health = "http://localhost:$UserPort/actuator/health"; EurekaName = "user-service" },
    @{ Name = "Category Service"; Dir = $categoryDir; Port = $CategoryPort; Health = "http://localhost:$CategoryPort/actuator/health"; EurekaName = "category-service" },
    @{ Name = "Thread Service"; Dir = $threadDir; Port = $ThreadPort; Health = "http://localhost:$ThreadPort/actuator/health"; EurekaName = "thread-service" },
    @{ Name = "Post Service"; Dir = $postDir; Port = $PostPort; Health = "http://localhost:$PostPort/actuator/health"; EurekaName = "post-service" },
    @{ Name = "Comment Service"; Dir = $commentDir; Port = $CommentPort; Health = "http://localhost:$CommentPort/actuator/health"; EurekaName = "comment-service" },
    @{ Name = "Messaging Service"; Dir = $messagingDir; Port = $MessagingPort; Health = "http://localhost:$MessagingPort/actuator/health"; EurekaName = "messaging-service" },
    @{ Name = "API Gateway"; Dir = $gatewayDir; Port = $GatewayPort; Health = "http://localhost:$GatewayPort/actuator/health"; EurekaName = "api-gateway" }
)

foreach ($service in $serviceDefinitions) {
    $repoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value $service.Name)
    if (-not (Test-Path -LiteralPath $repoDir -PathType Container)) {
        New-Item -ItemType Directory -Path $repoDir | Out-Null
    }
}

Start-SpringBootModule `
    -Name "Eureka" `
    -WorkingDir $eurekaDir `
    -MavenCommand $mavenCommand `
    -Port 8761 `
    -Arguments @("-Dmaven.repo.local=$(Join-Path $mavenRepoRoot 'Eureka')")
Wait-HttpReady -Name "Eureka" -Url "http://localhost:8761/eureka/apps" -TimeoutSeconds 180

Start-SpringBootModule `
    -Name "Config Server" `
    -WorkingDir $configDir `
    -MavenCommand $mavenCommand `
    -Port 8888 `
    -Arguments @("-Dmaven.repo.local=$(Join-Path $mavenRepoRoot 'Config-Server')")
Wait-HttpReady -Name "Config Server" -Url "http://localhost:8888/actuator/health" -TimeoutSeconds 180

if (-not $SkipPredict) {
    Start-PredictService -WorkingDir $predictDir
    Wait-HttpReady -Name "Predict Service" -Url "http://localhost:5001/health" -TimeoutSeconds 120
}

$mainServices = @(
    @{ Name = "User Service"; Dir = $userDir; Port = $UserPort; Health = "http://localhost:$UserPort/actuator/health"; EurekaName = "user-service"; UseEurekaOnly = $false; Env = @{ SERVER_PORT = "$UserPort"; FORUM_USER_PORT = "$UserPort" } },
    @{ Name = "Category Service"; Dir = $categoryDir; Port = $CategoryPort; Health = "http://localhost:$CategoryPort/actuator/health"; EurekaName = "category-service"; UseEurekaOnly = $true; Env = @{ SERVER_PORT = "$CategoryPort"; FORUM_CATEGORY_PORT = "$CategoryPort" } },
    @{ Name = "Thread Service"; Dir = $threadDir; Port = $ThreadPort; Health = "http://localhost:$ThreadPort/actuator/health"; EurekaName = "thread-service"; UseEurekaOnly = $true; Env = @{ SERVER_PORT = "$ThreadPort"; FORUM_THREAD_PORT = "$ThreadPort" } },
    @{ Name = "Post Service"; Dir = $postDir; Port = $PostPort; Health = "http://localhost:$PostPort/actuator/health"; EurekaName = "post-service"; UseEurekaOnly = $true; Env = @{ SERVER_PORT = "$PostPort"; FORUM_POST_PORT = "$PostPort" } },
    @{ Name = "Comment Service"; Dir = $commentDir; Port = $CommentPort; Health = "http://localhost:$CommentPort/actuator/health"; EurekaName = "comment-service"; UseEurekaOnly = $true; Env = @{ SERVER_PORT = "$CommentPort"; FORUM_COMMENT_PORT = "$CommentPort" } },
    @{ Name = "Messaging Service"; Dir = $messagingDir; Port = $MessagingPort; Health = "http://localhost:$MessagingPort/actuator/health"; EurekaName = "messaging-service"; UseEurekaOnly = $true; Env = @{ SERVER_PORT = "$MessagingPort"; FORUM_MESSAGING_PORT = "$MessagingPort" } }
)

foreach ($service in $mainServices) {
    $repoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value $service.Name)
    Start-SpringBootModule `
        -Name $service.Name `
        -WorkingDir $service.Dir `
        -MavenCommand $mavenCommand `
        -Port $service.Port `
        -Arguments @("-Dmaven.repo.local=$repoDir") `
        -EnvironmentVariables $service.Env
}

foreach ($service in $mainServices) {
    if ($FullWait) {
        if (-not $service.UseEurekaOnly) {
            Wait-HttpReady -Name $service.Name -Url $service.Health -TimeoutSeconds 240
        } else {
            Write-Host "[$($service.Name)] Skipping direct /actuator/health wait and using Eureka registration as readiness signal."
        }
        Wait-EurekaRegistration -AppName $service.EurekaName -TimeoutSeconds 240
    } else {
        Write-Host "[$($service.Name)] Startup launched. Skipping long health/Eureka wait; use -FullWait for verification."
    }
}

Start-SpringBootModule `
    -Name "API Gateway" `
    -WorkingDir $gatewayDir `
    -MavenCommand $mavenCommand `
    -Port $GatewayPort `
    -Arguments @("-Dmaven.repo.local=$(Join-Path $mavenRepoRoot 'API-Gateway')") `
    -EnvironmentVariables @{ SERVER_PORT = "$GatewayPort" }
if ($FullWait) {
    Wait-HttpReady -Name "API Gateway" -Url "http://localhost:$GatewayPort/actuator/health" -TimeoutSeconds 180
    Wait-EurekaRegistration -AppName "api-gateway" -TimeoutSeconds 180
} else {
    Write-Host "[API Gateway] Startup launched. Skipping long health/Eureka wait; use -FullWait for verification."
}

Write-Host ""
Write-Host "Forum backend launch sequence finished."
Write-Host "Frontend should call:"
Write-Host " - http://localhost:$GatewayPort/api/categories"
Write-Host " - http://localhost:$GatewayPort/api/threads"
Write-Host " - http://localhost:$GatewayPort/api/posts"
Write-Host " - http://localhost:$GatewayPort/api/comments"
Write-Host " - http://localhost:$GatewayPort/api/messages"
Write-Host " - http://localhost:$GatewayPort/api/ai"
Write-Host ""
if (-not $FullWait) {
    Write-Host "Services may need another minute to finish booting in their own windows."
    Write-Host "If something still fails, rerun with -FullWait to see exactly which service is not ready."
    Write-Host ""
}
Write-Host "Use Ctrl+C in each service window to stop the services."
