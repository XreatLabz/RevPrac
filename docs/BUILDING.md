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

## Phase 5 Queues And Matchmaking

For queue and matchmaking work, run the focused domain/config, service, storage, Paper-adapter, command, and plugin tests before the full gate:

```bash
./gradlew test --tests '*QueueTicketContractTest' --tests '*MatchmakingWindowPolicyContractTest'
./gradlew test --tests '*QueueServiceTest' --tests '*QueueMatchmakingServiceTest' --tests '*InMemoryQueueTicketRepositoryTest' --tests '*InMemoryQueueRatingRepositoryTest'
./gradlew test --tests '*PaperQueueLifecycleListenerTest' --tests '*PaperQueueTickerTest' --tests '*RevPracQueueCommandTest' --tests '*RevPracPluginPhase5Test'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application/queues src/main/java/io/github/xreatlabz/revprac/domain/queues src/main/java/io/github/xreatlabz/revprac/ports/queues src/main/java/io/github/xreatlabz/revprac/adapters/storage
```

The import check should return no matches for queue domain, application, ports, or storage code. `application.config.QueueConfig` owns `queues.matchmaking-period-ticks`, `queues.ranked-base-rating`, `queues.ticks-per-second`, and `queues.ranked-windows`; queue matching lives in `application.queues.QueueService` and `application.queues.QueueMatchmakingService`; queue tickets stay in memory while ranked search ratings are seeded from durable player ratings; Paper command parsing, listener wiring, ticker scheduling, and plugin tests belong under `adapters.paper.commands`, `adapters.paper.queues`, and the plugin boundary.

## Phase 6A Persistence Ratings

For the durable player profile and rating slice, run the focused storage, config, and plugin lifecycle tests before the full gate. This covers SQLite and the optional PostgreSQL backend, including active-season scoping:

```bash
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test'
```

Boundary checks:

```bash
rg -n "storage\\.|JdbcStorageFactory|JdbcStorageRuntime|Flyway|HikariCP|sqlite-jdbc|PlayerProfileRepository|PlayerRatingRepository" src/main/java src/main/resources
rg -n "import (java\\.sql|javax\\.sql|org\\.flywaydb|org\\.sqlite|org\\.postgresql|com\\.zaxxer\\.hikari)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

The import check should return no matches in application, domain, or ports. JDBC, HikariCP, and Flyway belong under `adapters.storage.jdbc`. `src/main/resources/plugin.yml` declares the runtime libraries, and `JdbcStorageFactory` should fail closed on invalid paths or migration errors before repositories are exposed. The PostgreSQL storage tests use Testcontainers and skip cleanly when Docker is unavailable, so a missing Docker daemon should not fail the suite.

## Phase 6C Ranked Progression, Stats, And Records

For ranked progression, `/stats`, and `/records` work, run the focused rating, settlement, query, command, storage, and plugin tests before the full gate:

```bash
./gradlew test --tests '*RatingServiceTest' --tests '*MatchSettlementServiceTest' --tests '*PlayerRecordQueryServiceTest'
./gradlew test --tests '*PlayerDirectoryServiceTest' --tests '*PlayerRecordTransferServiceTest' --tests '*RevPracStatsCommandTest' --tests '*RevPracRecordsCommandTest'
./gradlew test --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application/ratings src/main/java/io/github/xreatlabz/revprac/application/players
```

The import check should return no matches. Ranked progression belongs in `application.ratings`, player lookup/query/transfer orchestration belongs in `application.players`, `/stats` stays self-only with `revprac.stats` defaulting to `true`, and `/records` parsing plus YAML transfer files belong under `adapters.paper`.

## Phase 6 Task 3 Rematch And Post-Match Summaries

For rematch and post-match summary work, run the focused application, command, lifecycle, settlement, and plugin tests before the wider gate:

```bash
git diff --check
./gradlew test --tests '*RematchServiceTest' --tests '*PostMatchSummaryServiceTest' --tests '*RevPracDuelCommandTest'
./gradlew test --tests '*MatchLifecycleServiceTest' --tests '*MatchSettlementServiceTest' --tests '*RevPracPluginPhase6Test'
```

Boundary checks:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application/matches src/main/java/io/github/xreatlabz/revprac/application/ratings src/main/java/io/github/xreatlabz/revprac/ports/matches
rg -n "rematch|PostMatchSummary|rating=|result=win|result=loss|result=draw" src/main/java src/test/java
```

The import check should return no matches in `application.matches`, `application.ratings`, or `ports.matches`. Rematch must reuse the current history/replay boundary instead of adding new command-side business logic, and post-match summaries must remain best-effort after successful teardown only.

## Phase 6 Task 4 Runtime Recovery Sidecars

For active queue, active match, player-session, and pending-restoration recovery work, run the focused service, storage, listener, and plugin tests before the wider gate:

```bash
./gradlew test --tests '*RuntimeRecoveryServiceTest' --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest'
./gradlew test --tests '*PaperPlayerSessionListenerTest' --tests '*QueueServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*RevPracPluginPhase6Test'
```

Recovery keeps the live repositories in memory and mirrors safe state into JDBC sidecar tables. Pairing queue tickets recover as searching, offline queue tickets recover lazily on join, active matches restart from a fresh countdown only when both combatants are online, and spectators are not recovered.

## Phase 7 Staff Operations And Events

For staff operations, safe registry reload, integration probes, audit, metrics, season commands, and public event bridging, run:

```bash
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*PaperMatchEventBridgeTest' --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest'
```

Safe partial reload is registry-only: `/revprac reload registries` reloads arena and kit YAML after validation and refuses to run while queue tickets, matches, or arena reservations are active. Public plugin events are versioned through `RevPracMatchEvent.CONTRACT_VERSION`.

## Phase 8 Hardening Base

For the initial hardening base, party domain, and tournament domain, run:

```bash
./gradlew test --tests '*PartyTest' --tests '*PartyServiceTest' --tests '*InMemoryPartyRepositoryTest'
./gradlew test --tests '*TournamentTest' --tests '*TournamentServiceTest' --tests '*InMemoryTournamentRepositoryTest'
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*JdbcStorageFactoryTest'
```

Phase 8 currently provides staff-controlled season rollover, durable audit rows, lightweight metrics, minimal in-memory party membership, and minimal in-memory tournament lifecycle services. Rich party matchmaking, tournament commands, load-test harnesses, and physical PostgreSQL partitioning are future expansion points.

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
