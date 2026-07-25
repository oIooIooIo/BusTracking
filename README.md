# Bus Tracking System

Environment values and the mandatory approval policy are documented in
[`docs/environment-configuration.md`](docs/environment-configuration.md). Use
the protected templates under `config/environments/`; do not infer or change
environment values during routine start, build, or deployment work.

Bus tracking Demo implemented as a monorepo. The shared contract is documented
before implementation so Android, backend, and Admin Web use compatible data.

## Components

- `apps/android-bus`: Android 13 bus application with GPS, NFC, Room, and
  WorkManager.
- `services/backend`: Java 21 / Spring Boot 3 API with JPA, Flyway, PostGIS,
  Redis configuration, and Swagger.
- `apps/admin-web`: React / Vite / TypeScript / Ant Design administration UI.
- `infra/local`: Legacy Docker helper scripts; these are not used by the
  approved non-Docker LOCAL environment.
- `docs`: Approved MVP specification, architecture, and data contract.

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

```bash
cd services/backend && ./mvnw clean test package
cd apps/admin-web && npm run lint && npm run build
./scripts/environment/start-local.sh mobile .env.local
```

The Android build requires a configured Android SDK. See each component's
README for detailed setup and limitations.

The approved database and API definition is
[docs/proposed-data-contract.md](docs/proposed-data-contract.md).
