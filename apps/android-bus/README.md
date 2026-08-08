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

Install Android Studio and Android SDK 36. From the repository root, use the
approved LOCAL environment runner:

```bash
./scripts/environment/build-mobile.sh local
```

The debug APK is generated under `apps/android-bus/app/build/outputs/apk/debug`.

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

Example using an approved LOCAL runtime file:

```bash
./scripts/environment/build-mobile.sh local .env.local
```

## LOCAL USB Device

LOCAL uses a physical Android device connected through USB. After building,
install or update the APK and create the fixed ADB reverse tunnel:

```bash
adb install -r apps/android-bus/app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8080 tcp:8080
```

The approved LOCAL APK endpoint is
`http://127.0.0.1:8080/api/device/v1/`. Keep ADB reverse active while testing
or synchronizing against the native LOCAL Backend. Routine LOCAL work must not
replace this endpoint with a LAN, DEV, or PROD URL.

DEV and PROD APKs connect to their separately approved DNS endpoints. They do
not use the LOCAL USB tunnel for server communication.

Every API request includes the shared API key and
`X-Device-Hardware-Serial`. The Android app must be provisioned as the Device
Owner of a company-owned Dedicated Device. It configures the fixed Organization
ID `Fushan`, reads Android's enrollment-specific ID, hashes it with SHA-256, and
sends `ANDROID-ESID-<64 uppercase hexadecimal characters>`. The enrollment-
specific ID remains stable for the same physical device, Organization ID, and
managing app across factory reset and re-enrollment. The app deliberately does
not use `ANDROID_ID` or a generated fallback UUID. For legacy Cardlan 618K
units, the app retains the existing `QCM2290-<SoC serial>` identity only when
the vendor SoC serial file can actually be read. Any other device waits for
Device Owner enrollment instead of sending a temporary identity. The backend
maps the resulting identifier to a Device and Bus, so old Cardlan units and new
Dedicated Device tablets can install the same APK.

On first launch, grant precise location while using the app and notification
permission. Boot auto-start works only after this initial setup. Some device
vendors may also require disabling battery optimization or enabling their
auto-start setting.

NFC CardSN is converted to uppercase hexadecimal. The seeded allowed card is
`04A1B2C3D4`.

The device identifier is not a secret. The shared API key must be
provided through deployment configuration in production.
