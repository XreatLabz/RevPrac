# Phase 6A Persistence Ratings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Introduce the first durable persistence slice for RevPrac: storage configuration, SQLite-backed migrations, durable player profiles, and durable queue rating seeds.

**Architecture:** Keep active queues, matches, duel requests, player sessions, and pending restorations in memory. Add durable player-data ports and JDBC/Flyway adapters behind bootstrap wiring, then route ranked queue seed lookup through an application rating service. Do not use the current best-effort match event sink for authoritative stats or rating settlement.

**Tech Stack:** Java 21, Gradle 9.5.0, Paper 1.21.11 API, HikariCP, Flyway Java API, SQLite JDBC, JUnit Jupiter, MockBukkit.

---

## Research Decisions

- Use `domain.ratings` as the rating boundary; keep player profile identity in `domain.players`.
- Use SQLite as the default local durable store at `plugins/RevPrac/data/revprac.db`.
- Add config under `storage.*`; keep `config-version: 1` because this is plugin config versioning, not database schema versioning.
- Run Flyway migrations during `onEnable()` after config validation and before commands/listeners/tickers are registered.
- Close the storage runtime after queue, match, and player teardown in `BootstrapRuntime.shutdown()`.
- Keep PostgreSQL support deferred to Phase 6C; do not add a PostgreSQL adapter without Testcontainers parity in the same PR.
- Keep import/export deferred to a later Phase 6 sub-slice; land the durable spine first.

## Task 1: Dependency And Config Boundary

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/config.yml`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/config/StorageConfig.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/config/RevPracConfig.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Modify: `src/test/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigServiceContractTest.java`

- [x] Add dependencies:
  - `com.zaxxer:HikariCP:7.0.2`
  - `org.flywaydb:flyway-core:12.5.0`
  - `org.xerial:sqlite-jdbc:3.53.0.0`
- [x] Add `StorageConfig` as a record with:
  - `backend`, default `sqlite`
  - `sqlitePath`, default `data/revprac.db`
  - `poolMaximumSize`, default `4`
  - validation for supported backend and positive pool size
- [x] Parse `storage` as an optional section and fail closed on malformed values.
- [x] Add config defaults to `src/main/resources/config.yml`.
- [x] Write RED config tests for defaults, explicit values, invalid backend, invalid pool size, malformed storage parent, and no Bukkit/Paper imports.
- [x] Run focused RED/GREEN:
  - `./gradlew test --tests '*LoadValidatedConfigServiceContractTest'`

## Task 2: Durable Profile And Rating Contracts

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerProfile.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/ratings/PlayerRating.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerProfileRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/ratings/PlayerRatingRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerProfileService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/ratings/RatingService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueService.java`
- Modify: `src/test/java/io/github/xreatlabz/revprac/application/queues/QueueServiceTest.java`
- Create: matching domain/application tests under `src/test/java/io/github/xreatlabz/revprac/...`

- [x] Add `PlayerProfile` with `PlayerId`, optional last-known name, first-seen instant, and last-seen instant.
- [x] Add `PlayerRating` with `PlayerId`, `KitId`, positive rating, wins, losses, and updated-at instant.
- [x] Add repository ports that expose narrow upsert/find operations and no JDBC types.
- [x] Add `PlayerProfileService.touch(PlayerId, String, Instant)` for join/name updates.
- [x] Add `RatingService.ratingForQueue(PlayerId, KitId, int defaultRating)` and `saveSeed(...)`.
- [x] Replace `QueueService`'s direct `QueueRatingRepository` dependency with `RatingService` while preserving rollback behavior on lookup failure.
- [x] Write RED tests for rating validation, profile touch behavior, default rating fallback, persisted rating lookup, and queue rollback on rating service failure.
- [x] Run focused RED/GREEN:
  - `./gradlew test --tests '*PlayerProfile*Test' --tests '*PlayerRating*Test' --tests '*RatingServiceTest' --tests '*QueueServiceTest'`

## Task 3: SQLite/Flyway Storage Adapters

**Files:**
- Create: `src/main/resources/db/migration/V1__create_player_data.sql`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageRuntime.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageFactory.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcPlayerProfileRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcPlayerRatingRepository.java`
- Create: storage adapter tests under `src/test/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/`

- [x] Add Flyway migration for `player_profiles` and `player_ratings`.
- [x] Use durable text columns for UUIDs and kit IDs; use integer epoch-millis columns for instants.
- [x] Add a storage factory that builds an SQLite Hikari datasource from plugin data folder + `StorageConfig`.
- [x] Run Flyway migration immediately before repositories are exposed.
- [x] Repositories must use transactions for upserts and convert SQL exceptions into `IllegalStateException`.
- [x] Tests must use `@TempDir` and a real SQLite file, not `:memory:`.
- [x] Write RED tests proving empty DB migrates, profile/rating survives close-and-reopen, current schema migration is idempotent, and duplicate upserts update rather than duplicate.
- [x] Run focused RED/GREEN:
  - `./gradlew test --tests '*Jdbc*Test'`

## Task 4: Bootstrap Wiring And Lifecycle

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerSessionListener.java`
- Modify: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase6Test.java`

- [x] Add Paper/Spigot `plugin.yml` library metadata for non-bundled runtime libraries:
  - `com.zaxxer:HikariCP:7.0.2`
  - `org.flywaydb:flyway-core:12.5.0`
  - `org.xerial:sqlite-jdbc:3.53.0.0`
- [x] Wire `JdbcStorageRuntime` into bootstrap after config and registry load.
- [x] Use `JdbcPlayerRatingRepository` through `RatingService` for queue rating seed lookup.
- [x] Keep `InMemoryQueueTicketRepository`, `InMemoryMatchRepository`, `InMemoryDuelRequestRepository`, `InMemoryPlayerSessionRepository`, and `InMemoryPendingRestorationRepository`.
- [x] Add storage runtime close as the final shutdown step, preserving queue/match/player shutdown ordering.
- [x] Touch player profiles from the Paper join path without adding Paper imports to application/domain/ports.
- [x] Add plugin lifecycle tests for storage config defaults, migration failure fail-closed behavior if practical, and storage runtime close on disable.
- [x] Run focused RED/GREEN:
  - `./gradlew test --tests '*RevPracPluginPhase6Test' --tests '*PaperPlayerSessionListenerTest' --tests '*QueueServiceTest'`

## Task 5: Docs And Verification

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUILDING.md`
- Modify: `docs/DECISIONS.md`
- Modify: `gradle.lockfile`

- [x] Document Phase 6A as implemented and explicitly defer match-history settlement, stats, seasons, PostgreSQL, and import/export.
- [x] Document storage config keys, dependency choices, migration behavior, and focused verification commands.
- [x] Record the Phase 6A decision in `docs/DECISIONS.md`.
- [x] Refresh dependency locks with `./gradlew dependencies --write-locks`.
- [x] Run boundary scans:
  - `rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports`
  - `rg -n "import (java\\.sql|javax\\.sql|org\\.flywaydb|org\\.sqlite|org\\.postgresql|com\\.zaxxer\\.hikari)" src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/ports`
- [x] Run full verification:
  - `./gradlew spotlessCheck test jacocoTestReport jar`
  - `./scripts/smoke-run-paper.sh`
  - `git diff --check`

## Final Review

- [x] Dispatch a reviewer for spec compliance against this plan.
- [x] Dispatch a reviewer for code quality and architecture boundaries.
- [x] Fix valid findings.
- [x] Push `feature/phase-6-persistence-ratings` and open a pull request against `main`.
