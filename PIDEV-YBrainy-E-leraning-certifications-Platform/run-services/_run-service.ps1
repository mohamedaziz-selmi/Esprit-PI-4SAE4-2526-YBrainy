param(
    [Parameter(Mandatory = $true)]
    [string]$Name,

    [Parameter(Mandatory = $true)]
    [string]$ProjectPath,

    [ValidateSet("Maven", "Npm", "Python", "Command")]
    [string]$Kind = "Maven",

    [int]$Port = 0,
    [string]$WaitUrl = "",
    [string]$MavenRepoGroup = "services",
    [string[]]$MavenArgs = @(),
    [hashtable]$EnvVars = @{},
    [string]$EurekaUrl = "http://localhost:8761/eureka/",
    [string]$NpmScript = "start",
    [string[]]$NpmArgs = @(),
    [string]$PythonScript = "app.py",
    [string]$Command = "",
    [int]$TimeoutSeconds = 180,
    [switch]$SkipWait,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

if (-not $PSBoundParameters.ContainsKey("EurekaUrl") -and $env:YBRAINY_EUREKA_URL) {
    $EurekaUrl = $env:YBRAINY_EUREKA_URL
}

function Escape-SingleQuotes {
    param([Parameter(Mandatory = $true)][string]$Value)
    return $Value -replace "'", "''"
}

function Resolve-ProjectDir {
    param([Parameter(Mandatory = $true)][string]$Path)

    $candidate = $Path
    if (-not [System.IO.Path]::IsPathRooted($candidate)) {
        $candidate = Join-Path $repoRoot $candidate
    }

    if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
        throw "Project directory not found: $candidate"
    }

    return (Resolve-Path -LiteralPath $candidate).Path
}

function Get-SafeFolderName {
    param([Parameter(Mandatory = $true)][string]$Value)
    return (($Value -replace '[^A-Za-z0-9._-]', '-') -replace '-+', '-').Trim('-')
}

function Get-FirstCommand {
    param([Parameter(Mandatory = $true)][string[]]$Candidates)

    foreach ($candidate in $Candidates) {
        $commandInfo = Get-Command $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($commandInfo) {
            return $commandInfo.Source
        }
    }

    return $null
}

function Get-MavenCommand {
    param([Parameter(Mandatory = $true)][string]$WorkingDir)

    $localWrapper = Join-Path $WorkingDir "mvnw.cmd"
    if (Test-Path -LiteralPath $localWrapper -PathType Leaf) {
        return (Resolve-Path -LiteralPath $localWrapper).Path
    }

    $pathMaven = Get-FirstCommand -Candidates @("mvn.cmd", "mvn")
    if ($pathMaven) {
        return $pathMaven
    }

    $repoWrapper = Join-Path $repoRoot "user\mvnw.cmd"
    if (Test-Path -LiteralPath $repoWrapper -PathType Leaf) {
        return (Resolve-Path -LiteralPath $repoWrapper).Path
    }

    throw "Maven was not found. Install Maven, add it to PATH, or add mvnw.cmd to the service directory."
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)][int]$PortToCheck,
        [string]$HostName = "127.0.0.1",
        [int]$TimeoutMs = 900
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $PortToCheck, $null, $null)
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

function Wait-TcpReady {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][int]$PortToWaitFor,
        [int]$Timeout = 180
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -PortToCheck $PortToWaitFor) {
            Write-Host "[$ServiceName] Port $PortToWaitFor is listening."
            return
        }
        Start-Sleep -Seconds 2
    }

    Write-Warning "[$ServiceName] Timed out waiting for port $PortToWaitFor. Startup may still be running in its window."
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$Timeout = 180
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "[$ServiceName] Ready at $Url"
                return
            }
        } catch {
            if ($_.Exception.Response) {
                Write-Host "[$ServiceName] HTTP endpoint responded at $Url"
                return
            }
        }
        Start-Sleep -Seconds 2
    }

    Write-Warning "[$ServiceName] Timed out waiting for $Url. Startup may still be running in its window."
}

function ConvertTo-QuotedArguments {
    param([string[]]$Arguments)
    return ($Arguments | ForEach-Object { "'$(Escape-SingleQuotes -Value $_)'" }) -join " "
}

