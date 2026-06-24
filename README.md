# Bus Tracking System

Bus tracking Demo implemented as a monorepo. The shared contract is documented
before implementation so Android, backend, and Admin Web use compatible data.

## Components

- `apps/android-bus`: Android 13 bus application with GPS, NFC, Room, and
  WorkManager.
- `services/backend`: Java 21 / Spring Boot 3 API with JPA, Flyway, PostGIS,
  Redis configuration, and Swagger.
- `apps/admin-web`: React / Vite / TypeScript / Ant Design administration UI.
- `infra/local`: Local PostgreSQL/PostGIS and Redis scripts.
- `docs`: Approved MVP specification, architecture, and data contract.

## Quick Start

Requirements:

- Java 21
- Node.js and npm
- Docker Engine
- Android Studio with Android SDK 36 for the Android APK

Start infrastructure:

```bash
./infra/local/local-up.sh
```

Start the backend:

```bash
cd services/backend
./mvnw spring-boot:run
```

Start Admin Web in another terminal:

```bash
cd apps/admin-web
npm install
npm run dev
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
cd apps/android-bus && ./gradlew :app:assembleDebug
```

The Android build requires a configured Android SDK. See each component's
README for detailed setup and limitations.

The approved database and API definition is
[docs/proposed-data-contract.md](docs/proposed-data-contract.md).
