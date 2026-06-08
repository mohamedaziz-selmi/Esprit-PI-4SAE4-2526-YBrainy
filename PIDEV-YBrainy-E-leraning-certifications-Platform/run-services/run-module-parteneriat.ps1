param(
    [int]$EurekaPort = 8761,
    [int]$PartnershipPort = 8181,
    [Alias("JobOfferPort")]
    [int]$JobOffersPort = 8182,
    [int]$ReactPort = 5173,
    [switch]$SkipFrontend,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-parteneriat-partnership.ps1" -Port $PartnershipPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-parteneriat-job-offers.ps1" -Port $JobOffersPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipFrontend) {
    & "$PSScriptRoot\run-parteneriat-react.ps1" -Port $ReactPort -SkipWait:$SkipWait -DryRun:$DryRun
}
