# Infrastructure

Local services:

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
