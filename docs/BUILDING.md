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

## Phase 3 Arena and Kit Registries

For arena and kit registry work, run the focused pure-domain, application-service, storage, Paper-adapter, and command/plugin tests before the full gate:

```bash
./gradlew test --tests '*ArenaDefinitionContractTest' --tests '*KitDefinitionContractTest'
./gradlew test --tests '*ArenaRegistryServiceTest' --tests '*KitRegistryServiceTest' --tests '*InMemoryArenaRegistryRepositoryTest' --tests '*InMemoryKitRegistryRepositoryTest'
./gradlew test --tests '*PaperArenaRegistryFilesTest' --tests '*PaperKitLoadoutAdapterTest' --tests '*PaperKitRegistryFilesTest'
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*RevPracPluginPhase3Test'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

The import check should return no matches. Arena and kit domain/application logic belongs under `domain.arenas`, `domain.kits`, `application.arenas`, and `application.kits`. Paper YAML files, command parsing, player inventory capture/apply, and reset logging belong under `adapters.paper`.

Operator-managed registry files are `arenas.yml` and `kits.yml` in the plugin data folder. Bad registry content should fail startup through the existing bootstrap failure path.

## Phase 4 Duel and Match Engine

For duel and match work, run the focused pure-domain, application-service, storage, Paper-adapter, command, and config/bootstrap tests before the full gate:

```bash
./gradlew test --tests '*DuelRequestServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*InMemoryDuelRequestRepositoryTest' --tests '*InMemoryMatchRepositoryTest'
./gradlew test --tests '*PaperMatchLifecycleListenerTest' --tests '*PaperMatchPlayerAdapterTest' --tests '*PaperMatchTickerTest' --tests '*RevPracDuelCommandTest'
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*RevPracPluginPhase4Test'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application/matches src/main/java/io/github/xreatlabz/revprac/domain/matches src/main/java/io/github/xreatlabz/revprac/ports/matches src/main/java/io/github/xreatlabz/revprac/adapters/storage
rg -n "domain\\.matches|application\\.matches|ports\\.matches|adapters\\.paper\\.matches|RevPracDuelCommand|MatchConfig|BootstrapRuntime|RevPracBootstrap|plugin.yml|config.yml" src/main/java src/main/resources
```

The import check should return no matches in application, domain, ports, or storage. Match lifecycle logic belongs under `domain.matches` and `application.matches`; Paper event handling, ticker scheduling, and command parsing belong under `adapters.paper.matches` and `adapters.paper.commands`.

The Phase 4 runtime uses in-memory match and duel-request state, `/duel <player> <arena> <kit>` for normal requests, `/duel request <player> <arena> <kit>` when the target name collides with a subcommand, lifecycle subcommands for active duels, and `matches.*` config defaults for request expiry, countdown, max duration, and spectator enablement.

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
