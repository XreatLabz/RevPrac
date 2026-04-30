# Building RevPrac

RevPrac uses Gradle 9.5.0, Kotlin DSL, and a Java 21 toolchain. Use the checked-in Gradle wrapper instead of a system Gradle install.

## Common Commands

```bash
./gradlew spotlessCheck
./gradlew test
./gradlew jacocoTestReport
./gradlew jar
./gradlew spotlessCheck test jacocoTestReport jar
```

The plugin JAR is written to `build/libs/`.

## Runtime Smoke

Run the Paper smoke check after building the JAR:

```bash
./scripts/smoke-run-paper.sh
```

The script downloads the latest stable Paper build for Minecraft `1.21.11`, installs the built RevPrac JAR into a temporary server under `build/smoke/`, accepts the local EULA for that throwaway server, and fails unless the log contains `RevPrac enabled`.

## Dependency Updates

Dependency versions live in `gradle/libs.versions.toml`.

Use stable releases by default. Avoid RC, milestone, beta, or snapshot dependencies unless a documented feature requires them. The Paper API remains `1.21.11-R0.1-SNAPSHOT` because Paper publishes the 1.21 line API in that format.

After dependency changes, refresh locks and verify:

```bash
./gradlew dependencies --write-locks
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

## CI

GitHub Actions runs:

```bash
./gradlew --no-daemon spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Dependabot is configured for Gradle and GitHub Actions updates.
