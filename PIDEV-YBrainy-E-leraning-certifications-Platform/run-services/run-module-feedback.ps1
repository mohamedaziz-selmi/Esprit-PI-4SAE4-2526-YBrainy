param(
    [int]$EurekaPort = 8761,
    [int]$FeedbackPort = 9004,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-feedback-service.ps1" -Port $FeedbackPort -SkipWait:$SkipWait -DryRun:$DryRun
