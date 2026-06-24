# Backend

Java 21 / Spring Boot 3 backend for device synchronization and Admin Web APIs.

## Run

Start local infrastructure from the repository root:

```bash
./infra/local/local-up.sh
```

Then:

```bash
cd services/backend
./mvnw spring-boot:run
```

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

- `/api/device/v1`: permission snapshot, GPS batches, boarding-event batches
- `/api/admin/v1`: buses, employees, permissions, routes, boarding events

Device batches are limited to 1 MB. GPS accepts at most 500 records and
boarding events at most 200 records per request. Route queries are limited to
24 hours and 10,000 points.

## Build

```bash
./mvnw clean test package
```

Flyway migrations are in `src/main/resources/db/migration`.
