# PeerConnect

## How to run

Prerequisites:
- Java 21

Run locally:
```
./gradlew bootRun
```

If you need JWT locally, provide `APP_JWT_SECRET`:
```
APP_JWT_SECRET=your-secret ./gradlew bootRun
```

Run with local Zipkin tracing:
```
docker compose up -d zipkin
APP_JWT_SECRET=your-secret ./gradlew bootRun
```

Tracing is exported to Zipkin at `http://localhost:9411/api/v2/spans` by default.
Override this with `ZIPKIN_ENDPOINT` when needed.
Docker Compose integration is disabled by default, so Zipkin is opt-in for local runs.

Build a runnable jar:
```
./gradlew build
java -jar build/libs/peerconnect-0.0.1-SNAPSHOT.jar
```

Open the Zipkin UI at `http://localhost:9411` to inspect traces after making requests to the app.

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

## SonarQube with JaCoCo

This project generates JaCoCo XML coverage for SonarQube during the Gradle `sonar` task.

Run locally:
```
./gradlew build sonar --info
```

Coverage report path used by SonarQube:
```
build/reports/jacoco/test/jacocoTestReport.xml
```

For GitHub Actions, add these repository settings first:
- secret: `SONAR_TOKEN`
- variable: `SONAR_HOST_URL`

An example workflow is included at `.github/workflows/sonar.yml`.

If your SonarQube project key is not configured on the server side, pass it in Gradle as well:
```
./gradlew build sonar -Dsonar.projectKey=your-project-key --info
```
