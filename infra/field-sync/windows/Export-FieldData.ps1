[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Get-Location) ("field-data-" + (Get-Date -Format "yyyyMMdd-HHmmss")))
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root "compose.yaml"
$EnvFile = Join-Path $Root ".env"
$SessionFile = Join-Path $Root ".field-session-start.txt"

if (-not (Test-Path $SessionFile)) {
    throw "No field session cutoff was found. Start the field stack before exporting."
}
$StartedAt = (Get-Content $SessionFile -Raw).Trim()
if ($StartedAt -notmatch '^[0-9T:.+Z-]+$') {
    throw "Invalid field session cutoff: $StartedAt"
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$PostgresId = (docker compose --env-file $EnvFile -f $ComposeFile ps -q postgres).Trim()
if (-not $PostgresId) {
    throw "Field PostgreSQL container is not running."
}

$GpsSql = "\copy (SELECT device_id, sequence_no, bus_id, recorded_at, ST_X(position::geometry) AS longitude, ST_Y(position::geometry) AS latitude, accuracy_meters, received_at FROM gps_point WHERE received_at >= '$StartedAt'::timestamptz ORDER BY received_at, device_id, sequence_no) TO '/tmp/field-gps.csv' WITH (FORMAT csv, HEADER true)"
$EventSql = "\copy (SELECT id, bus_id, device_id, employee_id, card_sn, result, scanned_at, permission_version, received_at FROM boarding_event WHERE received_at >= '$StartedAt'::timestamptz ORDER BY received_at, id) TO '/tmp/field-boarding-events.csv' WITH (FORMAT csv, HEADER true)"

docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
    psql -U bus_tracking -d bus_tracking -v ON_ERROR_STOP=1 -c $GpsSql | Out-Host
if ($LASTEXITCODE -ne 0) { throw "GPS export failed." }

docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
    psql -U bus_tracking -d bus_tracking -v ON_ERROR_STOP=1 -c $EventSql | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Boarding-event export failed." }

docker cp "${PostgresId}:/tmp/field-gps.csv" (Join-Path $OutputDirectory "field-gps.csv") | Out-Host
docker cp "${PostgresId}:/tmp/field-boarding-events.csv" `
    (Join-Path $OutputDirectory "field-boarding-events.csv") | Out-Host
$StartedAt | Set-Content -Path (Join-Path $OutputDirectory "session-started-at.txt") -Encoding ascii

Remove-Item $SessionFile
Write-Host "Field data bundle created: $OutputDirectory"
Write-Host "Keep this folder together and import it on the desktop."
