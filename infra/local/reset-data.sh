#!/usr/bin/env bash
set -euo pipefail

docker rm -f bus-tracking-postgres bus-tracking-redis >/dev/null 2>&1 || true
docker volume rm bus-tracking-postgres bus-tracking-redis >/dev/null 2>&1 || true
echo "Local containers and data volumes removed."
