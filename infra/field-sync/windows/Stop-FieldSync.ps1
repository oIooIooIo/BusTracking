[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "compose.yaml"
$EnvFile = Join-Path $Root ".env"

if (Get-Command adb -ErrorAction SilentlyContinue) {
    adb reverse --remove tcp:8080 2>$null
}
docker compose --env-file $EnvFile -f $ComposeFile stop | Out-Host
Write-Host "USB reverse removed and field containers stopped. Data volume was preserved."
