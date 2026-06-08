param(
    [int]$EurekaPort = 8761,
    [switch]$SkipFrontends,
    [switch]$SkipMl,
    [switch]$SkipPredict,
    [switch]$SkipWait,
    [switch]$DryRun
)

& "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-user.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipFrontend:$SkipFrontends -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-forum.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipPredict:$SkipPredict -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-parteneriat.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipFrontend:$SkipFrontends -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-payment.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipFrontend:$SkipFrontends -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-courses.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipMl:$SkipMl -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-module-events.ps1" -EurekaPort $EurekaPort -SkipEureka -SkipWait:$SkipWait -DryRun:$DryRun
