# PeerConnect

## How to run

Prerequisites:
- Java 21

Run locally:
```
./gradlew bootRun
```

If `bootRun` fails on Azure SQL schema validation for `peer_feedback` or `study_groups.preferred_schedule`, apply [data/migrations/2026-03-21_repair_azure_sql_schema.sql](data/migrations/2026-03-21_repair_azure_sql_schema.sql) to the target database first.

Audit trail migration:
- apply [data/migrations/2026-03-27_create_audit_event.sql](data/migrations/2026-03-27_create_audit_event.sql) to the target database before starting the app with schema validation enabled.

Optional telemetry integrations:
- Azure Monitor activates automatically when `APPLICATIONINSIGHTS_CONNECTION_STRING` is set.
- Sentry can be enabled through Spring environment variables such as `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, and `SENTRY_RELEASE`.

Build a runnable jar:
```
./gradlew build
java -jar build/libs/peerconnect-0.0.1-SNAPSHOT.jar
```

## Test locally

Validate the main build, tests, and JaCoCo coverage report locally with:
```
./gradlew clean build
```

This verifies that the project compiles, the test suite passes, and the JaCoCo XML report is generated at:
```
build/reports/jacoco/test/jacocoTestReport.xml
```

If SonarCloud is configured, you can also run the analysis locally after setting `SONAR_TOKEN`, `SONAR_ORGANIZATION`, and `SONAR_PROJECT_KEY`:
```
./gradlew classes testClasses sonar \
  -x test \
  -x jacocoTestReport \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.organization="$SONAR_ORGANIZATION" \
  -Dsonar.projectKey="$SONAR_PROJECT_KEY"
```

For this repository, `SONAR_ORGANIZATION` should be:
```
nus-mtech-se-projects
```

### How to get the SonarCloud values

`SONAR_TOKEN`
- Log in to SonarCloud.
- Click your profile icon.
- Go to `My Account` -> `Security`.
- Generate a token and copy it immediately.
- Use that value for `SONAR_TOKEN`.
- The token must have permission to execute analysis for the project.

`SONAR_ORGANIZATION`
- Open the SonarCloud organization used by this repository.
- For this project, the organization key is:
```
nus-mtech-se-projects
```

`SONAR_PROJECT_KEY`
- Open the PeerConnect project in SonarCloud.
- Go to the project settings or project information page.
- Copy the exact project key shown there.
- For this repository, the project key is:
```
nus-mtech-se-projects_PeerConnect
```

For GitHub Actions, add these repository settings:
- Secret: `SONAR_TOKEN`
- Variable: `SONAR_HOST_URL=https://sonarcloud.io`
- Variable: `SONAR_ORGANIZATION=nus-mtech-se-projects`
- Variable: `SONAR_PROJECT_KEY=nus-mtech-se-projects_PeerConnect`

If SonarCloud manual analysis fails with a message about Automatic Analysis being enabled, disable `Automatic Analysis` in the SonarCloud project settings and keep the Gradle-based analysis from CI.

## GitHub workflow

Pushing to GitHub is different from local testing because it validates the actual GitHub Actions pipeline, not just the Gradle commands on your machine.

The main CI/CD workflow runs on pushes to `main` and on manual dispatch. It:
- builds and tests the application
- uploads the deployment artifact and JaCoCo report
- runs SonarCloud analysis if configured
- runs the Snyk dependency scan if configured
- deploys the application to Azure Web App
- runs Postman UAT against the deployed application

Local testing is the fast way to catch build and test problems early. Pushing to GitHub verifies the full automation path, including repository secrets and variables, artifact passing between jobs, SonarCloud, Azure deployment, and post-deploy checks.

## GitHub Packages (Gradle)

This project is configured to:
- resolve dependencies from GitHub Packages
- publish artifacts to GitHub Packages

Set credentials via Gradle properties (`~/.gradle/gradle.properties`) or environment variables.

Gradle properties:
```
gpr.owner=<github-org-or-user>
gpr.repo=<github-repo>
gpr.user=<github-username>
gpr.key=<github-personal-access-token>
```

Environment variable fallbacks:
- `GH_PACKAGES_OWNER`
- `GH_PACKAGES_REPO`
- `GH_PACKAGES_USER`
- `GH_PACKAGES_TOKEN` (store this as a GitHub Actions secret)

Publish command:
```
./gradlew publish
```
