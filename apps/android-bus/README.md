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
./scripts/environment/build-mobile.sh local
```

The debug APK is generated under `app/build/outputs/apk/debug`.

## Environment Configuration

Android has no implicit LOCAL, DEV, or PROD endpoint. It requires values from
the approved environment template before Gradle configuration. The authoritative
instructions are in `docs/environment-configuration.md`.

The build reads:

- `MOBILE_API_BASE_URL`
- `MOBILE_USES_CLEARTEXT`
- `DEVICE_API_KEY`

Gradle properties with the legacy names `apiBaseUrl`, `usesCleartextTraffic`,
and `deviceApiKey` may be passed explicitly, but their values must match the
approved environment configuration.

Example using an approved runtime file:

```bash
./scripts/environment/build-mobile.sh local .env.local
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
