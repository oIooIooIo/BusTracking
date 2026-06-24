# Bus Tracking Demo VM Request

## Purpose

This VM will be used as the demo environment for the Bus Tracking system.

It will host the backend API, database, cache, and Admin Web UI. Android bus
devices will upload GPS and NFC boarding events to this server.

## Recommended VM Specification

| Item | Requirement |
| --- | --- |
| OS | Ubuntu Server 22.04 LTS or 24.04 LTS |
| CPU | Minimum 2 vCPU, recommended 4 vCPU |
| RAM | Minimum 4 GB, recommended 8 GB |
| Disk | Minimum 60 GB SSD, recommended 100 GB SSD |
| Network | Static IP or internal DNS name |
| Timezone | Asia/Ho_Chi_Minh |
| Backup | Daily PostgreSQL backup is recommended for demo data |

## System Components

| Component | Requirement |
| --- | --- |
| Backend API | Java 21 / Spring Boot 3 |
| Database | PostgreSQL 16 + PostGIS |
| Cache | Redis 7 |
| Admin Web | React/Vite static build served by Nginx |
| Reverse Proxy | Nginx recommended |
| Container Runtime | Docker Engine + Docker Compose Plugin recommended |

## Network Ports

| Port | Purpose | Access |
| --- | --- | --- |
| 80 / 443 | Admin Web and API reverse proxy | Open to demo users and Android bus devices |
| 8080 | Spring Boot backend API | Can be localhost only, proxied by Nginx |
| 5432 | PostgreSQL | Restrict to localhost or IT admin network |
| 6379 | Redis | Restrict to localhost |
| 22 | SSH | Restrict to IT/developer maintenance IPs |

## External Connectivity

- Android bus devices must be able to reach the VM API, for example:
  `http://<VM-IP>/api/device/v1/`
- Admin Web must be able to reach the backend API, for example:
  `http://<VM-IP>/api/admin/v1`
- The Admin Web map uses OpenStreetMap tiles:
  `https://{s}.tile.openstreetmap.org/...`

If the corporate network blocks Internet access, the map background may not
display unless this access is allowed or an internal map source is provided.

## Backend Environment Variables

```bash
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/bus_tracking
DB_USERNAME=bus_tracking
DB_PASSWORD=<set by IT or developer>
REDIS_HOST=localhost
REDIS_PORT=6379
ADMIN_USERNAME=<demo admin username>
ADMIN_PASSWORD=<demo admin password>
DEVICE_API_KEY=<shared Android device API key>
ADMIN_WEB_ORIGIN=http://<VM-IP-or-DNS>
```

## Deployment Recommendation

- Run PostgreSQL/PostGIS and Redis with Docker Compose.
- Run the backend as a Java 21 Spring Boot jar.
- Build the Admin Web as static files and serve it via Nginx.
- Use Nginx as the public entry point:
  - `/` -> Admin Web
  - `/api/` -> Backend on `localhost:8080`
  - `/swagger-ui.html` -> Backend Swagger UI
  - `/actuator/health` -> Health check

## Note

For the current demo scope, `2 vCPU / 4 GB RAM / 60 GB SSD` is sufficient.

For smoother on-site demos, multiple users, and continuous GPS data upload,
`4 vCPU / 8 GB RAM / 100 GB SSD` is recommended.
