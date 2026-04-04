#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DAST_USER_EMAIL:-}" || -z "${AZURE_SQL_SERVER:-}" || -z "${AZURE_SQL_DATABASE:-}" || -z "${AZURE_SQL_USERNAME:-}" || -z "${AZURE_SQL_PASSWORD:-}" ]]; then
  echo "Skipping cleanup because one or more Azure SQL settings are missing."
  exit 0
fi

escaped_email=$(printf "%s" "${DAST_USER_EMAIL}" | sed "s/'/''/g")

docker run --rm \
  -v "$PWD:/work" \
  mcr.microsoft.com/mssql-tools \
  /opt/mssql-tools18/bin/sqlcmd \
  -C \
  -S "${AZURE_SQL_SERVER}" \
  -d "${AZURE_SQL_DATABASE}" \
  -U "${AZURE_SQL_USERNAME}" \
  -P "${AZURE_SQL_PASSWORD}" \
  -Q "DELETE pr FROM dbo.password_resets pr INNER JOIN dbo.users u ON u.id = pr.user_id WHERE u.email = '${escaped_email}';"
