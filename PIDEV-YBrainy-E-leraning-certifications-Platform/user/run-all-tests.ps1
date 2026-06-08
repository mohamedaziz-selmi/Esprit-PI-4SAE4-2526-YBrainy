param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [string]$FrontendBrowser = "ChromeHeadless"
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

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDir,
        [Parameter(Mandatory = $true)]
        [string]$CommandLine
    )

    Write-Host ""
    Write-Host "== $Name =="
    Write-Host "Dir: $WorkingDir"
    Write-Host "Cmd: $CommandLine"

    Push-Location $WorkingDir
    try {
        & powershell.exe -NoLogo -NoProfile -Command $CommandLine
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$userServiceDir = $root
$angularDir = Require-ProjectDir -Name "Angular" -Candidates @("angular\angular-app")

Write-Host "Root: $root"
$backendStatus = if ($SkipBackend) { "SKIPPED" } else { "ENABLED" }
$frontendStatus = if ($SkipFrontend) { "SKIPPED" } else { "ENABLED ($FrontendBrowser)" }
Write-Host "Backend tests:  $backendStatus"
Write-Host "Frontend tests: $frontendStatus"
Write-Host "Note: Frontend tests require a local browser supported by Karma (e.g. Chrome)."

if (-not $SkipBackend) {
    Invoke-Step -Name "Backend Tests" -WorkingDir $userServiceDir -CommandLine ".\mvnw.cmd -q test"
}

if (-not $SkipFrontend) {
    Invoke-Step -Name "Frontend Tests" -WorkingDir $angularDir -CommandLine "npm.cmd run test -- --watch=false --browsers=$FrontendBrowser --no-progress"
}

Write-Host ""
Write-Host "All requested tests completed successfully."
