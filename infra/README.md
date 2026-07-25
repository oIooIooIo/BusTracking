# Infrastructure

# Legacy Docker utilities

The approved `LOCAL` environment runs directly on the computer and does not
use Docker. Its source of truth and startup instructions are in
`config/environments/local.env.example` and
`docs/environment-configuration.md`.

The scripts in this directory are retained only as legacy Docker utilities and
must not be treated as the approved LOCAL environment.

Legacy containerized services:

- PostgreSQL 16 with PostGIS 3.4 on port `5432`
- Redis 7 on port `6379`

The scripts use Docker Engine directly and do not require the Docker Compose
plugin.

From the repository root:

```bash
./infra/local/local-up.sh
./infra/local/local-down.sh
./infra/local/reset-data.sh
```

`reset-data.sh` deletes the local Demo containers and database volumes. The
backend recreates the schema and seed data through Flyway on its next startup.

Local PostgreSQL credentials:

- Database: `bus_tracking`
- User: `bus_tracking`
- Password: `bus_tracking`

These values are for local Demo use only.
