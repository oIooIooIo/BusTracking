#!/usr/bin/env bash
set -euo pipefail

output=${1:-field-snapshot-$(date -u +%Y%m%d-%H%M%S).dump}
container=${POSTGRES_CONTAINER:-bus-tracking-postgres}
database=${POSTGRES_DB:-bus_tracking}
username=${POSTGRES_USER:-bus_tracking}

if ! docker container inspect "$container" >/dev/null 2>&1; then
  echo "PostgreSQL container not found: $container" >&2
  exit 1
fi

docker exec "$container" pg_dump \
  -U "$username" \
  -d "$database" \
  --format=custom \
  --no-owner \
  --no-acl >"$output"

echo "Field snapshot created: $output"
echo "Transfer this file and infra/field-sync to the Windows laptop."
