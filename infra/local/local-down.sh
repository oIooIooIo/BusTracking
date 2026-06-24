#!/usr/bin/env bash
set -euo pipefail

docker stop bus-tracking-postgres bus-tracking-redis >/dev/null 2>&1 || true
echo "Local containers stopped. Volumes were preserved."
