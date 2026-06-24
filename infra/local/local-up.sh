#!/usr/bin/env bash
set -euo pipefail

network=bus-tracking

docker network inspect "$network" >/dev/null 2>&1 ||
  docker network create "$network" >/dev/null

if ! docker container inspect bus-tracking-postgres >/dev/null 2>&1; then
  docker run -d \
    --name bus-tracking-postgres \
    --network "$network" \
    -e POSTGRES_DB=bus_tracking \
    -e POSTGRES_USER=bus_tracking \
    -e POSTGRES_PASSWORD=bus_tracking \
    -p 5432:5432 \
    -v bus-tracking-postgres:/var/lib/postgresql/data \
    postgis/postgis:16-3.4 >/dev/null
else
  docker start bus-tracking-postgres >/dev/null
fi

if ! docker container inspect bus-tracking-redis >/dev/null 2>&1; then
  docker run -d \
    --name bus-tracking-redis \
    --network "$network" \
    -p 6379:6379 \
    -v bus-tracking-redis:/data \
    redis:7-alpine >/dev/null
else
  docker start bus-tracking-redis >/dev/null
fi

for _ in $(seq 1 30); do
  if docker exec bus-tracking-postgres pg_isready -U bus_tracking -d bus_tracking >/dev/null 2>&1; then
    echo "PostgreSQL/PostGIS and Redis are running."
    exit 0
  fi
  sleep 1
done

echo "PostgreSQL did not become ready in time." >&2
exit 1
