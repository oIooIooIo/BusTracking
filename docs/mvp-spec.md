# MVP Specification

## 1. Objective

Provide a bus-mounted Android application that records bus location and checks
employee boarding eligibility, plus an Admin Web application for permission
management and route history.

The MVP should remain a separable `bus-tracking` domain so it can later be
integrated into a car booking system without rewriting device-specific logic.

## 2. Actors

- Employee: presents an employee ID card when boarding.
- Bus device: records location, reads NFC cards, and synchronizes data.
- Administrator: manages boarding permissions and views route history.
- Backend service: owns business rules, persistence, and API authorization.

## 3. Android Bus App

### Required behavior

1. Start after the Android 13 device boots.
2. Record a GPS observation every 10 seconds while tracking is active.
3. Store observations locally when the server cannot be reached.
4. Upload pending observations automatically when network access returns.
5. Read an employee ID card through NFC.
6. Show whether the employee is allowed to board the current bus.

### Technical constraints

- Background location on Android 13 requires a foreground service and a
  persistent notification.
- Boot start uses `BOOT_COMPLETED`; device/OEM policy may also require kiosk,
  device-owner, or battery optimization configuration.
- Local pending data should use Room.
- Retry and network-constrained synchronization should use WorkManager.
- GPS retries use a persisted per-device 64-bit sequence number. Boarding-event
  retries use a client-generated UUID.
- GPS synchronization uses oldest-first batches of at most 500 rows.
- Boarding-event synchronization uses oldest-first batches of at most 200
  rows.
- Each upload request is limited to 1 MB.
- Room loads one batch at a time, and only server-acknowledged rows are
  deleted.
- Permanently rejected rows move to a diagnostic/dead-letter state so later
  rows can continue synchronizing.
- Only one queue synchronization worker runs at a time; failures use
  exponential backoff.
- Android retains at most 30 days of local GPS rows and drops the oldest rows
  when that limit is exceeded.

### Permission-check mode

The Demo uses cached authorization:

- The Android app downloads the current bus's allowed CardSN list.
- The latest successfully downloaded list is stored in Room.
- NFC checks use the local list, so they work without network access.
- The app refreshes the list after startup, when connectivity returns, and
  periodically while online.
- Permission changes made while a bus is offline take effect only after its
  next successful synchronization.
- If a newly installed device has never downloaded a list, it reports that
  authorization data is not ready instead of silently allowing boarding.

For the Demo, CardSN is the only NFC card identifier.

## 4. Backend

### Responsibilities

- Authenticate bus devices and administrators.
- Receive idempotent batches of GPS observations.
- Evaluate or distribute bus boarding permissions.
- Receive boarding scan events.
- Provide permission-management operations.
- Provide route-history queries suitable for map display.
- Publish an OpenAPI contract used to generate or validate client types.

### Proposed package boundary

Use a modular monolith for the MVP:

- `identity`: administrators, employees, and authentication integration.
- `fleet`: buses and installed devices.
- `tracking`: location ingestion and route queries.
- `access`: boarding permissions and NFC scan decisions.

These are logical module boundaries, not separate deployable services.

## 5. Admin Web

### Required screens

- Sign-in.
- Bus list and bus details.
- Employee boarding-permission management.
- Route-history search by bus and time range.
- Route map with ordered path points and basic observation details.

### Client contract rule

The Web app must not hand-write a second version of backend data structures.
API types should be generated from, or checked against, the approved OpenAPI
contract.

## 6. Infrastructure

- PostgreSQL with PostGIS is the system of record.
- Redis is reserved for cache, short-lived authorization data, and optional
  asynchronous workload coordination.
- Redis must not be the sole store for GPS or boarding events.
- Flyway owns database schema changes after schema approval.

## 7. Out of Scope for Initial MVP

- Route planning or optimization.
- Employee seat booking.
- Payroll or attendance calculation.
- Passenger-facing application.
- Live dispatch control.
- Microservice decomposition.
- Long-term analytics and reporting warehouse.

## 8. Quality Requirements

- GPS upload retries must be idempotent.
- Device and admin authentication must be separate concerns.
- Sensitive card identifiers must not be logged in plaintext.
- All persisted timestamps must use a timezone-safe representation.
- Route queries must have bounded date ranges and pagination or point limits.
- Admin permission changes must be authenticated and application-logged for
  the Demo; persistent audit history is deferred.

## 9. Acceptance Outline

- Rebooting a configured Android 13 device resumes the bus application and
  continuous tracking service.
- A disconnected device keeps collecting observations and uploads them after
  connectivity returns without duplicate server records.
- An NFC scan produces an understandable allow/deny result.
- An administrator can change a bus permission and see the effective result.
- An administrator can select a bus and time range and view its path on a map.
