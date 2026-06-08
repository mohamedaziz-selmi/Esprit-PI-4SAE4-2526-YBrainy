param(
    [string]$KeycloakBaseUrl = $(if ($env:KEYCLOAK_BASE_URL) { $env:KEYCLOAK_BASE_URL } else { "http://localhost:9190" }),
    [string]$AdminRealm = $(if ($env:KEYCLOAK_ADMIN_REALM) { $env:KEYCLOAK_ADMIN_REALM } else { "master" }),
    [string]$AdminUsername = $(if ($env:KEYCLOAK_ADMIN_USERNAME) { $env:KEYCLOAK_ADMIN_USERNAME } else { "admin" }),
    [string]$AdminPassword = $(if ($env:KEYCLOAK_ADMIN_PASSWORD) { $env:KEYCLOAK_ADMIN_PASSWORD } else { "admin" }),
    [string]$Realm = $(if ($env:KEYCLOAK_REALM) { $env:KEYCLOAK_REALM } else { "microservices" }),
    [string]$Alias = $(if ($env:KEYCLOAK_GOOGLE_ALIAS) { $env:KEYCLOAK_GOOGLE_ALIAS } else { "google" }),
    [string]$GoogleClientId = $env:KEYCLOAK_GOOGLE_CLIENT_ID,
    [string]$GoogleClientSecret = $env:KEYCLOAK_GOOGLE_CLIENT_SECRET
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $GoogleClientId -or -not $GoogleClientSecret) {
    throw "KEYCLOAK_GOOGLE_CLIENT_ID and KEYCLOAK_GOOGLE_CLIENT_SECRET must be set to configure the Google identity provider."
}

$tokenResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$KeycloakBaseUrl/realms/$AdminRealm/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body "client_id=admin-cli&username=$([uri]::EscapeDataString($AdminUsername))&password=$([uri]::EscapeDataString($AdminPassword))&grant_type=password"

$headers = @{
    Authorization = "Bearer $($tokenResponse.access_token)"
}

$instancesUrl = "$KeycloakBaseUrl/admin/realms/$Realm/identity-provider/instances"
$existingProviders = @(Invoke-RestMethod -Method Get -Uri $instancesUrl -Headers $headers)
$existingProvider = $existingProviders | Where-Object { $_.alias -eq $Alias } | Select-Object -First 1

if ($existingProvider -and $existingProvider.providerId -ne "google") {
    throw "Identity provider alias '$Alias' already exists with providerId '$($existingProvider.providerId)'."
}

$payload = @{
    alias = $Alias
    providerId = "google"
    displayName = "Google"
    enabled = $true
    trustEmail = $true
    storeToken = $false
    addReadTokenRoleOnCreate = $false
    authenticateByDefault = $false
    linkOnly = $false
    hideOnLogin = $false
    firstBrokerLoginFlowAlias = "first broker login"
    config = @{
        clientId = $GoogleClientId
        clientSecret = $GoogleClientSecret
        useJwksUrl = "true"
        syncMode = "IMPORT"
    }
}

$jsonPayload = $payload | ConvertTo-Json -Depth 8
$jsonHeaders = @{
    Authorization = "Bearer $($tokenResponse.access_token)"
    "Content-Type" = "application/json"
}

if ($existingProvider) {
    Invoke-RestMethod -Method Put -Uri "$instancesUrl/$Alias" -Headers $jsonHeaders -Body $jsonPayload | Out-Null
    Write-Host "Updated Google identity provider '$Alias' in realm '$Realm'."
} else {
    Invoke-RestMethod -Method Post -Uri $instancesUrl -Headers $jsonHeaders -Body $jsonPayload | Out-Null
    Write-Host "Created Google identity provider '$Alias' in realm '$Realm'."
}
