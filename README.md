# Bus Tracking System

Environment values and the mandatory approval policy are documented in
[`docs/environment-configuration.md`](docs/environment-configuration.md). Use
the protected templates under `config/environments/`; do not infer or change
environment values during routine start, build, or deployment work.

Bus tracking Demo implemented as a monorepo. Current implementation work must
follow the Flyway migrations, implemented APIs, and current approved behavior;
the original proposed data contract is retained only as a partially superseded baseline.

## Components

- `apps/android-bus`: Android 13 bus application with GPS, NFC, Room, and
  WorkManager.
- `services/backend`: Java 21 / Spring Boot 3 API with JPA, Flyway, PostGIS,
  Redis configuration, and Swagger.
- `apps/admin-web`: React / Vite / TypeScript / Ant Design administration UI.
- `infra/local`: Legacy Docker helper scripts; these are not used by the
  approved non-Docker LOCAL environment.
- `docs`: Environment authority, current approved behavior, deployment SOP,
  and historical baseline specifications.

## Quick Start

Requirements:

- Java 21
- Node.js and npm
- PostgreSQL 16 with PostGIS 3.4
- Redis 7
- Android Studio with Android SDK 36 for the Android APK

Create the optional untracked LOCAL runtime file. PostgreSQL/PostGIS and Redis
must run as computer services (not Docker):

```bash
cp config/environments/local.env.example .env.local
./scripts/environment/start-local.sh check .env.local
```

Start the backend:

```bash
./scripts/environment/start-local.sh backend .env.local
```

Start Admin Web in another terminal:

```bash
./scripts/environment/start-local.sh frontend .env.local
```

Build and connect the LOCAL Android physical device through USB:

```bash
./scripts/environment/start-local.sh mobile .env.local
adb install -r apps/android-bus/app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8080 tcp:8080
```

The LOCAL APK uses `http://127.0.0.1:8080/api/device/v1/`. Keep the USB
connection and ADB reverse active while testing or synchronizing. Do not change
the endpoint to a LAN, DEV, or PROD URL during routine LOCAL work.

Open `http://localhost:5173`. Demo Admin credentials are `admin` /
`admin123`. Swagger is available at `http://localhost:8080/swagger-ui.html`.

Seeded Demo data:

- Buses: `BUS-01`, `BUS-02`, and inactive `BUS-03`
- Employees: `E00123`, `E00201` through `E00204`
- CardSN examples: `04A1B2C3D4`, `TESTCARD0001` through `TESTCARD0004`
- Shared Android device API key: `demo-device-key`
- Device hardware serial mappings: `QCM2290-CF8F718B` (`BUS-01`) and
  `QCM2290-TEST0002` (`BUS-02`)
- Today's route points and boarding events for `BUS-01` and `BUS-02`

These credentials are for local Demo use only.

## Verification

The following is the approved LOCAL verification sequence. Before executing
it, Codex must list the exact environment file and commands and receive the
owner's confirmation as required by `AGENTS.md`.

```bash
source scripts/environment/load-env.sh
bus_env_load local .env.local
(cd services/backend && ./mvnw clean test package)
(cd apps/admin-web && npm run lint)
./scripts/environment/build-frontend.sh local .env.local
./scripts/environment/build-mobile.sh local .env.local
adb install -r apps/android-bus/app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8080 tcp:8080
```

This sequence uses native LOCAL PostgreSQL/PostGIS and Redis and must never
target DEV or PROD services. The Android build requires a configured Android
SDK and a USB-connected physical device for installation and synchronization.
See each component's README for detailed setup and limitations.

Current contract authority is explained in
[Data Contract Approval](docs/data-contract-approval.md). The
[Baseline Data Contract](docs/proposed-data-contract.md) is partially
superseded; current Flyway migrations, implemented APIs, and
[Routes and Boarding Location](docs/routes-and-boarding-location.md) take
precedence for newer behavior.
