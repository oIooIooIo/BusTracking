[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "compose.yaml"
$EnvFile = Join-Path $Root ".env"
$SessionFile = Join-Path $Root ".field-session-start.txt"

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy .env.example to .env and configure it first."
}

docker compose --env-file $EnvFile -f $ComposeFile up -d --build | Out-Host

Write-Host "Waiting for the field API..."
$Healthy = $false
for ($Attempt = 0; $Attempt -lt 60; $Attempt++) {
    try {
        $Health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 2
        if ($Health.status -eq "UP") {
            $Healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $Healthy) {
    throw "Field API did not become healthy. Check the backend container logs."
}

if (-not (Test-Path $SessionFile)) {
    [DateTime]::UtcNow.ToString("o") | Set-Content -Path $SessionFile -Encoding ascii
}

Write-Host "Field API is ready at http://127.0.0.1:8080"
Write-Host "Session cutoff: $(Get-Content $SessionFile)"
Write-Host "Next: connect USB and run Connect-Android.ps1"
