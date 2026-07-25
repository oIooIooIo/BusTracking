#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <dev|prod> <runtime-env-file>" >&2
  exit 2
fi
if [[ $1 != dev && $1 != prod ]]; then
  echo "Offline images are supported only for dev or prod." >&2
  exit 2
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=load-env.sh
source "$script_dir/load-env.sh"
bus_env_load "$1" "$2"

docker build -t "$BACKEND_IMAGE" "$BUS_REPO_ROOT/services/backend"
docker build --build-arg VITE_API_URL="$VITE_API_URL" -t "$ADMIN_WEB_IMAGE" "$BUS_REPO_ROOT/apps/admin-web"
docker image inspect "$POSTGRES_IMAGE" >/dev/null
docker image inspect "$REDIS_IMAGE" >/dev/null
docker save -o "$OFFLINE_PACKAGE_NAME" \
  "$BACKEND_IMAGE" "$ADMIN_WEB_IMAGE" "$POSTGRES_IMAGE" "$REDIS_IMAGE"

echo "Offline image package created: $OFFLINE_PACKAGE_NAME"

