#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${BASE_URL:-}" ]]; then
  echo "BASE_URL is required."
  exit 1
fi

report_title="${REPORT_TITLE:-PeerConnect UAT Report}"

echo "Running UAT against: ${BASE_URL}"

newman run postman/PeerConnect.postman_collection.json \
  --environment postman/PeerConnect.postman_environment.json \
  --env-var "baseUrl=${BASE_URL}" \
  --reporters cli,htmlextra \
  --reporter-htmlextra-export postman-uat-report.html \
  --reporter-htmlextra-title "${report_title}" \
  --timeout-request 30000 \
  --timeout-script 10000
