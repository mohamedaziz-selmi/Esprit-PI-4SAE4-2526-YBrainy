param(
    [string]$CoursesRoot = "..\courses",
    [string]$CartRoot = "..\payment\cart",
    [string]$MavenCommand = "",
    [int]$EurekaPort = 8071,
    [int]$GatewayPort = 8088,
    [int]$UserPort = 8899,
    [int]$CoursePort = 8093,
    [int]$QuizPort = 8094,
    [int]$CartPort = 8954,
    [int]$MlPort = 5000,
    [switch]$SkipUserService,
    [switch]$SkipQuizService,
    [switch]$SkipCartService,
    [switch]$SkipMlService,
    [switch]$FullWait,
    [switch]$SkipWait,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenOverrideArg = $MavenCommand

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
        [string[]]$Candidates
    )

    foreach ($childDir in $Candidates) {
        $dir = Join-Path $ParentDir $childDir
        if (Test-Path -LiteralPath $dir -PathType Container) {
            return (Resolve-Path -LiteralPath $dir).Path
        }
    }

    throw "$Name directory not found. Tried: $($Candidates -join ', ')"
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

    throw "Maven was not found in PATH and no fallback was found. Install Maven or pass -MavenCommand 'C:\path\to\mvn.cmd'."
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

    throw "Python was not found in PATH. Install Python or run with -SkipMlService."
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
        Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4 | Out-Null
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
        [Parameter(Mandatory = $true)]
        [string]$EurekaUrl,
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
        [hashtable]$EnvironmentVariables = @{},
        [switch]$PreferProjectWrapper
    )

    Write-Host "[$Name] $WorkingDir"

    if (Test-TcpPort -Port $Port) {
        Write-Host "[$Name] Port $Port is already listening. Skipping startup."
        return
    }

    $escapedDir = Escape-SingleQuotes -Value $WorkingDir
    $escapedName = Escape-SingleQuotes -Value $Name
    $effectiveMavenCommand = $MavenCommand
    $projectWrapper = Join-Path $WorkingDir "mvnw.cmd"
    if ($PreferProjectWrapper -and (Test-Path -LiteralPath $projectWrapper -PathType Leaf)) {
        $effectiveMavenCommand = (Resolve-Path -LiteralPath $projectWrapper).Path
    }

    $escapedMaven = Escape-SingleQuotes -Value $effectiveMavenCommand
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

function Start-PythonModule {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDir,
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    Write-Host "[$Name] $WorkingDir"

    if (Test-TcpPort -Port $Port) {
        Write-Host "[$Name] Port $Port is already listening. Skipping startup."
        return
    }

    $pythonLaunch = Get-PythonLaunchCommand
    $escapedDir = Escape-SingleQuotes -Value $WorkingDir
    $escapedName = Escape-SingleQuotes -Value $Name
    $command = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = '$escapedName'; $pythonLaunch"

    if ($DryRun) {
        Write-Host "[$Name] Dry run: powershell.exe -NoExit -Command $command"
        return
    }

    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoExit", "-Command", $command `
        -WorkingDirectory $WorkingDir | Out-Null

    Write-Host "[$Name] Launch command started in a new PowerShell window."
}

$coursesRootPath = Resolve-ExistingPath -Path $CoursesRoot -Label "Courses backend root"
$cartRootPath = $null
if (-not $SkipCartService) {
    $cartRootPath = Resolve-ExistingPath -Path $CartRoot -Label "Cart backend root"
}
$eurekaDir = Require-ProjectDir -ParentDir $coursesRootPath -Name "Eureka" -Candidates @("p-r-k\Eureka")
$gatewayDir = Require-ProjectDir -ParentDir $coursesRootPath -Name "API Gateway" -Candidates @("p-r-k\ApiGateway\ApiGateway", "p-r-k\ApiGateway")
$courseDir = Require-ProjectDir -ParentDir $coursesRootPath -Name "Course Service" -Candidates @("Course\tp-foyer")
$quizDir = Require-ProjectDir -ParentDir $coursesRootPath -Name "Quiz Service" -Candidates @("Quiz\quiz-service")
$mlDir = Require-ProjectDir -ParentDir $coursesRootPath -Name "ML Service" -Candidates @("ML-Service")
$userDir = $scriptRoot
$courseUploadDir = Join-Path $courseDir "uploads"

