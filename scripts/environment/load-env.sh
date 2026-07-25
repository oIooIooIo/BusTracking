#!/usr/bin/env bash

bus_env_load() {
  if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: bus_env_load <local|dev|prod> [runtime-env-file]" >&2
    return 2
  fi

  local environment=$1
  local requested_file=${2:-}
  local script_dir repo_root env_file

  case "$environment" in
    local|dev|prod) ;;
    *)
      echo "Unsupported environment: $environment (expected local, dev, or prod)." >&2
      return 2
      ;;
  esac

  script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
  repo_root=$(cd -- "$script_dir/../.." && pwd)
  env_file=${requested_file:-$repo_root/config/environments/$environment.env.example}

  if [[ ! -f "$env_file" ]]; then
    echo "Environment file not found: $env_file" >&2
    return 2
  fi

  env_file=$(cd -- "$(dirname -- "$env_file")" && pwd)/$(basename -- "$env_file")

  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a

  if [[ ${APP_ENV:-} != "$environment" ]]; then
    echo "APP_ENV '${APP_ENV:-<empty>}' does not match requested environment '$environment'." >&2
    return 2
  fi

  export BUS_ENV="$environment"
  export BUS_ENV_FILE="$env_file"
  export BUS_REPO_ROOT="$repo_root"

  "$script_dir/validate-env.sh" "$environment" || return $?
  echo "Loaded approved $environment environment from $env_file"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  echo "This file must be sourced by an approved environment runner." >&2
  exit 2
fi

