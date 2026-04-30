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

## Phase 1 Bootstrap Smoke

For Phase 1 bootstrap/config wiring, verify the lifecycle boundary as part of local smoke work:

- `plugin.yml` still names the `JavaPlugin` main class.
- `config.yml` is bundled in `src/main/resources/` and copied before config reads.
- `saveDefaultConfig()` or `saveResource("config.yml", false)` runs from `onEnable()`, not the constructor.
- `JavaPlugin#getConfig()` is only read after the config resource has been saved.

Suggested checks:

```bash
./gradlew test
./scripts/smoke-run-paper.sh
rg -n "saveDefaultConfig|saveResource|getConfig|onLoad|onEnable|onDisable" src/main/java src/main/resources
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java
```

The import check should only report `RevPracPlugin`, `bootstrap` if unavoidable, and `adapters.paper`. It should not report `application` or `ports` packages.

## Phase 2 Player Session Safety

For player-session work, run the focused pure-domain, application-service, storage, Paper-adapter, and plugin lifecycle tests before the full gate:

```bash
./gradlew test --tests '*PlayerContextContractTest' --tests '*PlayerSnapshotContractTest' --tests '*PlayerSessionTransitionPolicyTest'
./gradlew test --tests '*PlayerSessionServiceTest' --tests '*InMemoryPlayerSessionRepositoryTest' --tests '*InMemoryPendingRestorationRepositoryTest'
./gradlew test --tests '*PaperPlayerStateAdapterTest' --tests '*PaperPlayerSessionListenerTest' --tests '*RevPracPluginSessionSafetyTest'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

The import check should return no matches. Paper player snapshot and event behavior belongs under `adapters.paper.players`; in-memory session storage belongs under `adapters.storage`.

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