$resolvedMavenExecutable = Get-MavenCommand -PreferredCommand $mavenOverrideArg
$preferProjectWrapper = [string]::IsNullOrWhiteSpace($mavenOverrideArg)
$mavenRepoRoot = Join-Path $scriptRoot ".m2-courses"
$eurekaAppsUrl = "http://localhost:$EurekaPort/eureka/apps"
$eurekaDefaultZone = "http://localhost:$EurekaPort/eureka/"

if (-not (Test-Path -LiteralPath $mavenRepoRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $mavenRepoRoot | Out-Null
}

Write-Host "Courses backend root: $coursesRootPath"
Write-Host "Using Maven: $resolvedMavenExecutable"
if ($preferProjectWrapper) {
    Write-Host "Project mvnw.cmd files will be preferred when present."
}
Write-Host "Using Maven repo root: $mavenRepoRoot"
Write-Host "Using course upload dir: $courseUploadDir"
Write-Host ""
Write-Host "Services to start:"
Write-Host " - Eureka             http://localhost:$EurekaPort"
if (-not $SkipUserService) {
    Write-Host " - User Service       http://localhost:$UserPort"
}
Write-Host " - Course Service     http://localhost:$CoursePort"
if (-not $SkipQuizService) {
    Write-Host " - Quiz Service       http://localhost:$QuizPort"
}
if (-not $SkipCartService) {
    Write-Host " - Cart Service       http://localhost:$CartPort"
}
if (-not $SkipMlService) {
    Write-Host " - ML Service         http://localhost:$MlPort"
}
Write-Host " - API Gateway        http://localhost:$GatewayPort"
Write-Host ""

if (-not (Test-TcpPort -Port 3306)) {
    Write-Warning "MySQL is not listening on 127.0.0.1:3306. Course services will fail until MySQL is running."
}

Write-Host "Notes:"
Write-Host " - Angular course pages normally call the gateway at http://localhost:$GatewayPort."
Write-Host " - Angular pack cart calls the cart service directly at http://localhost:$CartPort/api/cart."
Write-Host " - The courses gateway routes users/auth/tracking to the current user project as breadandbutteruser."
Write-Host " - Course and quiz ports default to $CoursePort/$QuizPort to avoid forum port conflicts."
Write-Host " - Python ML dependencies must already be installed for the Python version used by 'python app.py'."
Write-Host " - Add -FullWait if you want the script to wait for every health and Eureka registration."
Write-Host ""

Start-SpringBootModule `
    -Name "Courses Eureka" `
    -WorkingDir $eurekaDir `
    -MavenCommand $resolvedMavenExecutable `
    -Port $EurekaPort `
    -Arguments @("-Dmaven.repo.local=$(Join-Path $mavenRepoRoot 'Eureka')") `
    -EnvironmentVariables @{
        SERVER_PORT = "$EurekaPort"
        EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
    } `
    -PreferProjectWrapper:$preferProjectWrapper
Wait-HttpReady -Name "Courses Eureka" -Url $eurekaAppsUrl -TimeoutSeconds 180

if (-not $SkipMlService) {
    Start-PythonModule -Name "Courses ML Service" -WorkingDir $mlDir -Port $MlPort
    Wait-HttpReady -Name "Courses ML Service" -Url "http://localhost:$MlPort/health" -TimeoutSeconds 120
}

$mainServices = @()

if (-not $SkipUserService) {
    $mainServices += @{
        Name = "Courses User Service"
        Dir = $userDir
        Port = $UserPort
        Health = "http://localhost:$UserPort/actuator/health"
        EurekaName = "breadandbutteruser"
        Env = @{
            SERVER_PORT = "$UserPort"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
        }
    }
}

$mainServices += @{
    Name = "Course Service"
    Dir = $courseDir
    Port = $CoursePort
    Health = "http://localhost:$CoursePort/actuator/health"
    EurekaName = "course-service"
    Env = @{
        SERVER_PORT = "$CoursePort"
        EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
        ML_SERVICE_URL = "http://localhost:$MlPort"
        APP_FILE_UPLOAD_DIR = $courseUploadDir
    }
}

if (-not $SkipQuizService) {
    $mainServices += @{
        Name = "Quiz Service"
        Dir = $quizDir
        Port = $QuizPort
        Health = "http://localhost:$QuizPort/actuator/health"
        EurekaName = "quiz-service"
        Env = @{
            SERVER_PORT = "$QuizPort"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
        }
    }
}

if (-not $SkipCartService) {
    $mainServices += @{
        Name = "Cart Service"
        Dir = $cartRootPath
        Port = $CartPort
        Health = "http://localhost:$CartPort/api/cart"
        EurekaName = "cart-service"
        Env = @{
            SERVER_PORT = "$CartPort"
            EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
        }
    }
}

foreach ($service in $mainServices) {
    $repoDir = Join-Path $mavenRepoRoot (Get-SafeFolderName -Value $service.Name)
    if (-not (Test-Path -LiteralPath $repoDir -PathType Container)) {
        New-Item -ItemType Directory -Path $repoDir | Out-Null
    }

    Start-SpringBootModule `
        -Name $service.Name `
        -WorkingDir $service.Dir `
        -MavenCommand $resolvedMavenExecutable `
        -Port $service.Port `
        -Arguments @("-Dmaven.repo.local=$repoDir") `
        -EnvironmentVariables $service.Env `
        -PreferProjectWrapper:$preferProjectWrapper
}