function Get-PreferredMavenRepoRoot {
    param(
        [Parameter(Mandatory = $true)][string]$RepoGroup,
        [Parameter(Mandatory = $true)][string]$RelativeProjectPath
    )

    $pathKey = ($RelativeProjectPath -replace '/', '\').Trim('\').ToLowerInvariant()

    $legacyRoot = $null
    if ($pathKey -like "forum\*") {
        $legacyRoot = Join-Path $repoRoot "user\.m2-forum"
    } elseif ($pathKey -like "parteneriat\backend\*") {
        $legacyRoot = Join-Path $repoRoot "user\.m2-parteneriat"
    } elseif ($pathKey -like "payment\*" -or $pathKey -like "courses\*") {
        $legacyRoot = Join-Path $repoRoot "user\.m2-courses"
    }

    if ($legacyRoot -and (Test-Path -LiteralPath $legacyRoot -PathType Container)) {
        return (Resolve-Path -LiteralPath $legacyRoot).Path
    }

    return (Join-Path (Join-Path $repoRoot ".m2-runners") (Get-SafeFolderName -Value $RepoGroup))
}

function Get-MavenRepoLeafName {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$RelativeProjectPath
    )

    $pathKey = ($RelativeProjectPath -replace '/', '\').Trim('\').ToLowerInvariant()
    $legacyNames = @{
        "forum\eureka" = "Eureka"
        "forum\config-server" = "Config-Server"
        "forum\user-service" = "User-Service"
        "forum\category-service" = "Category-Service"
        "forum\thread-service" = "Thread-Service"
        "forum\post-service" = "Post-Service"
        "forum\comment-service" = "Comment-Service"
        "forum\messaging-service" = "Messaging-Service"
        "forum\api-gateway" = "API-Gateway"
        "parteneriat\backend\partnership-service" = "partnership-service"
        "parteneriat\backend\job-offer-service" = "job-offer-service"
        "parteneriat\backend\api-gateway" = "api-gateway"
        "payment\cart" = "Cart-Service"
        "payment\payment" = "Payment-Service"
        "payment\finance" = "Finance-Service"
        "payment\api-gateway" = "API-Gateway"
        "courses\course\tp-foyer" = "Course-Service"
        "courses\quiz\quiz-service" = "Quiz-Service"
        "courses\p-r-k\apigateway\apigateway" = "API-Gateway"
    }

    if ($legacyNames.ContainsKey($pathKey)) {
        return $legacyNames[$pathKey]
    }

    return (Get-SafeFolderName -Value $ServiceName)
}

$workingDir = Resolve-ProjectDir -Path $ProjectPath

if ($Port -gt 0 -and (Test-TcpPort -PortToCheck $Port)) {
    Write-Host "[$Name] Port $Port is already listening. Skipping startup."
    return
}

if ($Kind -eq "Maven" -and $EurekaUrl -and $Name -notmatch 'Eureka|Discovery Server|Config Server') {
    foreach ($key in @("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE", "EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE", "EUREKA_DEFAULT_ZONE")) {
        if (-not $EnvVars.ContainsKey($key)) {
            $EnvVars[$key] = $EurekaUrl
        }
    }
}

if ($Kind -eq "Maven") {
    $mavenDefaultEnv = @{
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "3"
        SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "1"
        SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT = "30000"
        SPRING_DATASOURCE_HIKARI_MAX_LIFETIME = "120000"
        SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = "20000"
    }

    foreach ($pair in $mavenDefaultEnv.GetEnumerator()) {
        $existingEnvVar = [Environment]::GetEnvironmentVariable($pair.Key)
        if (-not $EnvVars.ContainsKey($pair.Key) -and -not $existingEnvVar) {
            $EnvVars[$pair.Key] = $pair.Value
        }
    }
}

$environmentSetup = ""
foreach ($pair in $EnvVars.GetEnumerator()) {
    $escapedValue = Escape-SingleQuotes -Value ([string]$pair.Value)
    $environmentSetup += "`$env:$($pair.Key) = '$escapedValue'; "
}

switch ($Kind) {
    "Maven" {
        $mavenCommand = Get-MavenCommand -WorkingDir $workingDir
        $runArgs = @("-nsu", "-DskipTests") + $MavenArgs + @("spring-boot:run")

        if ($env:YBRAINY_USE_ISOLATED_MAVEN_REPO -eq "1") {
            $safeName = Get-MavenRepoLeafName -ServiceName $Name -RelativeProjectPath $ProjectPath
            $mavenRepoRoot = Get-PreferredMavenRepoRoot -RepoGroup $MavenRepoGroup -RelativeProjectPath $ProjectPath
            $mavenRepoDir = Join-Path $mavenRepoRoot $safeName

            if (-not (Test-Path -LiteralPath $mavenRepoDir -PathType Container)) {
                New-Item -ItemType Directory -Path $mavenRepoDir -Force | Out-Null
            }

            $runArgs = @("-Dmaven.repo.local=$mavenRepoDir") + $runArgs
        }

        $runCommand = "& '$(Escape-SingleQuotes -Value $mavenCommand)' $(ConvertTo-QuotedArguments -Arguments $runArgs)"
    }
    "Npm" {
        $npmCommand = Get-FirstCommand -Candidates @("npm.cmd", "npm")
        if (-not $npmCommand) {
            throw "npm was not found in PATH."
        }
        $runArgs = @("run", $NpmScript) + $NpmArgs
        $runCommand = "& '$(Escape-SingleQuotes -Value $npmCommand)' $(ConvertTo-QuotedArguments -Arguments $runArgs)"
    }
    "Python" {
        $pythonCommand = Get-FirstCommand -Candidates @("python", "python3", "py")
        if (-not $pythonCommand) {
            throw "Python was not found in PATH."
        }

        $requirementsPath = Join-Path $workingDir "requirements.txt"
        if (Test-Path -LiteralPath $requirementsPath -PathType Leaf) {
            $venvDir = Join-Path $workingDir ".venv-runner"
            $venvPython = Join-Path $venvDir "Scripts\python.exe"
            $requirementsHash = (Get-FileHash -LiteralPath $requirementsPath -Algorithm SHA256).Hash
            $requirementsHashPath = Join-Path $venvDir ".requirements.sha256"
            $createVenvArgs = @("-m", "venv", $venvDir)
            if ((Split-Path -Leaf $pythonCommand) -ieq "py.exe" -or (Split-Path -Leaf $pythonCommand) -ieq "py") {
                $createVenvArgs = @("-3") + $createVenvArgs
            }

            $createVenvCommand = "& '$(Escape-SingleQuotes -Value $pythonCommand)' $(ConvertTo-QuotedArguments -Arguments $createVenvArgs)"
            $installArgs = @("-m", "pip", "install", "--disable-pip-version-check", "-r", $requirementsPath)
            $installCommand = "& '$(Escape-SingleQuotes -Value $venvPython)' $(ConvertTo-QuotedArguments -Arguments $installArgs)"
            $installIfNeededCommand = "if ((-not (Test-Path -LiteralPath '$(Escape-SingleQuotes -Value $requirementsHashPath)')) -or ((Get-Content -LiteralPath '$(Escape-SingleQuotes -Value $requirementsHashPath)' -Raw).Trim() -ne '$(Escape-SingleQuotes -Value $requirementsHash)')) { $installCommand; if (`$LASTEXITCODE -ne 0) { exit `$LASTEXITCODE }; Set-Content -LiteralPath '$(Escape-SingleQuotes -Value $requirementsHashPath)' -Value '$(Escape-SingleQuotes -Value $requirementsHash)' -NoNewline }"
            $runCommand = "if (-not (Test-Path -LiteralPath '$(Escape-SingleQuotes -Value $venvPython)')) { $createVenvCommand; if (`$LASTEXITCODE -ne 0) { exit `$LASTEXITCODE } }; $installIfNeededCommand; & '$(Escape-SingleQuotes -Value $venvPython)' $(ConvertTo-QuotedArguments -Arguments @($PythonScript))"
        } else {
            $pythonArgs = @($PythonScript)
            if ((Split-Path -Leaf $pythonCommand) -ieq "py.exe" -or (Split-Path -Leaf $pythonCommand) -ieq "py") {
                $pythonArgs = @("-3", $PythonScript)
            }
            $runCommand = "& '$(Escape-SingleQuotes -Value $pythonCommand)' $(ConvertTo-QuotedArguments -Arguments $pythonArgs)"
        }
    }
    "Command" {
        if (-not $Command) {
            throw "Command kind requires -Command."
        }
        $runCommand = $Command
    }
}

$escapedDir = Escape-SingleQuotes -Value $workingDir
$escapedName = Escape-SingleQuotes -Value $Name
$launchCommand = "Set-Location -LiteralPath '$escapedDir'; `$host.UI.RawUI.WindowTitle = '$escapedName'; $environmentSetup$runCommand"

Write-Host "[$Name] $workingDir"
Write-Host "[$Name] $Kind startup prepared."

if ($DryRun) {
    Write-Host "[$Name] Dry run:"
    Write-Host $launchCommand
    return
}

Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $launchCommand `
    -WorkingDirectory $workingDir | Out-Null

Write-Host "[$Name] Launch command started in a new PowerShell window."

if ($SkipWait) {
    Write-Host "[$Name] Skipping readiness wait."
    return
}

if ($WaitUrl) {
    Wait-HttpReady -ServiceName $Name -Url $WaitUrl -Timeout $TimeoutSeconds
} elseif ($Port -gt 0) {
    Wait-TcpReady -ServiceName $Name -PortToWaitFor $Port -Timeout $TimeoutSeconds
}
