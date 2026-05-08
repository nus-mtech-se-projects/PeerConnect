#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${AZURE_SQL_SERVER:-}" || -z "${AZURE_SQL_DATABASE:-}" || -z "${AZURE_SQL_USERNAME:-}" || -z "${AZURE_SQL_PASSWORD:-}" ]]; then
  echo "Skipping UAT cleanup because one or more Azure SQL settings are missing."
  exit 0
fi

cat > cleanup-uat.sql <<'EOF'
IF OBJECT_ID('tempdb..#uat_users') IS NOT NULL DROP TABLE #uat_users;

SELECT id
INTO #uat_users
FROM dbo.users
WHERE email LIKE 'test-%@u.nus.edu.sg'
   OR email LIKE 'test2-%@u.nus.edu.sg'
   OR email LIKE 'tutor2-%@u.nus.edu.sg';

DELETE FROM dbo.password_resets
WHERE user_id IN (SELECT id FROM #uat_users);

DELETE FROM dbo.audit_event
WHERE actor_user_id IN (SELECT id FROM #uat_users);

DELETE FROM dbo.peer_feedback
WHERE reviewer_id IN (SELECT id FROM #uat_users)
   OR reviewee_id IN (SELECT id FROM #uat_users)
   OR peer_tutor_group_id IN (
       SELECT id FROM dbo.tutoring_courses WHERE created_by IN (SELECT id FROM #uat_users)
   );

DELETE FROM dbo.tutoring_signup
WHERE user_id IN (SELECT id FROM #uat_users)
   OR course_id IN (
       SELECT id FROM dbo.tutoring_courses WHERE created_by IN (SELECT id FROM #uat_users)
   );

DELETE FROM dbo.study_sessions
WHERE group_id IN (
    SELECT id FROM dbo.study_groups WHERE created_by IN (SELECT id FROM #uat_users)
);

DELETE FROM dbo.study_group_members
WHERE user_id IN (SELECT id FROM #uat_users)
   OR group_id IN (
       SELECT id FROM dbo.study_groups WHERE created_by IN (SELECT id FROM #uat_users)
   );

DELETE FROM dbo.restricted_member
WHERE blocker_id IN (SELECT id FROM #uat_users)
   OR blocked_id IN (SELECT id FROM #uat_users);

DELETE FROM dbo.tutoring_courses
WHERE created_by IN (SELECT id FROM #uat_users);

DELETE FROM dbo.study_groups
WHERE created_by IN (SELECT id FROM #uat_users);

DELETE FROM dbo.profiles
WHERE user_id IN (SELECT id FROM #uat_users);

DELETE FROM dbo.users
WHERE id IN (SELECT id FROM #uat_users);
EOF

docker run --rm \
  -v "$PWD:/work" \
  -e AZURE_SQL_SERVER \
  -e AZURE_SQL_DATABASE \
  -e AZURE_SQL_USERNAME \
  -e AZURE_SQL_PASSWORD \
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
      -i /work/cleanup-uat.sql
  '
