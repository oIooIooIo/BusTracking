# Data Contract Approval

## Rule

Do not create or modify database tables, Flyway migrations, JPA entities,
OpenAPI schemas, Android DTOs, or Admin Web API types until the owner approves
the relevant data proposal.

Approval should cover both persistence and externally visible data. A database
field is not automatically an API field.

## Confirmed Demo Decisions

- NFC identification uses the CardSN returned by the reader.
- Boarding permission is a direct employee-to-bus assignment.
- The Android app must check permission while offline using a local copy of
  the bus permission list.
- GPS recording starts after boot and continues while the device is running.
- GPS upload is limited to 500 rows and 1 MB per request.
- Boarding-event upload is limited to 200 rows and 1 MB per request.
- Android keeps at most 30 days of local GPS rows.
- Queue synchronization uses oldest-first batches, server acknowledgement,
  idempotent identifiers, single-worker execution, and exponential backoff.
- GPS uses (`device_id`, `sequence_no bigint`) as its primary key and upload
  identity; GPS does not use a standalone UUID.
- App reinstall or local-data reset requires a new registered `device_id`.
- Only one active device may be assigned to a bus; old device records remain
  inactive for history.
- Batch APIs return per-row accepted and rejected results so malformed records
  cannot permanently block synchronization.
- Route-history requests are limited to 24 hours and 10,000 points.

The proposed tables and API payloads based on these decisions are documented
in [Proposed Data Contract](proposed-data-contract.md).

## Deferred Decisions

### Bus and device identity

- What identifier is already used for a bus: internal number, license plate,
  asset number, or another value?
- Can one physical Android device move between buses?
- Does a bus need active/inactive state?
- The current Demo registers the unique 618K SoC hardware serial manually and
  maps it to a Bus; all devices use the same APK and deployment API key.

### Employee and NFC identity

- Is employee information owned by this Demo or synchronized from HR?
- Which employee fields may this system store and display?
- Must CardSN be encrypted, hashed, or tokenized after the Demo?

### Boarding permission

- What deny reasons should be visible to the driver or employee?
- After the Demo, should an offline permission cache have an expiry time?

### GPS and trip behavior

- Is there a formal trip with start/end, route, direction, or schedule after
  the Demo?
- Which location values are required beyond latitude and longitude: accuracy,
  altitude, speed, bearing, provider, or mocked-location status?
- How long must raw 10-second observations be retained?
- Is real-time position required, or only historical route display?

### Boarding events

- Must every NFC scan be stored?
- Should successful and denied scans both be retained?
- Must events record GPS location and bus trip context?
- Is this data used only for security, or also attendance/reporting?
- What is the retention period?

### Administration and audit

- Which roles exist?
- Is company SSO required?
- Which changes require audit history?
- Are multiple companies/tenants required now or in the future?

### Time and localization

- Which business timezone controls permission windows and reports?
- Which UI languages are required?
- Should API timestamps use UTC while preserving the business timezone?

## Current Approval Artifact

The current proposal includes:

- Proposed tables with field names, types, nullability, keys, and indexes.
- Proposed relationships and deletion/retention behavior.
- Sample API requests and responses.
- Android offline records and synchronization states.
- Mapping between database fields, API fields, and UI fields.

[Proposed Data Contract](proposed-data-contract.md) was approved on
June 13, 2026.
