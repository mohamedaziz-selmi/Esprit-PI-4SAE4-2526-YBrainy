$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8899'
$script:AuthHeaders = @{}

function PostJson([string]$url, $body) {
  Write-Host ('POST ' + $url)
  $json = $body | ConvertTo-Json -Depth 10
  try {
    $invokeParams = @{
      Method = 'Post'
      Uri = $url
      ContentType = 'application/json'
      Body = $json
    }
    if ($script:AuthHeaders.Count -gt 0) { $invokeParams.Headers = $script:AuthHeaders }
    $res = Invoke-RestMethod @invokeParams
    $res | ConvertTo-Json -Depth 10
    return $res
  } catch {
    Write-Host $_.Exception.Message
    if ($_.Exception.Response -and $_.Exception.Response.GetResponseStream()) {
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $bodyText = $sr.ReadToEnd()
        if ($bodyText) { Write-Host $bodyText }
      } catch {
        Write-Host 'Could not read error response body.'
      }
    }
    return $null
  }
}

function PutJson([string]$url, $body) {
  Write-Host ('PUT ' + $url)
  $json = $body | ConvertTo-Json -Depth 10
  try {
    $invokeParams = @{
      Method = 'Put'
      Uri = $url
      ContentType = 'application/json'
      Body = $json
    }
    if ($script:AuthHeaders.Count -gt 0) { $invokeParams.Headers = $script:AuthHeaders }
    $res = Invoke-RestMethod @invokeParams
    $res | ConvertTo-Json -Depth 10
    return $res
  } catch {
    Write-Host $_.Exception.Message
    if ($_.Exception.Response -and $_.Exception.Response.GetResponseStream()) {
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $bodyText = $sr.ReadToEnd()
        if ($bodyText) { Write-Host $bodyText }
      } catch {
        Write-Host 'Could not read error response body.'
      }
    }
    return $null
  }
}

function GetJson([string]$url) {
  Write-Host ('GET ' + $url)
  try {
    $invokeParams = @{
      Method = 'Get'
      Uri = $url
    }
    if ($script:AuthHeaders.Count -gt 0) { $invokeParams.Headers = $script:AuthHeaders }
    $res = Invoke-RestMethod @invokeParams
    $res | ConvertTo-Json -Depth 10
    return $res
  } catch {
    Write-Host $_.Exception.Message
    if ($_.Exception.Response -and $_.Exception.Response.GetResponseStream()) {
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $bodyText = $sr.ReadToEnd()
        if ($bodyText) { Write-Host $bodyText }
      } catch {
        Write-Host 'Could not read error response body.'
      }
    }
    return $null
  }
}

function AssertNotNull($value, [string]$stepName) {
  if ($null -eq $value) {
    throw "Stopping: step failed -> $stepName"
  }
}

function SetBearerToken([string]$accessToken) {
  if ([string]::IsNullOrWhiteSpace($accessToken)) {
    $script:AuthHeaders = @{}
    return
  }
  $script:AuthHeaders = @{ Authorization = ('Bearer ' + $accessToken) }
}

function GetSignUpChallenge([int]$age, [string]$country, [string]$preferredMode = 'jigsaw') {
  $challenge = PostJson "$base/api/auth/signup/challenge" @{
    age = $age
    country = $country
    preferredMode = $preferredMode
  }
  AssertNotNull $challenge "create signup challenge ($country/$preferredMode)"
  return $challenge
}

function ResolveSignUpChallengeAnswer($challenge) {
  if ($null -eq $challenge) {
    throw 'Signup challenge was not returned.'
  }

  if ($challenge.challengeKind -eq 'flag_jigsaw' -and $null -ne $challenge.flagJigsaw -and $null -ne $challenge.flagJigsaw.pieces) {
    $orderedPieces = @($challenge.flagJigsaw.pieces | Sort-Object -Property correctIndex)
    return (($orderedPieces | ForEach-Object { [string]$_.id }) -join '|')
  }

  throw "Unsupported automated signup challenge kind: $($challenge.challengeKind)."
}

