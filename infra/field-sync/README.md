# Windows Laptop Field Synchronization

This procedure reuses the verified USB transport:

```text
Android http://127.0.0.1:8080
        -> adb reverse tcp:8080 tcp:8080
        -> Windows laptop field backend
```

The laptop is a temporary field server. It receives GPS points and boarding
events from Android and serves the permission snapshot copied from the desktop.
It must not be used to edit buses, devices, employees, or permissions.

## Requirements

On the Windows laptop install:

- Docker Desktop with Linux containers and Docker Compose
- Android SDK Platform Tools (`adb` available in `PATH`)
- Git, or another way to transfer this repository and the snapshot

The first Docker build needs internet access to download container images and
Maven dependencies. Build and test the stack before leaving the office.

## 1. Create a snapshot on the desktop

Make sure the desktop PostgreSQL container is running, then from the repository
root run:

```bash
chmod +x infra/field-sync/desktop/*.sh
./infra/field-sync/desktop/create-field-snapshot.sh
```

Copy the generated `.dump` file and this repository to the Windows laptop.

## 2. Configure and restore on Windows

From PowerShell:

```powershell
cd infra\field-sync
Copy-Item .env.example .env
notepad .env
```

`DEVICE_API_KEY` must match the key compiled into the USB APK. Do not commit
`.env`.

Restore the snapshot before the field visit:

```powershell
.\windows\Import-Snapshot.ps1 -SnapshotPath C:\path\field-snapshot.dump
.\windows\Start-FieldSync.ps1
```

## 3. Connect the 618K Android device

The installed USB APK must use:

```text
http://127.0.0.1:8080/api/device/v1/
```

and allow cleartext HTTP for this field build. Connect exactly one authorized
device and run:

```powershell
.\windows\Connect-Android.ps1
```

The script verifies the ADB connection, creates the reverse tunnel, reads the
618K hardware serial, and downloads its permission snapshot. Open or restart
Bus Tracking to trigger WorkManager synchronization.

Before disconnecting USB, confirm the script displayed the correct bus and
permission version. Keep the laptop powered until Android synchronization has
finished.

## 4. Export after the visit

While the field containers are still running:

```powershell
.\windows\Export-FieldData.ps1 -OutputDirectory C:\BusTracking\field-result
.\windows\Stop-FieldSync.ps1
```

The bundle contains only rows received after the field session started:

- `field-gps.csv`
- `field-boarding-events.csv`
- `session-started-at.txt`

Do not delete the laptop Docker volume until the desktop import is verified.

## 5. Import on the desktop

Transfer the complete result directory back to the desktop, then run:

```bash
./infra/field-sync/desktop/import-field-data.sh /path/to/field-result
```

The import runs in one transaction. GPS uses `(device_id, sequence_no)` and
boarding events use `id` for conflict handling, so repeated imports skip rows
already present. It does not update master data or overwrite desktop records.

## USB APK build

Build the field APK on the same trusted build computer used for the currently
installed APK so its signing key remains consistent:

```bash
cd apps/android-bus
./gradlew :app:assembleDebug \
  -PapiBaseUrl=http://127.0.0.1:8080/api/device/v1/ \
  -PusesCleartextTraffic=true \
  -PdeviceApiKey=<same-value-as-field-env>
```

Installing a debug APK built on a different computer may fail because Android
debug signing keys differ. Preserve the signing key or install the field APK
before leaving the office.
