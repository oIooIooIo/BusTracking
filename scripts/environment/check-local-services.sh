#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=load-env.sh
source "$script_dir/load-env.sh"
bus_env_load local "${1:-}"

if ! command -v pg_isready >/dev/null 2>&1; then
  echo "pg_isready is not installed; install the local PostgreSQL client." >&2
  exit 1
fi
if ! command -v redis-cli >/dev/null 2>&1; then
  echo "redis-cli is not installed; install the local Redis client." >&2
  exit 1
fi

pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB"
redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping | grep -qx PONG
echo "LOCAL PostgreSQL and Redis are reachable without Docker."

