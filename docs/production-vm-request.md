# Bus Tracking Production VM Request

## Purpose

This VM will be used as the production environment for the Bus Tracking system.

It will host the backend API, database, cache, and Admin Web UI. Android bus
devices will upload GPS and NFC boarding events to this server.

The initial production rollout is expected to support around 30 buses. The
system should also allow future scaling if the company fleet grows.

## Recommended VM Specification

| Item | Requirement |
| --- | --- |
| OS | Ubuntu Server 22.04 LTS or 24.04 LTS |
| CPU | Minimum 4 vCPU |
| RAM | Minimum 8 GB, recommended 16 GB |
| Disk | Minimum 300 GB SSD, recommended 500 GB SSD |
| Network | Public Internet access with static public IP or public DNS name |
| Timezone | Asia/Ho_Chi_Minh |
| Backup | Daily PostgreSQL backup required |
| Monitoring | Basic CPU, RAM, disk, service health, and database monitoring recommended |

## Production Load Estimate

| Item | Estimate |
| --- | --- |
| Initial bus count | 30 buses |
| GPS frequency | 1 GPS point per bus every 10 seconds |
| GPS volume | About 180 GPS points per minute |
| Daily GPS volume | About 259,200 GPS points per day |
| Boarding events | Depends on employee boarding volume |
| Admin users | Low concurrency during initial rollout |

## System Components

| Component | Requirement |
| --- | --- |
| Backend API | Java 21 / Spring Boot 3 |
| Database | PostgreSQL 16 + PostGIS |
| Cache | Redis 7 |
| Admin Web | React/Vite static build served by Nginx |
| Reverse Proxy | Nginx recommended |
| TLS | HTTPS certificate required for production |
| Container Runtime | Docker Engine + Docker Compose Plugin recommended |

## Network Ports

| Port | Purpose | Access |
| --- | --- | --- |
| 443 | Admin Web and API reverse proxy over HTTPS | Open to Internet for authorized users and Android bus devices |
| 80 | Optional HTTP to HTTPS redirect | Open to Internet only if redirect is required |
| 8080 | Spring Boot backend API | Localhost only, proxied by Nginx |
| 5432 | PostgreSQL | Do not expose to Internet; restrict to localhost or specific maintenance IPs |
| 6379 | Redis | Do not expose to Internet; restrict to localhost |
| 22 | SSH | Restrict to specific IT/developer maintenance public IPs |

## External Connectivity

- Production will use public Internet access, not an internal-only network.
- Android bus devices must be able to reach the production API, for example:
  `https://<production-domain>/api/device/v1/`
- Admin users must be able to reach the Admin Web UI, for example:
  `https://<production-domain>/`
- Admin Web must be able to reach the backend API, for example:
  `https://<production-domain>/api/admin/v1`
- The Admin Web map uses OpenStreetMap tiles:
  `https://{s}.tile.openstreetmap.org/...`

If outbound Internet access is restricted by the hosting environment, please
allow access to OpenStreetMap tile servers or provide another map tile source.

## Backend Environment Variables

```bash
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/bus_tracking
DB_USERNAME=bus_tracking
DB_PASSWORD=<set by IT or developer>
REDIS_HOST=localhost
REDIS_PORT=6379
ADMIN_USERNAME=<production admin username>
ADMIN_PASSWORD=<production admin password>
DEVICE_API_KEY=<production Android device API key>
ADMIN_WEB_ORIGIN=https://<production-domain>
```

## Backup and Retention Recommendation

| Item | Recommendation |
| --- | --- |
| PostgreSQL backup | Daily backup required |
| Backup retention | At least 14 days |
| Backup storage | Store backups outside the production VM if possible |
| Restore test | Recommended before go-live |
| GPS route history | Keep at least 90 days online |
| Boarding events | Keep at least 1 year online, or follow company policy |
| Application logs | Keep at least 30 days |

With 30 buses sending GPS every 10 seconds, the system will receive about
259,200 GPS points per day. If longer GPS history is required, increase disk
capacity or plan archive storage.

## Deployment Recommendation

- Run PostgreSQL/PostGIS and Redis with Docker Compose or managed services.
- Run the backend as a Java 21 Spring Boot service.
- Build the Admin Web as static files and serve it via Nginx.
- Use HTTPS for all production access.
- Use Nginx as the public entry point:
  - `/` -> Admin Web
  - `/api/` -> Backend on `localhost:8080`
  - `/swagger-ui.html` -> Backend Swagger UI, optional for production
  - `/actuator/health` -> Health check
- Configure all services to restart automatically after VM reboot.

## Security Notes

- Do not expose PostgreSQL or Redis directly to the Internet.
- Restrict SSH to specific maintenance public IPs.
- Use production-specific admin credentials.
- Use a production-specific Android device API key.
- Do not reuse demo credentials in production.
- Store production secrets outside source code.

## Future Scaling

The initial 30-bus rollout can run on a single production VM with the
recommended specification.

If the fleet grows significantly, the system can be scaled by:

- Moving PostgreSQL/PostGIS to a dedicated database VM or managed database.
- Running multiple backend API instances behind Nginx or a load balancer.
- Increasing disk capacity or adding archive storage for historical GPS data.
- Adding database partitioning for high-volume GPS history.

## Note

For the initial production scope of around 30 buses, `4 vCPU / 8 GB RAM /
300 GB SSD` is the minimum acceptable production specification.

For smoother operation, longer GPS history, and safer growth capacity,
`4 vCPU / 16 GB RAM / 500 GB SSD` is recommended.
