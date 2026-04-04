# Study Group Management — End-to-End Handoff

## Summary
This update delivers end-to-end Study Group creation and management across backend and frontend.

Implemented capabilities include:
- Create group with required details and validation
- Owner assignment at creation
- Join/leave with membership and capacity checks
- Admin management for editing, invites, approvals, removals, ownership transfer
- Session scheduling CRUD for each group
- Group status handling (`active`, `full`, `dissolved`)
- Authorization checks for management actions
- UI integration for all core management operations

## Branches and Commits
### Backend (`PeerConnect`)
- Branch: `dev/Roohi`
- Commit: `eaa4c1f`
- Message: `feat(groups): implement end-to-end study group management`

### Frontend (`PeerConnect-frontend`)
- Branch: `dev/roohi`
- Commit: `a18bfa7`
- Message: `feat(groups-ui): add end-to-end study group management flows`

## Backend API Coverage
### Group APIs
- `GET /api/groups`
- `POST /api/groups`
- `GET /api/groups/{id}`
- `PUT /api/groups/{id}`
- `POST /api/groups/{id}/join`
- `POST /api/groups/{id}/leave`
- `POST /api/groups/{id}/dissolve`
- `POST /api/groups/{id}/transfer-ownership`

### Member Management APIs
- `GET /api/groups/{id}/members`
- `POST /api/groups/{id}/members/invite`
- `POST /api/groups/{id}/members/{userId}/approve`
- `DELETE /api/groups/{id}/members/{userId}`

### Session APIs
- `GET /api/groups/{id}/sessions`
- `POST /api/groups/{id}/sessions`
- `PUT /api/groups/{id}/sessions/{sessionId}`
- `DELETE /api/groups/{id}/sessions/{sessionId}`

## Database Tables Used
- `study_groups` — stores group details (name, module/subject, description, schedule, mode, status, capacity, owner)
- `study_group_members` — stores membership entries (role, status: invited/pending/approved)
- `study_sessions` — stores scheduled study sessions per group
- `users` — user identities referenced for owners/members/admin checks
- `sql_items` — existing sample table (unrelated to study-group feature)

## Validation and Rules Implemented
- Required fields validation for create/update
- Mode-based validation:
  - online/hybrid requires `meetingLink`
  - in-person/hybrid requires `location`
- Membership capacity enforcement (`maxMembers`)
- Group status transitions (`active` -> `full`, and `dissolved`)
- Owner/admin authorization checks for management actions
- Non-admin users blocked from edit/manage endpoints

## Frontend UX Coverage
- Study groups dashboard list with metadata (`status`, counts, schedule)
- Create Group modal with required field validation aligned to backend
- Join/Leave actions with status-aware behavior
- Admin-only Manage flow for:
  - Editing group details
  - Inviting and approving members
  - Removing members
  - Transferring ownership
  - Creating/deleting sessions
  - Dissolving group

## Build and Verification Status
- Backend compile: success (`./gradlew compileJava`)
- Frontend build: success (`npm run build`)

## Quick Run Instructions
1. Backend:
   - `cd PeerConnect`
   - `./gradlew bootRun`
2. Frontend:
   - `cd PeerConnect-frontend`
   - `npm run dev`
3. Test sequence:
   - Login -> Create Group -> Manage Group -> Invite/Approve -> Create Session -> Join/Leave -> Transfer Owner/Dissolve

## Notes
- Local DB is configured to file-based H2 persistence in development so data survives restarts.
- Some local untracked files (env/db artifacts) were intentionally not committed.