function NewSignUpBody([hashtable]$body, [int]$age, [string]$country, [string]$preferredMode = 'jigsaw') {
  $challenge = GetSignUpChallenge -age $age -country $country -preferredMode $preferredMode
  $body.age = $age
  $body.country = $country
  $body.challengeToken = [string]$challenge.token
  $body.challengeMode = [string]$challenge.selectedMode
  $body.challengeAnswer = ResolveSignUpChallengeAnswer $challenge
  return $body
}

function DeleteRequest([string]$url) {
  Write-Host ('DELETE ' + $url)
  $invokeParams = @{
    Method = 'Delete'
    Uri = $url
  }
  if ($script:AuthHeaders.Count -gt 0) { $invokeParams.Headers = $script:AuthHeaders }
  Invoke-RestMethod @invokeParams | Out-Null
  Write-Host '204 No Content'
}

$ts = [int][double]::Parse((Get-Date -UFormat %s))
$adminUsername = "admin_$ts"
$adminEmail = "admin_$ts@example.com"
$username1 = "user1_$ts"
$username2 = "enterprise1_$ts"
$email1 = "user1_$ts@example.com"
$email2 = "enterprise1_$ts@example.com"
$sharedPassword = 'Password123'
$signupCountry = 'Tunisia'

$adminUser = PostJson "$base/api/auth/signup" (NewSignUpBody @{
  username = $adminUsername
  firstName = 'Admin'
  lastName = 'Tester'
  email = $adminEmail
  password = $sharedPassword
  confirmPassword = $sharedPassword
  role = 'ADMIN'
  address = 'Ops Lane'
  city = 'Tunis'
  sex = 'F'
} 28 $signupCountry)
AssertNotNull $adminUser 'signup admin user'
$adminId = $adminUser.userId

$user1 = PostJson "$base/api/auth/signup" (NewSignUpBody @{
  username = $username1
  firstName = 'Yassin'
  lastName = 'Test'
  email = $email1
  password = $sharedPassword
  confirmPassword = $sharedPassword
  role = 'STUDENT'
  address = '123 Main St'
  city = 'Tunis'
  sex = 'M'
} 25 $signupCountry)
AssertNotNull $user1 'signup user1'
$u1Id = $user1.userId

$user2 = PostJson "$base/api/auth/signup" (NewSignUpBody @{
  username = $username2
  firstName = 'Sara'
  lastName = 'Company'
  email = $email2
  password = $sharedPassword
  confirmPassword = $sharedPassword
  role = 'ENTERPRISE_USER'
  address = 'HQ Street'
  city = 'Tunis'
  sex = 'F'
  companyName = 'Acme Corp'
  enterpriseCompanyId = 'Acme Corp'
} 30 $signupCountry)
AssertNotNull $user2 'signup enterprise user'
$u2Id = $user2.userId

$userSignIn = PostJson "$base/api/auth/signin" @{ email=$email1; password=$sharedPassword }
AssertNotNull $userSignIn 'signin user1'
if ([string]::IsNullOrWhiteSpace([string]$userSignIn.accessToken)) {
  throw 'Stopping: step failed -> signin user1 (no accessToken returned)'
}
$adminSignIn = PostJson "$base/api/auth/signin" @{ email=$adminEmail; password=$sharedPassword }
AssertNotNull $adminSignIn 'signin admin user'
if ([string]::IsNullOrWhiteSpace([string]$adminSignIn.accessToken)) {
  throw 'Stopping: step failed -> signin admin user (no accessToken returned)'
}

SetBearerToken ([string]$adminSignIn.accessToken)
GetJson "$base/api/users" | Out-Null

SetBearerToken ([string]$userSignIn.accessToken)
GetJson "$base/api/users/$u1Id" | Out-Null

