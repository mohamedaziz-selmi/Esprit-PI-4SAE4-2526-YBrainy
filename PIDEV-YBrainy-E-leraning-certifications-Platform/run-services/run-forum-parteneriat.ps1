param(
    [int]$EurekaPort = 8761,
    [int]$ForumCategoryPort = 8082,
    [int]$ForumThreadPort = 8083,
    [int]$ForumPostPort = 8084,
    [int]$ForumCommentPort = 8085,
    [int]$ForumMessagingPort = 8086,
    [int]$ForumPredictPort = 5001,
    [int]$ForumUserPort = 8191,
    [int]$PartnershipPort = 8181,
    [int]$JobOffersPort = 8182,
    [int]$ParteneriatReactPort = 5173,
    [switch]$StartForumUserService,
    [switch]$SkipPredict,
    [switch]$WithParteneriatFrontend,
    [switch]$WaitServices,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
$env:ML_PREDICT_URL = "http://localhost:$ForumPredictPort/predict"

function Test-TcpPort {
    param([Parameter(Mandatory = $true)][int]$PortToCheck)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $PortToCheck, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(700, $false)) {
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

function Write-DependencyWarning {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port
    )

    if (-not (Test-TcpPort -PortToCheck $Port)) {
        Write-Warning "$Name does not look reachable on port $Port. Services can launch, but database/broker features may fail until it is running."
    }
}

function Write-PortSummary {
    param([Parameter(Mandatory = $true)][object[]]$Services)

    Write-Host ""
    Write-Host "Port summary"
    foreach ($service in $Services) {
        $state = if (Test-TcpPort -PortToCheck $service.Port) { "listening" } else { "starting/not listening yet" }
        Write-Host ("  {0,-30} {1,5}  {2}" -f $service.Name, $service.Port, $state)
    }
}

$servicePorts = @(
    [pscustomobject]@{ Name = "Unified Eureka"; Port = $EurekaPort },
    [pscustomobject]@{ Name = "Forum Category"; Port = $ForumCategoryPort },
    [pscustomobject]@{ Name = "Forum Thread"; Port = $ForumThreadPort },
    [pscustomobject]@{ Name = "Forum Post"; Port = $ForumPostPort },
    [pscustomobject]@{ Name = "Forum Comment"; Port = $ForumCommentPort },
    [pscustomobject]@{ Name = "Forum Messaging"; Port = $ForumMessagingPort },
    [pscustomobject]@{ Name = "Partnership Service"; Port = $PartnershipPort },
    [pscustomobject]@{ Name = "Job Offer Service"; Port = $JobOffersPort }
)

if (-not $SkipPredict) {
    $servicePorts += [pscustomobject]@{ Name = "Forum Predict"; Port = $ForumPredictPort }
}

if ($StartForumUserService) {
    $servicePorts += [pscustomobject]@{ Name = "Forum User"; Port = $ForumUserPort }
}

if ($WithParteneriatFrontend) {
    $servicePorts += [pscustomobject]@{ Name = "Parteneriat React"; Port = $ParteneriatReactPort }
}

Write-Host "Launching forum + partenariat with one Eureka at $env:YBRAINY_EUREKA_URL"
Write-DependencyWarning -Name "MySQL" -Port 3306
Write-DependencyWarning -Name "RabbitMQ" -Port 5672

& "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$DryRun -DryRun:$DryRun

$serviceSkipWait = -not $WaitServices

if (-not $SkipPredict) {
    & "$PSScriptRoot\run-forum-predict.ps1" -Port $ForumPredictPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
}

if ($StartForumUserService) {
    & "$PSScriptRoot\run-forum-user.ps1" -Port $ForumUserPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
}

& "$PSScriptRoot\run-forum-category.ps1" -Port $ForumCategoryPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-thread.ps1" -Port $ForumThreadPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-post.ps1" -Port $ForumPostPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-comment.ps1" -Port $ForumCommentPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-messaging.ps1" -Port $ForumMessagingPort -SkipWait:$serviceSkipWait -DryRun:$DryRun

& "$PSScriptRoot\run-parteneriat-partnership.ps1" -Port $PartnershipPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-parteneriat-job-offers.ps1" -Port $JobOffersPort -SkipWait:$serviceSkipWait -DryRun:$DryRun

if ($WithParteneriatFrontend) {
    & "$PSScriptRoot\run-parteneriat-react.ps1" -Port $ParteneriatReactPort -SkipWait:$serviceSkipWait -DryRun:$DryRun
}

if (-not $DryRun) {
    Start-Sleep -Seconds 8
    Write-PortSummary -Services $servicePorts
}
