[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SnapshotPath
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "compose.yaml"
$EnvFile = Join-Path $Root ".env"

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy .env.example to .env and configure it first."
}
if (-not (Test-Path $SnapshotPath)) {
    throw "Snapshot not found: $SnapshotPath"
}

docker compose --env-file $EnvFile -f $ComposeFile stop backend | Out-Host
docker compose --env-file $EnvFile -f $ComposeFile up -d postgres redis | Out-Host

$PostgresId = (docker compose --env-file $EnvFile -f $ComposeFile ps -q postgres).Trim()
if (-not $PostgresId) {
    throw "Field PostgreSQL container is not running."
}

Write-Host "Waiting for field PostgreSQL..."
$Ready = $false
for ($Attempt = 0; $Attempt -lt 30; $Attempt++) {
    docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
        pg_isready -U bus_tracking -d bus_tracking *> $null
    if ($LASTEXITCODE -eq 0) {
        $Ready = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $Ready) {
    throw "Field PostgreSQL did not become ready."
}

docker cp (Resolve-Path $SnapshotPath) "${PostgresId}:/tmp/field-snapshot.dump" | Out-Host
docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
    pg_restore -U bus_tracking -d bus_tracking --clean --if-exists --no-owner --no-acl `
    /tmp/field-snapshot.dump | Out-Host

if ($LASTEXITCODE -ne 0) {
    throw "Snapshot restore failed."
}

Write-Host "Snapshot restored. Run Start-FieldSync.ps1 before connecting Android."
