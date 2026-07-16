# Field Sync Runbook for This Desktop

This computer's active backend uses `bus-local-postgres`. Use the commands in
this runbook so the snapshot and returned field data target the active database.

## Prepared files

The ready-to-transfer package is:

```text
offline-package/field-sync/field-sync-kit.zip
```

It already contains:

- the current database snapshot;
- the USB Android APK built on this desktop;
- the backend source and Windows field scripts.

## Windows laptop setup

Extract the ZIP to `C:\BusTrackingField`, open PowerShell in that directory,
then configure the field environment:

```powershell
Copy-Item .\infra\field-sync\.env.example .\infra\field-sync\.env
notepad .\infra\field-sync\.env
```

Keep `POSTGRES_DB=bus_tracking` and `POSTGRES_USER=bus_tracking`. Set
`DEVICE_API_KEY=device-local-test-key` so it matches the prepared USB APK.
Choose local-only PostgreSQL and admin passwords.

Restore and start the field server:

```powershell
.\infra\field-sync\windows\Import-Snapshot.ps1 `
  -SnapshotPath .\offline-package\field-sync\field-snapshot-20260716.dump
.\infra\field-sync\windows\Start-FieldSync.ps1
```

Connect the 618K device, authorize USB debugging, then install the prepared APK
and establish the HTTP tunnel:

```powershell
adb install -r .\offline-package\field-sync\bus-tracking-usb-debug.apk
.\infra\field-sync\windows\Connect-Android.ps1
```

Open Bus Tracking on Android and allow it to synchronize before unplugging USB.

## Export on Windows after the visit

```powershell
.\infra\field-sync\windows\Export-FieldData.ps1 `
  -OutputDirectory C:\BusTrackingField\field-result
.\infra\field-sync\windows\Stop-FieldSync.ps1
```

Transfer the complete `field-result` directory back to this desktop. Keep the
Windows Docker volume until the desktop import has been verified.

## Import into this desktop

From the repository root:

```bash
POSTGRES_CONTAINER=bus-local-postgres \
  ./infra/field-sync/desktop/import-field-data.sh /path/to/field-result
```

The importer adds only GPS points and boarding events. Repeated imports are
safe because existing GPS sequence keys and boarding-event UUIDs are skipped.
