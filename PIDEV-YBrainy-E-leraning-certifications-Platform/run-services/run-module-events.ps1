param(
    [int]$EurekaPort = 8761,
    [int]$EventPort = 9001,
    [int]$InscriptionPort = 9002,
    [int]$UserPort = 9003,
    [int]$FeedbackPort = 9004,
    [switch]$SkipFeedback,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-event-service.ps1" -Port $EventPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-events-user-service.ps1" -Port $UserPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-inscription-service.ps1" -Port $InscriptionPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipFeedback) {
    & "$PSScriptRoot\run-feedback-service.ps1" -Port $FeedbackPort -SkipWait:$SkipWait -DryRun:$DryRun
}
