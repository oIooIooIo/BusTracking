#!/usr/bin/env bash
set -euo pipefail

environment=${1:-}
if [[ -z "$environment" ]]; then
  echo "Usage: validate-env.sh <local|dev|prod>" >&2
  exit 2
fi

common_required=(
  APP_ENV PUBLIC_WEB_URL VITE_API_URL MOBILE_API_BASE_URL
  MOBILE_USES_CLEARTEXT ADMIN_WEB_ORIGIN SERVER_PORT
  POSTGRES_HOST POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
  DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PORT
  ADMIN_USERNAME ADMIN_PASSWORD DEVICE_API_KEY
)

deployment_required=(
  BACKEND_IMAGE ADMIN_WEB_IMAGE POSTGRES_IMAGE REDIS_IMAGE
  TLS_CERT_DIR OFFLINE_PACKAGE_NAME
)

required=("${common_required[@]}")
case "$environment" in
  local) ;;
  dev|prod) required+=("${deployment_required[@]}") ;;
  *)
    echo "Unsupported environment: $environment" >&2
    exit 2
    ;;
esac

errors=0
for name in "${required[@]}"; do
  value=${!name-}
  if [[ -z "$value" ]]; then
    echo "Missing required $environment field: $name" >&2
    errors=1
  elif [[ "$value" == CHANGE_ME_* ]]; then
    echo "Unresolved placeholder in $environment field: $name" >&2
    errors=1
  fi
done

if [[ ${APP_ENV:-} != "$environment" ]]; then
  echo "APP_ENV must equal '$environment'." >&2
  errors=1
fi

if (( errors != 0 )); then
  echo "Environment validation failed; no program was started or built." >&2
  exit 2
fi

echo "Environment validation passed: $environment"

