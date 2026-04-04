#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${APP_URL:-}" || -z "${DAST_TOKEN:-}" ]]; then
  echo "APP_URL and DAST_TOKEN are required."
  exit 1
fi

printf "100001\tIGNORE\t.*\n" > zap.conf

docker run --rm \
  -v "$PWD:/zap/wrk" \
  -u 0 \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-api-scan.py \
  -t "${APP_URL}/v3/api-docs" \
  -f openapi \
  -c /zap/wrk/zap.conf \
  -r /zap/wrk/report_html.html \
  -J /zap/wrk/report_json.json \
  -w /zap/wrk/report_md.md \
  -z "-config replacer.full_list(0).description=jwt-auth \
      -config replacer.full_list(0).enabled=true \
      -config replacer.full_list(0).matchtype=REQ_HEADER \
      -config replacer.full_list(0).matchstr=Authorization \
      -config replacer.full_list(0).replacement=Bearer\ ${DAST_TOKEN}" \
  -I || true
