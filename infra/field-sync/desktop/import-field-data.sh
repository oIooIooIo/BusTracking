#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <field-data-directory>" >&2
  exit 1
fi

bundle=$1
container=${POSTGRES_CONTAINER:-bus-tracking-postgres}
database=${POSTGRES_DB:-bus_tracking}
username=${POSTGRES_USER:-bus_tracking}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

if ! docker container inspect "$container" >/dev/null 2>&1; then
  echo "PostgreSQL container not found: $container" >&2
  exit 1
fi

for file in field-gps.csv field-boarding-events.csv; do
  if [[ ! -f "$bundle/$file" ]]; then
    echo "Missing $bundle/$file" >&2
    exit 1
  fi
done

docker cp "$bundle/field-gps.csv" "$container:/tmp/field-gps.csv"
docker cp "$bundle/field-boarding-events.csv" "$container:/tmp/field-boarding-events.csv"
docker cp "$script_dir/import-field-data.sql" "$container:/tmp/import-field-data.sql"

docker exec -i "$container" psql \
  -U "$username" \
  -d "$database" \
  -v ON_ERROR_STOP=1 \
  -f /tmp/import-field-data.sql

echo "Field GPS and boarding events imported. Duplicate IDs were skipped."
