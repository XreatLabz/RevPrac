# Phase 6 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Completed on 2026-05-17. The implementation also added the Phase 7 staff operations/public event slice and the Phase 8 hardening base that now appear in the roadmap.

**Goal:** Finish the remaining Phase 6 persistence, records, recovery, rematch, and documentation scope so the roadmap can mark Phase 6 fully complete.

**Architecture:** Keep Paper adapters thin, keep application/domain/ports free of Bukkit imports, and preserve the existing settlement-before-teardown safety contract. Add PostgreSQL as an optional backend, introduce logical active-season scoping now, use season-scoped `(season_id, match_id)` history idempotency, and defer physical PostgreSQL table partitioning until a later identity design explicitly requires it.

**Tech Stack:** Java 21, Paper API 1.21.11, Gradle Kotlin DSL, JUnit Jupiter, MockBukkit, HikariCP, Flyway, SQLite JDBC, PostgreSQL JDBC, Testcontainers PostgreSQL.

---

## File Structure

- Storage and seasons:
  - Modify `gradle/libs.versions.toml`, `build.gradle.kts`, `src/main/resources/plugin.yml`, `src/main/resources/config.yml`.
  - Modify `application.config.StorageConfig` and `LoadValidatedConfigService`.
  - Add backend-specific Flyway locations under `src/main/resources/db/migration/sqlite/` and `src/main/resources/db/migration/postgresql/`, preserving current V1/V2 contents and adding V3 active-season migrations.
  - Add `domain.seasons`, `ports.seasons`, `application.seasons`, and JDBC season repository/runtime wiring.
  - Modify JDBC profile/rating/settlement repositories to operate against the active season where appropriate.

- Records, transfer, rematch, and summaries:
  - Add `PlayerDirectoryService`, `PlayerRecordTransferService`, `RevPracRecordsCommand`, and a YAML transfer file adapter.
  - Add `RematchService`, `PostMatchSummaryService`, and `PostMatchSummaryPort`.
  - Modify `RevPracDuelCommand`, `RevPracBootstrap`, `BootstrapRuntime`, `plugin.yml`, repository ports, and JDBC implementations.

- Runtime recovery:
  - Add `domain.recovery`, `ports.recovery`, `application.recovery.RuntimeRecoveryService`, and JDBC recovery repository.
  - Extend `PlayerSessionService`, `QueueService`, `MatchLifecycleService`, `PaperPlayerSessionListener`, bootstrap, and runtime shutdown wiring with sidecar persistence hooks.
  - Keep live `MatchRepository` and `QueueTicketRepository` in memory.

- Docs:
  - Update `ROADMAP.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, `docs/DECISIONS.md`, and this plan as tasks complete.

## Task 1: PostgreSQL Backend And Logical Seasons

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/config/StorageConfig.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageFactory.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageRuntime.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcPlayerRatingRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/seasons/SeasonId.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/seasons/Season.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/seasons/SeasonRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/seasons/SeasonService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcSeasonRepository.java`
- Create/Move: backend-specific migration files under `src/main/resources/db/migration/sqlite/` and `src/main/resources/db/migration/postgresql/`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigServiceContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageFactoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/PostgresJdbcStorageFactoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase6Test.java`

- [ ] Add PostgreSQL config with `storage.backend: sqlite|postgresql`, `storage.postgresql.jdbc-url`, `storage.postgresql.username`, `storage.postgresql.password`, and optional `storage.postgresql.schema`.
- [ ] Keep SQLite defaults byte-compatible for existing users.
- [ ] Add `org.postgresql:postgresql`, `org.flywaydb:flyway-database-postgresql`, `org.testcontainers:postgresql`, and `org.testcontainers:junit-jupiter` with dependency locks refreshed.
- [ ] Split Flyway locations by backend. SQLite keeps current schema behavior; PostgreSQL uses `bigint` for epoch-millis columns.
- [ ] Add a seeded active season row and active-season service. Use `default` as the initial season id.
- [ ] Scope ratings, stats, and history reads/writes to the active season while keeping `player_profiles` global.
- [ ] Replace SQLite-only scalar `max(...)` upsert logic with portable `case when ... then ... else ... end`.
- [ ] Add Testcontainers PostgreSQL coverage for migrations, reopen behavior, idempotent settlement, rollback, recent-history ordering, and active-season scoping.
- [ ] Run:

```bash
./gradlew dependencies --write-locks
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test'
```

Expected: all selected tests pass.

## Task 2: Records Lookup And Import/Export

**Files:**
- Modify: `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerProfileRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/ports/ratings/PlayerRatingRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`
- Modify: JDBC and in-memory repository implementations as needed
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerDirectoryService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerRecordTransferService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerRecordBundle.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracRecordsCommand.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerRecordTransferFiles.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/resources/plugin.yml`
- Test: `PlayerDirectoryServiceTest`, `PlayerRecordTransferServiceTest`, `RevPracRecordsCommandTest`, `JdbcStorageFactoryTest`, `RevPracPluginPhase6Test`

