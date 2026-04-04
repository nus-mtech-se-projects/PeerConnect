#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${APP_URL:-}" || -z "${DAST_USER_EMAIL:-}" || -z "${DAST_USER_PASSWORD:-}" ]]; then
  echo "::error::APP_URL, DAST_USER_EMAIL, and DAST_USER_PASSWORD must be set."
  exit 1
fi

json_payload=$(jq -n \
  --arg email "${DAST_USER_EMAIL}" \
  --arg password "${DAST_USER_PASSWORD}" \
  '{email: $email, password: $password}')

response=$(curl -s -w "\n%{http_code}" \
  -X POST "${APP_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "${json_payload}")

http_status=$(echo "${response}" | tail -n1)
body=$(echo "${response}" | head -n -1)

if [[ "${http_status}" != "200" ]]; then
  echo "Login failed with status ${http_status}"
  echo "${body}"
  exit 1
fi

token=$(echo "${body}" | jq -r ".accessToken")
if [[ -z "${token}" || "${token}" == "null" ]]; then
  echo "accessToken not found in response"
  echo "${body}"
  exit 1
fi

echo "token=${token}" >> "${GITHUB_OUTPUT}"
