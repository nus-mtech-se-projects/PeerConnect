#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${APP_URL:-}" ]]; then
  echo "APP_URL is required."
  exit 1
fi

echo "Checking application is reachable via POST /api/auth/login ..."

for attempt in $(seq 1 18); do
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{}' \
    "${APP_URL}/api/auth/login" || echo "000")

  echo "Attempt ${attempt}: HTTP ${http_code}"

  if [[ "${http_code}" == "400" || "${http_code}" == "200" ]]; then
    echo "Application is reachable"
    exit 0
  fi

  sleep 10
done

echo "Application did not respond in time; aborting DAST"
exit 1