- [ ] Add exact player selector resolution: UUID first, then exact case-insensitive `lastKnownName`.
- [ ] Fail ambiguous names with `Player name is ambiguous; use UUID: <name>.`.
- [ ] Add `/records summary <player> <kit>` and `/records history <player> [page]` with `revprac.records.lookup`.
- [ ] Add `/records export <player>` and `/records import <file>` with `revprac.records.transfer`.
- [ ] Store exports under `exports/player-records/<uuid>.yml`; read imports only from simple `.yml` filenames under `imports/player-records/`.
- [ ] Use schema-versioned YAML with profile, ratings, stats, and history sections. Imports must validate schema-version `1`, fail closed on malformed values, and be idempotent on repeat.
- [ ] Keep `/stats` self-only.
- [ ] Run:

```bash
./gradlew test --tests '*PlayerDirectoryServiceTest' --tests '*PlayerRecordTransferServiceTest' --tests '*RevPracRecordsCommandTest' --tests '*JdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test'
```

Expected: lookup, export/import, command permission, and wiring tests pass.

## Task 3: Rematch And Post-Match Summaries

**Files:**
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracDuelCommand.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/resources/plugin.yml`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/matches/RematchService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/matches/PostMatchSummaryService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/matches/PostMatchSummaryPort.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperPostMatchSummaryPort.java`
- Test: `RematchServiceTest`, `PostMatchSummaryServiceTest`, `RevPracDuelCommandTest`, `MatchLifecycleServiceTest`, `RevPracPluginPhase6Test`

- [ ] Add `/duel rematch <player>` using `revprac.duel`.
- [ ] Resolve the latest mutual completed match inside the existing duel-request expiry window.
- [ ] Reuse the previous arena and kit, then create a normal duel request. Acceptance stays `/duel accept <player>`.
- [ ] Send post-match plain-chat summaries to participants only after settlement and lobby return succeed.
- [ ] Skip summaries for shutdown outcomes.
- [ ] Make summary delivery best-effort; formatting or send failures must not block teardown.
- [ ] Include rating delta for ranked decisive outcomes when available; omit rating text otherwise.
- [ ] Run:

```bash
./gradlew test --tests '*RematchServiceTest' --tests '*PostMatchSummaryServiceTest' --tests '*RevPracDuelCommandTest' --tests '*MatchLifecycleServiceTest' --tests '*RevPracPluginPhase6Test'
```

Expected: rematch, summary, teardown, and command tests pass.

## Task 4: Active Match And Queue Recovery Sidecars

**Files:**
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerSessionService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerSessionListener.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageRuntime.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/recovery/*`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/recovery/RuntimeRecoveryRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/recovery/RuntimeRecoveryService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcRuntimeRecoveryRepository.java`
- Add migrations for runtime recovery tables in both backend migration locations
- Test: `RuntimeRecoveryServiceTest`, `PaperPlayerSessionListenerTest`, `QueueServiceTest`, `MatchLifecycleServiceTest`, `JdbcStorageFactoryTest`, `RevPracPluginPhase6Test`

- [ ] Persist managed player baselines and pending restorations needed for recovery.
- [ ] Persist active queue tickets as recovery rows with wall-clock join time; reload `PAIRING` as `SEARCHING`.
- [ ] Persist active match shells and retained completed matches. Do not persist spectators or exact mid-fight Bukkit state.
- [ ] Keep live match and queue repositories in memory.
- [ ] On bootstrap, hydrate recovery after storage and registries load, before tracking already-online players and before tickers start.
- [ ] Make queue and match recovery lazy for offline players. Do not place offline recovered queue tickets into the live in-memory queue.
- [ ] Restart recovered active matches from a fresh countdown when both combatants are online. Drop spectators.
- [ ] Delete recovery rows only after lobby restoration, match teardown, or queue leave succeeds.
- [ ] Run:

```bash
./gradlew test --tests '*RuntimeRecoveryServiceTest' --tests '*PaperPlayerSessionListenerTest' --tests '*QueueServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*JdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test'
```

Expected: recovery tests pass and existing player safety tests remain green.

## Task 5: Docs, Verification, And Phase 6 Closeout

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUILDING.md`
- Modify: `docs/DECISIONS.md`
- Modify: `docs/PRODUCT.md` only if user-facing scope changed materially.

- [ ] Mark Phase 6 as fully implemented.
- [ ] Remove the remaining Phase 6 items from deferred language and move anything genuinely not implemented into Phase 7+ or a new explicit future section.
- [ ] Record decisions for optional PostgreSQL support, logical active-season scoping, transfer artifact format, rematch TTL, post-match summaries, and recovery sidecar semantics.
- [ ] Add exact BUILDING verification commands for the completed Phase 6 gate.
- [ ] Run focused gates sequentially, then full verification:

```bash
git diff --check
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest' --tests '*Season*Test' --tests '*RevPracPluginPhase6Test'
./gradlew test --tests '*PlayerDirectoryServiceTest' --tests '*PlayerRecordTransferServiceTest' --tests '*RevPracRecordsCommandTest'
./gradlew test --tests '*RematchServiceTest' --tests '*PostMatchSummaryServiceTest' --tests '*RevPracDuelCommandTest'
./gradlew test --tests '*RuntimeRecoveryServiceTest' --tests '*PaperPlayerSessionListenerTest' --tests '*QueueServiceTest' --tests '*MatchLifecycleServiceTest'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Expected: all commands pass. If Testcontainers cannot run because Docker is unavailable, document the exact failure and run all non-PostgreSQL gates.