foreach ($service in $mainServices) {
    if ($FullWait) {
        Wait-HttpReady -Name $service.Name -Url $service.Health -TimeoutSeconds 240
        Wait-EurekaRegistration -AppName $service.EurekaName -EurekaUrl $eurekaAppsUrl -TimeoutSeconds 240
    } else {
        Write-Host "[$($service.Name)] Startup launched. Skipping long health/Eureka wait; use -FullWait for verification."
    }
}

Start-SpringBootModule `
    -Name "Courses API Gateway" `
    -WorkingDir $gatewayDir `
    -MavenCommand $resolvedMavenExecutable `
    -Port $GatewayPort `
    -Arguments @("-Dmaven.repo.local=$(Join-Path $mavenRepoRoot 'API-Gateway')") `
    -EnvironmentVariables @{
        SERVER_PORT = "$GatewayPort"
        EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = $eurekaDefaultZone
    }

if ($FullWait) {
    Wait-HttpReady -Name "Courses API Gateway" -Url "http://localhost:$GatewayPort/actuator/health" -TimeoutSeconds 180
    Wait-EurekaRegistration -AppName "api-gateway" -EurekaUrl $eurekaAppsUrl -TimeoutSeconds 180
} else {
    Write-Host "[Courses API Gateway] Startup launched. Skipping long health/Eureka wait; use -FullWait for verification."
}

Write-Host ""
Write-Host "Courses backend launch sequence finished."
Write-Host "Frontend should call:"
Write-Host " - http://localhost:$GatewayPort/api/courses"
Write-Host " - http://localhost:$GatewayPort/api/enrollments"
Write-Host " - http://localhost:$GatewayPort/api/quizzes"
Write-Host " - http://localhost:$GatewayPort/api/ml"
Write-Host " - http://localhost:$CartPort/api/cart"
Write-Host ""
if (-not $FullWait) {
    Write-Host "Services may need another minute to finish booting in their own windows."
    Write-Host "If something still fails, rerun with -FullWait to see exactly which service is not ready."
    Write-Host ""
}
Write-Host "Use Ctrl+C in each service window to stop the services."
