#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <local|dev|prod> [runtime-env-file]" >&2
  exit 2
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=load-env.sh
source "$script_dir/load-env.sh"
bus_env_load "$1" "${2:-}"

cd "$BUS_REPO_ROOT/apps/admin-web"
exec npm run dev

