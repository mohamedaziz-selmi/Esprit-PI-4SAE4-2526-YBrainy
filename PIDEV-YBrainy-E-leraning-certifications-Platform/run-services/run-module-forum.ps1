param(
    [int]$EurekaPort = 8761,
    [int]$UserPort = 8191,
    [int]$CategoryPort = 8082,
    [int]$ThreadPort = 8083,
    [int]$PostPort = 8084,
    [int]$CommentPort = 8085,
    [int]$MessagingPort = 8086,
    [int]$PredictPort = 5001,
    [switch]$StartForumUserService,
    [switch]$SkipPredict,
    [switch]$SkipEureka,
    [switch]$SkipWait,
    [switch]$DryRun
)

$env:ML_PREDICT_URL = "http://localhost:$PredictPort/predict"

if ($SkipEureka) {
    $env:YBRAINY_EUREKA_URL = "http://localhost:$EurekaPort/eureka/"
} else {
    & "$PSScriptRoot\run-eureka.ps1" -Port $EurekaPort -SkipWait:$SkipWait -DryRun:$DryRun
}

if (-not $SkipPredict) {
    & "$PSScriptRoot\run-forum-predict.ps1" -Port $PredictPort -SkipWait:$SkipWait -DryRun:$DryRun
}

if ($StartForumUserService) {
    & "$PSScriptRoot\run-forum-user.ps1" -Port $UserPort -SkipWait:$SkipWait -DryRun:$DryRun
}
& "$PSScriptRoot\run-forum-category.ps1" -Port $CategoryPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-thread.ps1" -Port $ThreadPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-post.ps1" -Port $PostPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-comment.ps1" -Port $CommentPort -SkipWait:$SkipWait -DryRun:$DryRun
& "$PSScriptRoot\run-forum-messaging.ps1" -Port $MessagingPort -SkipWait:$SkipWait -DryRun:$DryRun