SetBearerToken ([string]$adminSignIn.accessToken)
GetJson "$base/api/users/username/$username1" | Out-Null
GetJson ("$base/api/users/email/" + [uri]::EscapeDataString($email1)) | Out-Null

SetBearerToken ([string]$adminSignIn.accessToken)
$behavior = PostJson "$base/api/behaviors" @{ agitationLevelPct=10; focusScorePct=90; engagementIndexPct=75; learningPacePercentile=50; fraudProbabilityScore=0.1 }
AssertNotNull $behavior 'create behavior'
$bId = $behavior.behaviorId
GetJson "$base/api/behaviors/$bId" | Out-Null
GetJson "$base/api/behaviors" | Out-Null
PutJson "$base/api/behaviors/$bId" @{ agitationLevelPct=15; focusScorePct=85; engagementIndexPct=70; learningPacePercentile=55; fraudProbabilityScore=0.05 } | Out-Null

$personality = PostJson "$base/api/personalities" @{ visualLearningPct=40; auditoryLearningPct=30; kinestheticLearningPct=30; careerAlignmentScore=0.8; cognitiveLoadTolerance=0.6; careerGoals=@('Dev','Data'); behavior=@{ agitationLevelPct=5; focusScorePct=95; engagementIndexPct=80; learningPacePercentile=60; fraudProbabilityScore=0.02 } }
AssertNotNull $personality 'create personality'
$personalityId = $personality.personalityId
GetJson "$base/api/personalities/$personalityId" | Out-Null
GetJson "$base/api/personalities" | Out-Null
PutJson "$base/api/personalities/$personalityId" @{ visualLearningPct=45; auditoryLearningPct=25; kinestheticLearningPct=30; careerAlignmentScore=0.85; cognitiveLoadTolerance=0.65; careerGoals=@('DevOps'); behavior=@{ agitationLevelPct=6; focusScorePct=94; engagementIndexPct=82; learningPacePercentile=62; fraudProbabilityScore=0.03 } } | Out-Null

$warn = PostJson "$base/api/warnings" @{ reason='Test warning'; severity='LOW'; issuedBy='system'; userId=$u1Id }
AssertNotNull $warn 'create warning'
$wId = $warn.warningId
GetJson "$base/api/warnings/$wId" | Out-Null
GetJson "$base/api/warnings" | Out-Null

SetBearerToken ([string]$userSignIn.accessToken)
GetJson "$base/api/warnings/user/$u1Id" | Out-Null
GetJson "$base/api/warnings/user/$u1Id/count" | Out-Null

SetBearerToken ([string]$adminSignIn.accessToken)
$banResult = PutJson "$base/api/users/$u1Id/ban" @{ banned=$true; reasonForBan='Automated moderation test'; banPeriodDays=7 }
AssertNotNull $banResult 'ban user before appeal'

SetBearerToken ([string]$userSignIn.accessToken)
$appeal = PostJson "$base/api/ban-appeals" @{ description='Please unban me' }
AssertNotNull $appeal 'submit ban appeal'
$aId = $appeal.appealId
GetJson "$base/api/ban-appeals/$aId" | Out-Null
GetJson "$base/api/ban-appeals/user/$u1Id" | Out-Null

SetBearerToken ([string]$adminSignIn.accessToken)
GetJson "$base/api/ban-appeals" | Out-Null
GetJson "$base/api/ban-appeals/status/PENDING" | Out-Null
PostJson ("$base/api/ban-appeals/$aId/approve") @{} | Out-Null

DeleteRequest "$base/api/warnings/$wId"
DeleteRequest "$base/api/ban-appeals/$aId"
DeleteRequest "$base/api/behaviors/$bId"
DeleteRequest "$base/api/personalities/$personalityId"
DeleteRequest "$base/api/users/$u2Id"
DeleteRequest "$base/api/users/$u1Id"
DeleteRequest "$base/api/users/$adminId"

Write-Host 'DONE'
