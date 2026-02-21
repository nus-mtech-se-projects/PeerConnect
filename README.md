# PeerConnect

## How to run

Prerequisites:
- Java 21

Run locally:
```
./gradlew bootRun
```

Build a runnable jar:
```
./gradlew build
java -jar build/libs/peerconnect-0.0.1-SNAPSHOT.jar
```

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
