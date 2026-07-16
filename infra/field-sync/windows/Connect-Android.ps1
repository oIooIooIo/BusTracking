[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found. Install Android Platform Tools and add it to PATH."
}
if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile."
}

$DeviceLines = @(adb devices | Select-String "`tdevice$")
if ($DeviceLines.Count -ne 1) {
    adb devices | Out-Host
    throw "Expected exactly one authorized Android device, found $($DeviceLines.Count)."
}

adb reverse tcp:8080 tcp:8080 | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "adb reverse failed."
}

$Serial = (adb shell cat /sys/devices/soc0/serial_number).Trim().ToUpperInvariant()
if (-not $Serial) {
    throw "Could not read the 618K hardware serial."
}

$Settings = @{}
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $Settings[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}
if (-not $Settings['DEVICE_API_KEY']) {
    throw "DEVICE_API_KEY is missing from $EnvFile."
}

$Headers = @{
    Authorization = "Bearer $($Settings['DEVICE_API_KEY'])"
    "X-Device-Hardware-Serial" = $Serial
}
$Snapshot = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/device/v1/permissions" `
    -Headers $Headers -TimeoutSec 10

Write-Host "USB reverse is active: Android tcp:8080 -> laptop tcp:8080"
Write-Host "Device serial: $Serial"
Write-Host "Bus: $($Snapshot.bus.code) - $($Snapshot.bus.name)"
Write-Host "Permission version: $($Snapshot.version); employees: $($Snapshot.employees.Count)"
Write-Host "Open or restart Bus Tracking on Android to trigger synchronization."
