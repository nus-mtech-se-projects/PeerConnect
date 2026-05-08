#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DAST_USER_EMAIL:-}" || -z "${AZURE_SQL_SERVER:-}" || -z "${AZURE_SQL_DATABASE:-}" || -z "${AZURE_SQL_USERNAME:-}" || -z "${AZURE_SQL_PASSWORD:-}" ]]; then
  echo "Skipping cleanup because one or more Azure SQL settings are missing."
  exit 0
fi

escaped_email=$(printf "%s" "${DAST_USER_EMAIL}" | sed "s/'/''/g")

docker run --rm \
  -v "$PWD:/work" \
  -e AZURE_SQL_SERVER \
  -e AZURE_SQL_DATABASE \
  -e AZURE_SQL_USERNAME \
  -e AZURE_SQL_PASSWORD \
  -e DAST_CLEANUP_EMAIL="${escaped_email}" \
  mcr.microsoft.com/mssql-tools:latest \
  /bin/bash -lc '
    if [[ -x /opt/mssql-tools18/bin/sqlcmd ]]; then
      SQLCMD=/opt/mssql-tools18/bin/sqlcmd
    elif [[ -x /opt/mssql-tools/bin/sqlcmd ]]; then
      SQLCMD=/opt/mssql-tools/bin/sqlcmd
    else
      echo "sqlcmd was not found in the mssql-tools container." >&2
      exit 127
    fi

    "$SQLCMD" \
      -C \
      -S "${AZURE_SQL_SERVER}" \
      -d "${AZURE_SQL_DATABASE}" \
      -U "${AZURE_SQL_USERNAME}" \
      -P "${AZURE_SQL_PASSWORD}" \
      -Q "DELETE pr FROM dbo.password_resets pr INNER JOIN dbo.users u ON u.id = pr.user_id WHERE u.email = '\''${DAST_CLEANUP_EMAIL}'\'';"
  '
