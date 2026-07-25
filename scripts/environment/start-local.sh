#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <check|backend|frontend|mobile> [runtime-env-file]" >&2
  exit 2
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
component=$1
runtime_file=${2:-}

case "$component" in
  check) exec "$script_dir/check-local-services.sh" "$runtime_file" ;;
  backend)
    "$script_dir/check-local-services.sh" "$runtime_file"
    exec "$script_dir/run-backend.sh" local "$runtime_file"
    ;;
  frontend) exec "$script_dir/run-frontend.sh" local "$runtime_file" ;;
  mobile) exec "$script_dir/build-mobile.sh" local "$runtime_file" ;;
  *)
    echo "Unsupported LOCAL component: $component" >&2
    exit 2
    ;;
esac

