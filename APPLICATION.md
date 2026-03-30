# PeerConnect — Application Documentation

**Course:** SWE5006 — National University of Singapore (NUS)  
**Team:** Group 1 — Callum Tan Dai Min, Foo Chuan Yong, Law Song Ming, Pyie Sone, Ruby Ferdianto, Vinoth Kannan Rohini, Mark Teo

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Data Model](#data-model)
6. [API Reference](#api-reference)
7. [Security](#security)
8. [Email Notifications](#email-notifications)
9. [File Storage](#file-storage)
10. [Running Locally](#running-locally)
11. [Building & Packaging](#building--packaging)
12. [Docker](#docker)
13. [GitHub Packages](#github-packages)
14. [Database Migrations](#database-migrations)
15. [Testing](#testing)

---

## Overview

PeerConnect is a RESTful backend service that enables NUS students to:

- Register and authenticate using email/password (JWT-based)
- Create and manage **study groups** with session scheduling
- Join study groups via invitation or direct request, with optional owner approval
- Offer and enroll in **peer tutoring classes**
- Give and receive **peer feedback**
- Manage a personal **profile** (including avatar upload)
- **Restrict/block** other users from joining their groups

---

## Architecture

```
Client (SPA / Postman)
        │
        ▼ HTTPS
┌─────────────────────────────┐
│     Spring Boot API Server  │
│                             │
│  SecurityConfig + JwtFilter │  ← JWT authentication on every request
│  REST Controllers (api/)    │  ← Handles HTTP, validates input
│  Service Layer (service/)   │  ← EmailService, AzureBlobService
│  Data Layer (data/sql/)     │  ← JPA Entities + Repositories
└─────────┬───────────────────┘
          │
          ├──► Azure SQL Server  (primary data store)
          ├──► Azure Blob Storage (avatar images)
          └──► Gmail SMTP        (transactional email)
```

The application is deployed as a container behind an Azure Static Web app frontend hosted at `https://salmon-island-0f8625f00.6.azurestaticapps.net`.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Web | Spring MVC |
| Security | Spring Security + JWT (Auth0 `java-jwt` 4.4.0) |
| ORM | Spring Data JPA / Hibernate |
| Database | Azure SQL Server (Microsoft MSSQL JDBC) |
| Blob Storage | Azure Blob Storage (`azure-storage-blob` 12.29.1) |
| Email | Spring Mail via Gmail SMTP |
| API Docs | SpringDoc OpenAPI 3.0 (Swagger UI at `/swagger-ui.html`) |
| Modularity | Spring Modulith 2.0.2 |
| Build | Gradle 8 |
| Code Gen | Lombok |
| Testing | JUnit 5, Spring Security Test |
| Coverage | JaCoCo |
| Quality | SonarQube 7.2 |
| Container | Docker / Spring Boot Docker Compose |

---

## Project Structure

```
src/
└── main/
    ├── java/mtech/swe5006/peerconnect/
    │   ├── PeerconnectApplication.java       # Entry point
    │   ├── api/                              # REST Controllers
    │   │   ├── AuthController.java           # /api/auth
    │   │   ├── UserController.java           # /api/users
    │   │   ├── ProfileController.java        # /api/profile
    │   │   ├── GroupController.java          # /api/groups
    │   │   ├── TutoringController.java       # /api/tutoring
    │   │   ├── RestrictedUserController.java # /api/restricted-users
    │   │   ├── SqlItemController.java        # /api/sqlitems
    │   │   ├── RootController.java           # /
    │   │   ├── BaseTemplate.java
    │   │   └── ControllerUtils.java
    │   ├── data/sql/                         # JPA Entities & Repositories
    │   │   ├── User.java / UserRepository.java
    │   │   ├── Profile.java / ProfileRepository.java
    │   │   ├── StudyGroup.java / StudyGroupRepository.java
    │   │   ├── StudyGroupMember.java / StudyGroupMemberRepository.java
    │   │   ├── StudySession.java / StudySessionRepository.java
    │   │   ├── TutoringClass.java / TutoringClassRepository.java
    │   │   ├── TutoringEnrollment.java / TutoringEnrollmentRepository.java
    │   │   ├── PeerFeedback.java / PeerFeedbackRepository.java
    │   │   ├── RestrictedUser.java / RestrictedUserRepository.java
    │   │   ├── SqlItem.java / SqlItemRepository.java
    │   │   ├── PasswordResetToken.java / PasswordResetTokenRepository.java
    │   │   └── BaseEntity.java
    │   ├── dto/
    │   │   ├── AuthDtos.java
    │   │   └── ItemRequest.java
    │   ├── security/
    │   │   ├── SecurityConfig.java
    │   │   ├── JwtAuthFilter.java
    │   │   ├── JwtService.java
    │   │   └── CustomUserDetailsService.java
    │   └── service/
    │       ├── EmailService.java
    │       ├── AzureBlobService.java
    │       └── SqlItemService.java
    └── resources/
        └── application.properties
data/
└── migrations/                               # SQL migration scripts
```

---

## Data Model

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `nus_student_id` | varchar | Unique |
| `first_name` | varchar(100) | |
| `last_name` | varchar(100) | |
| `email` | varchar(255) | Unique |
| `phone` | varchar(30) | |
| `password_hash` | varchar(100) | BCrypt |
| `user_type` | varchar(30) | `student` (default) |
| `status` | varchar(30) | `active` (default) |
| `created_at` | datetime2 | |
| `updated_at` | datetime2 | |

### `study_groups`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `name` | varchar | |
| `module_code` | varchar | e.g. `CS5001` |
| `description` | varchar | |
| `topic` | varchar | |
| `study_mode` | varchar | `online` / `in-person` / `hybrid` |
| `location` | varchar | For in-person |
| `meeting_link` | varchar | For online |
| `preferred_schedule` | datetime2 | |
| `max_members` | smallint | Default: 10 |
| `approval_required` | bit | Whether join requests need approval |
| `status` | varchar | `active` / `dissolved` |
| `created_by` | UUID | FK → users |
| `created_at` | datetime2 | |

### `study_group_members`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `group_id` | UUID | FK → study_groups |
| `user_id` | UUID | FK → users |
| `role` | varchar | `owner` / `member` |
| `membership_status` | varchar | `invited` / `pending` / `approved` |
| `joined_at` | datetime2 | |

### `study_sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `group_id` | UUID | FK → study_groups |
| `title` | varchar | |
| `notes` | varchar | |
| `starts_at` | datetime2 | |
| `ends_at` | datetime2 | |
| `location` | varchar | |
| `meeting_link` | varchar | |
| `created_by` | UUID | FK → users |
| `created_at` | datetime2 | |

### `tutoring_classes`
Stores peer-tutoring sessions offered by tutors with subject, schedule, capacity, and enrollment count.

### `tutoring_enrollments`
Maps students to tutoring classes they have enrolled in.

### `peer_feedback`
Stores ratings and written feedback from one user to another, linked to a tutoring class.

### `profiles`
Extended profile info for users: faculty, year of study, bio, avatar URL.

### `restricted_users`
Blocker/blocked user pairs for the user-restriction feature.

### `password_reset_tokens`
Short-lived tokens used in the forgot-password / reset-password flow.

---

## API Reference

All endpoints are prefixed with `/api`. Endpoints other than `/api/auth/**` require a valid JWT `Authorization: Bearer <token>` header.

Interactive Swagger UI is available at `/swagger-ui.html` when the server is running.

### Authentication — `/api/auth`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Log in, receive JWT |
| `POST` | `/api/auth/forgot-password` | Send password-reset email (rate-limited: 2 min) |
| `POST` | `/api/auth/reset-password` | Reset password using token |

### Users — `/api/users`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/users/me` | Get current authenticated user |

### Profile — `/api/profile`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/profile` | Get current user's profile |
| `PUT` | `/api/profile` | Update profile fields |
| `POST` | `/api/profile/avatar` | Upload avatar image (PNG/JPEG, max 2 MB) |

### Study Groups — `/api/groups`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/groups` | List all study groups |
| `POST` | `/api/groups` | Create a new study group |
| `GET` | `/api/groups/{id}` | Get a single group with members |
| `PUT` | `/api/groups/{id}` | Update group details (owner only) |
| `DELETE` | `/api/groups/{id}` | Dissolve a group (owner only) |
| `POST` | `/api/groups/{id}/join` | Request to join a group |
| `POST` | `/api/groups/{id}/invite` | Invite a user to the group (owner) |
| `POST` | `/api/groups/{id}/approve/{userId}` | Approve a pending member (owner) |
| `POST` | `/api/groups/{id}/reject/{userId}` | Reject a pending member (owner) |
| `DELETE` | `/api/groups/{id}/members/{userId}` | Remove a member / leave group |
| `GET` | `/api/groups/{id}/sessions` | List sessions for a group |
| `POST` | `/api/groups/{id}/sessions` | Create a study session |
| `PUT` | `/api/groups/{id}/sessions/{sessionId}` | Update a study session |
| `DELETE` | `/api/groups/{id}/sessions/{sessionId}` | Delete a study session |

### Tutoring — `/api/tutoring`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/tutoring/classes` | List all tutoring classes |
| `POST` | `/api/tutoring/classes` | Create a tutoring class |
| `DELETE` | `/api/tutoring/classes/{id}` | Delete a tutoring class |
| `POST` | `/api/tutoring/classes/{id}/enroll` | Enroll in a class |
| `DELETE` | `/api/tutoring/classes/{id}/enroll` | Withdraw enrollment |
| `POST` | `/api/tutoring/feedback` | Submit peer feedback |
| `DELETE` | `/api/tutoring/feedback/{id}` | Delete peer feedback |

### Restricted Users — `/api/restricted-users`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/restricted-users` | List users blocked by current user |
| `POST` | `/api/restricted-users` | Block a user by ID |
| `DELETE` | `/api/restricted-users/{userId}` | Unblock a user |

---

## Security

- **Authentication:** Stateless JWT via `Authorization: Bearer <token>` header.
- **Token expiry:** 15 minutes (configurable via `app.jwt.access.token.minutes`).
- **Password hashing:** BCrypt.
- **CORS:** Configured to allow `http://localhost:*`, `http://127.0.0.1:*`, and the production Azure Static Web App origin.
- **Public routes:** `GET /`, `/api/auth/**`, Swagger UI docs, and preflight `OPTIONS`.
- **Password reset:** Rate-limited to one email per address every 2 minutes; tokens expire server-side.

---

## Email Notifications

Emails are sent via Gmail SMTP (`peerconnectsg@gmail.com`). The `EmailService` sends:

| Event | Recipients |
|---|---|
| Group invitation sent | Invited user (owner CC'd) |
| Member approved | Approved member (owner CC'd) |
| Member rejected | Rejected member (owner CC'd) |
| Group dissolved | All approved members (batch) |
| Password reset link | Requesting user |

---

## File Storage

Avatar images are stored in **Azure Blob Storage** (container `avatars`).

- Accepted formats: `image/png`, `image/jpeg`
- Maximum size: 2 MB
- Upload via `POST /api/profile/avatar` (multipart)
- Connection string is read from the `AZURE_STORAGE_CONNECTION_STRING` environment variable (falls back to the value in `application.properties`).

---

## Running Locally

**Prerequisites:** Java 21

```bash
./gradlew bootRun
```

> If `bootRun` fails on Azure SQL schema validation for `peer_feedback` or `study_groups.preferred_schedule`, apply the repair migration to the target database first:
> ```
> data/migrations/2026-03-21_repair_azure_sql_schema.sql
> ```

The server starts on `http://localhost:8080` by default.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Environment Variables

| Variable | Purpose |
|---|---|
| `MAIL_PASSWORD` | Gmail App Password for SMTP |
| `AZURE_STORAGE_CONNECTION_STRING` | Azure Blob Storage connection string |
| `GH_PACKAGES_OWNER` | GitHub org/user for package registry |
| `GH_PACKAGES_REPO` | GitHub repo name |
| `GH_PACKAGES_USER` | GitHub username |
| `GH_PACKAGES_TOKEN` | GitHub Personal Access Token |

Variables not provided fall back to the defaults configured in `application.properties`.

---

## Building & Packaging

```bash
# Compile and run tests
./gradlew build

# Run the packaged jar
java -jar build/libs/peerconnect-0.0.1-SNAPSHOT.jar

# Generate JaCoCo coverage report
./gradlew jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Run SonarQube analysis
./gradlew sonar
```

---

## Docker

A `Dockerfile` is included in the project root. Build and run:

```bash
docker build -t peerconnect .
docker run -p 8080:8080 \
  -e MAIL_PASSWORD=<your-password> \
  -e AZURE_STORAGE_CONNECTION_STRING=<your-conn-string> \
  peerconnect
```

---

## GitHub Packages

The project is configured to resolve dependencies from and publish artifacts to GitHub Packages.

Set credentials in `~/.gradle/gradle.properties`:

```properties
gpr.owner=<github-org-or-user>
gpr.repo=<github-repo>
gpr.user=<github-username>
gpr.key=<github-personal-access-token>
```

Or via environment variables: `GH_PACKAGES_OWNER`, `GH_PACKAGES_REPO`, `GH_PACKAGES_USER`, `GH_PACKAGES_TOKEN`.

```bash
./gradlew publish
```

---

## Database Migrations

Manual SQL migration scripts are stored in `data/migrations/`. Apply these to the Azure SQL database when schema changes are needed:

| File | Description |
|---|---|
| `2026-03-17_create_peer_feedback.sql` | Creates the `peer_feedback` table |
| `2026-03-21_repair_azure_sql_schema.sql` | Fixes Azure SQL schema for `peer_feedback` and `study_groups.preferred_schedule` |

The application uses `spring.jpa.hibernate.ddl-auto=validate`, so the schema must be applied manually before the application starts.

---

## Testing

Test classes are located in `src/test/java/`. Run with:

```bash
./gradlew test
```

Test reports are generated in `build/reports/tests/test/`.

Key test suites:

| Suite | Coverage |
|---|---|
| `AuthControllerTest` | Register, Login, Microsoft Login flows |
| `CustomUserDetailsServiceTest` | User details loading |
| `EmailServiceTest` | Member approved email flow |
