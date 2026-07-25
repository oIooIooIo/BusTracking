#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <dev|prod> <runtime-env-file>" >&2
  exit 2
fi
if [[ $1 != dev && $1 != prod ]]; then
  echo "Offline deployment is supported only for dev or prod." >&2
  exit 2
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=load-env.sh
source "$script_dir/load-env.sh"
bus_env_load "$1" "$2"

docker load -i "$OFFLINE_PACKAGE_NAME"
docker compose --env-file "$BUS_ENV_FILE" -f "$BUS_REPO_ROOT/infra/vm/compose.yaml" up -d
docker compose --env-file "$BUS_ENV_FILE" -f "$BUS_REPO_ROOT/infra/vm/compose.yaml" ps

