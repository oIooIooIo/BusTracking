# Android Bus App

Android 13 Demo application installed on a bus device.

Implemented functions:

- Foreground GPS tracking with a minimum 10-second interval
- Room-backed offline GPS and boarding-event queues
- WorkManager batch synchronization and exponential retry
- Automatic synchronization on app startup, device boot, APK update, network
  recovery, new GPS records, new NFC events, and a 15-minute fallback schedule
- NFC CardSN reading and offline permission checks
- Permission snapshot synchronization
- Boot receiver that restarts tracking after the user has granted permissions
- 30-day local GPS retention

## Build

Install Android Studio and Android SDK 36, then:

```bash
cd apps/android-bus
./gradlew :app:assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug`.

## Demo Configuration

The default demo APK uses:

- API base URL: `https://taxiportal-dev.fushan.fihnbb.com/api/device/v1/`
- Shared device API key: `demo-device-key`
- Cleartext HTTP traffic: disabled

For a different HTTPS endpoint, pass the target device API URL when building:

```bash
./gradlew :app:assembleDebug \
  -PapiBaseUrl=https://bus.example.internal/api/device/v1/
```

For local emulator development against a local backend, pass the local device
API URL explicitly and enable cleartext traffic only for that local build:

```bash
./gradlew :app:assembleDebug \
  -PapiBaseUrl=http://<local-device-api-url>/ \
  -PusesCleartextTraffic=true
```

Every API request includes the shared API key and
`X-Device-Hardware-Serial`. The app reads the 618K SoC serial from
`/sys/devices/soc0/serial_number` and sends an identifier such as
`QCM2290-CF8F718B`. The backend maps that unique value to a Device and Bus, so
all units install the same APK.

On first launch, grant precise location while using the app and notification
permission. Boot auto-start works only after this initial setup. Some device
vendors may also require disabling battery optimization or enabling their
auto-start setting.

NFC CardSN is converted to uppercase hexadecimal. The seeded allowed card is
`04A1B2C3D4`.

The hardware serial is an identifier, not a secret. The shared API key must be
provided through deployment configuration in production.
