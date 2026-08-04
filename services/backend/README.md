# Backend

Java 21 / Spring Boot 3 backend for device synchronization and Admin Web APIs.

## Run

For the approved non-Docker LOCAL environment, start PostgreSQL/PostGIS and
Redis directly on the computer, then run from the repository root:

```bash
./scripts/environment/start-local.sh check
./scripts/environment/start-local.sh backend
```

To use an approved untracked runtime file:

```bash
./scripts/environment/start-local.sh backend .env.local
```

Do not run Maven directly without first loading an approved environment. The
backend intentionally has no fallback values for environment-controlled fields.

Default endpoints:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Demo Authentication

- Admin APIs: HTTP Basic `admin` / `admin123`
- Device APIs: `Authorization: Bearer demo-device-key` and
  `X-Device-Hardware-Serial: QCM2290-CF8F718B`

Do not use these built-in credentials outside a local Demo.

## API Groups

- `/api/device/v1`: permission/configuration synchronization, GPS batches,
  and boarding-event batches
- `/api/admin/v1`: buses, devices, device assignment history, employees,
  routes, shared stops, route permissions, route history, and boarding events

Device batches are limited to 1 MB. GPS accepts at most 500 records and
boarding events at most 200 records per request. Route queries are limited to
24 hours and 10,000 points.

## Build

Backend verification is a LOCAL operation and uses native PostgreSQL/PostGIS
and Redis. It must not target DEV or PROD. Before execution, Codex must list the
exact environment file and commands and receive the owner's confirmation.

From the repository root:

```bash
source scripts/environment/load-env.sh
bus_env_load local .env.local
(cd services/backend && ./mvnw clean test package)
```

Flyway migrations are in `src/main/resources/db/migration`.
