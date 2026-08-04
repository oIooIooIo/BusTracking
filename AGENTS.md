# BusTracking repository instructions

## Protected environment configuration

The environment configuration is owner-controlled. The protected source-of-truth files are:

- `config/environments/local.env.example`
- `config/environments/dev.env.example`
- `config/environments/prod.env.example`
- `docs/environment-configuration.md`
- Any runtime `.env` file copied from those templates
- Environment-dependent values in Android Gradle, Vite, Spring Boot, Nginx, Dockerfiles, and Compose files

Codex and other automation must not add, remove, rename, infer, normalize, or change any environment field or value without explicit approval from the user in the current conversation.

Before changing protected configuration:

1. Explain why the change is needed.
2. Show the exact files, fields, old values, and proposed new values.
3. Ask the user for approval.
4. Make the change only after the user explicitly agrees.

Starting, stopping, rebuilding, or inspecting an environment must use the existing approved values and does not authorize configuration changes. Missing PROD values must remain blank until the user supplies and approves them. Never copy DEV secrets into PROD or commit populated deployment secrets.

## Fixed runtime topology

The deployment topology is owner-approved and must not be changed implicitly:

- LOCAL runs Admin Web, Backend, PostgreSQL/PostGIS, and Redis directly on the
  development computer. LOCAL does not use Docker.
- LOCAL Android uses a physical USB-connected device. Install or update the APK
  through ADB, run `adb reverse tcp:8080 tcp:8080`, and connect the Android app
  to `http://127.0.0.1:8080/api/device/v1/`.
- DEV runs Admin Web, Backend, PostgreSQL/PostGIS, and Redis as Docker
  containers on the DEV Server. Releases are delivered as offline images. The
  already approved DEV DNS name and values must be preserved.
- PROD will use a separate Production Server, Docker, offline-image delivery,
  and a production DNS name. Infrastructure and values that are not approved
  remain blank and must not be inferred from LOCAL or DEV.
- DEV and PROD use the owner-approved Blue-Green application deployment model:
  start the inactive application stack, wait for health and smoke tests, switch
  traffic gracefully, drain the old stack, and retain it for rollback.
- The current Compose and deployment scripts do not implement Blue-Green yet.
  Until that implementation is separately reviewed and approved, never claim
  that the current deployment is zero-downtime.
- This topology itself is protected. Changing it requires the same explicit
  approval process as changing an environment field or value.

## Mandatory confirmation before execution

Before running any test, build, package, or deployment command, Codex must:

1. State the target environment: LOCAL, DEV, or PROD.
2. State the operation: test, APK build/install, offline-image build, release
   package, or deployment.
3. List the exact environment file and commands that will be used.
4. List the affected components.
5. State whether the operation touches PostgreSQL, Redis, Docker, an Android
   device, or a server.
6. Confirm that no environment field or value will be changed.
7. Ask the owner for approval.

Execution may begin only after the owner explicitly approves that exact test,
build, package, or deployment operation.

For DEV and PROD application releases, routine deployment must not recreate
PostgreSQL, Redis, or the future permanent edge proxy. During Blue-Green
deployment, database migrations must remain compatible with both the old and
new Backend versions while they overlap. Any incompatible migration requires a
separately approved maintenance window and must not be described as zero-downtime.

