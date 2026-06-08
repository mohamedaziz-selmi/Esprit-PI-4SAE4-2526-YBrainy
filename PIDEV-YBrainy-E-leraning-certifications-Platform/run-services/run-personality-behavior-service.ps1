param([int]$Port = 8084, [switch]$SkipWait, [switch]$DryRun)

$envVars = @{
    PERSONALITY_BEHAVIOR_SERVICE_PORT = "$Port"
    SERVER_PORT = "$Port"
    EUREKA_ENABLED = if ($env:YBRAINY_EUREKA_URL) { "true" } else { "false" }
    JAVA_TOOL_OPTIONS = if ($env:JAVA_TOOL_OPTIONS) { $env:JAVA_TOOL_OPTIONS } else { "-Xms32m -Xmx192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC" }
    MAVEN_OPTS = if ($env:MAVEN_OPTS) { $env:MAVEN_OPTS } else { "-Xms32m -Xmx192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC" }
}

& "$PSScriptRoot\\_run-service.ps1" `
    -Name "Personality Behavior Service" `
    -ProjectPath "personality-behavior-service" `
    -Kind Maven `
    -Port $Port `
    -WaitUrl "http://localhost:$Port/actuator/health" `
    -MavenRepoGroup "personality-behavior" `
    -EnvVars $envVars `
    -SkipWait:$SkipWait `
    -DryRun:$DryRun

