param(
    [int]$EurekaPort = 8761,
    [int]$CoursePort = 8172,
    [int]$QuizPort = 8173,
    [int]$MlPort = 5000,
    [int]$GazePort = 5002,
    [int]$TalkingHeadPort = 8765,
    [int]$GatewayPort = 8170,
    [switch]$SkipMl,
    [switch]$SkipAiModels,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$env:AI_GAZE_URL = "http://localhost:$GazePort"
$env:AI_TALKING_HEAD_URL = "http://localhost:$TalkingHeadPort"

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-courses-service.ps1" -Port $CoursePort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-courses-quiz.ps1" -Port $QuizPort -SkipWait:$SkipWait -DryRun:$DryRun

if (-not $SkipMl) {
    & "$PSScriptRoot\run-courses-ml.ps1" -Port $MlPort -SkipWait:$SkipWait -DryRun:$DryRun
}

if (-not $SkipAiModels) {
    & "$PSScriptRoot\run-ai-gaze.ps1" -Port $GazePort -SkipWait:$SkipWait -DryRun:$DryRun
    & "$PSScriptRoot\run-ai-talking-head.ps1" -Port $TalkingHeadPort -SkipWait:$SkipWait -DryRun:$DryRun
}

& "$PSScriptRoot\run-courses-gateway.ps1" -Port $GatewayPort -SkipWait:$SkipWait -DryRun:$DryRun
