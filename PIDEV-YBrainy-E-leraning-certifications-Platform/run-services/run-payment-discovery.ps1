param([int]$Port = 8761, [switch]$SkipWait, [switch]$DryRun)

& "$PSScriptRoot\run-eureka.ps1" -Port $Port -SkipWait:$SkipWait -DryRun:$DryRun
