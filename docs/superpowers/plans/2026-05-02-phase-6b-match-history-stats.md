# Phase 6B Match History Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist completed match history and aggregate per-player per-kit match stats as the next Phase 6 slice.

**Architecture:** Keep match settlement in plain Java application/domain code and keep storage behind ports. `MatchLifecycleService` remains the only match-completion owner; it captures the completion instant on the retained `Match` and calls a settlement service before teardown. JDBC persistence records each match once and increments stats only when the match-history insert is new, making retry paths idempotent. `MatchOrigin` records direct duel, ranked queue, and unranked queue sources without making active match state durable.

**Tech Stack:** Java 21 records/services, existing Paper API boundary, HikariCP/Flyway/sqlite-jdbc, JUnit Jupiter, MockBukkit.

---

## File Map

- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchHistoryEntry.java`: durable completed-match record.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchOrigin.java`: durable source marker for direct and queued matches.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/PlayerKitStats.java`: aggregate stat snapshot.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/PlayerKitStatDelta.java`: per-settlement stat increment.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/MatchSettlement.java`: history row plus stat deltas.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`: persistence port for idempotent settlement and lookup.
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementService.java`: converts completed `Match` into a `MatchSettlement`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchSettlementRepository.java`: application tests and non-JDBC fallback fixture.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`: transactional SQLite implementation.
- Create `src/main/resources/db/migration/V2__create_match_history_stats.sql`: match history and stats tables.
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`: call settlement before teardown.
- Modify `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageRuntime.java`: expose settlement repository.
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`: wire JDBC settlement into `MatchLifecycleService`.
- Modify docs: `ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/README.md`.
- Tests: add domain contract tests, application settlement tests, lifecycle integration tests, JDBC migration/repository tests, and plugin wiring tests.

## Task 1: Domain Settlement Contracts

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchHistoryEntry.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/PlayerKitStats.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/PlayerKitStatDelta.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/stats/MatchSettlement.java`
- Test `src/test/java/io/github/xreatlabz/revprac/domain/matches/MatchHistoryEntryTest.java`
- Test `src/test/java/io/github/xreatlabz/revprac/domain/stats/PlayerKitStatsTest.java`

- [x] Write failing tests for required fields, non-negative counters, winner/loser consistency, and immutable lists.
- [x] Run:

```bash
./gradlew test --tests '*MatchHistoryEntryTest' --tests '*PlayerKitStatsTest'
```

Expected: compile failure or test failure because records do not exist.

- [x] Implement the records with null checks and non-negative validation.
- [x] Re-run the same tests and confirm they pass.

## Task 2: Settlement Service

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchSettlementRepository.java`
- Test `src/test/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementServiceTest.java`

- [x] Write failing tests for `WIN`, `FORFEIT`, `TIMEOUT`, and `SHUTDOWN` completed matches.
- [x] Verify that `WIN` and `FORFEIT` produce one win delta for the winner and one loss delta for the loser; forfeits also increment the loser's forfeit count.
- [x] Verify that `TIMEOUT` increments timeout counts for both players without wins/losses.
- [x] Verify that `SHUTDOWN` increments shutdown counts for both players without wins/losses.
- [x] Run:

```bash
./gradlew test --tests '*MatchSettlementServiceTest'
```

Expected: fail because service and in-memory repository do not exist.

- [x] Implement minimal service and in-memory repository.
- [x] Re-run the same tests and confirm they pass.

## Task 3: Match Lifecycle Integration

**Files:**
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Modify `src/test/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleServiceTest.java`

- [x] Write failing tests proving completion settles before teardown and retains durable history/stat output.
- [x] Write failing test proving settlement failure leaves the completed match retained for retry and does not teardown players.
- [x] Run:

```bash
./gradlew test --tests '*MatchLifecycleServiceTest'
```

Expected: fail because lifecycle does not call settlement.

- [x] Add a `MatchSettlementService` collaborator to `MatchLifecycleService` with a no-op default constructor overload for existing tests where needed.
- [x] Call settlement after saving the completed match and before teardown.
- [x] Re-run the same tests and confirm they pass.

## Task 4: JDBC Migration And Repository

**Files:**
- Create `src/main/resources/db/migration/V2__create_match_history_stats.sql`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageRuntime.java`
- Modify `src/test/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageFactoryTest.java`

- [x] Write failing JDBC tests for V2 migration, match-history persistence, player stats persistence, and idempotent duplicate settlement.
- [x] Run:

```bash
./gradlew test --tests '*JdbcStorageFactoryTest'
```

Expected: fail because V2 tables and repository do not exist.

- [ ] Implement V2 schema:

```sql
create table match_history (
    match_id text primary key,
    player_one_id text not null,
    player_two_id text not null,
    arena_id text not null,
    kit_id text not null,
    match_origin text not null,
    end_reason text not null,
    winner_id text,
    loser_id text,
    active_ticks integer not null check (active_ticks >= 0),
    completed_at integer not null
);

create table player_kit_stats (
    player_id text not null,
    kit_id text not null,
    matches_played integer not null check (matches_played >= 0),
    wins integer not null check (wins >= 0),
    losses integer not null check (losses >= 0),
    forfeits integer not null check (forfeits >= 0),
    timeouts integer not null check (timeouts >= 0),
    shutdowns integer not null check (shutdowns >= 0),
    updated_at integer not null,
    primary key (player_id, kit_id)
);
```

- [x] Implement JDBC settlement as one transaction: insert `match_history`; if inserted, apply stat deltas; if already present, commit without changing stats.
- [x] Re-run the same tests and confirm they pass.

## Task 5: Bootstrap Wiring And Plugin Proof

**Files:**
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase6Test.java`

- [x] Write failing plugin/storage tests proving the runtime exposes a JDBC match settlement repository and V2 tables exist after enable.
- [x] Run:

```bash
./gradlew test --tests '*RevPracPluginPhase6Test'
```

Expected: fail because bootstrap does not wire the new repository.

- [x] Wire `new MatchSettlementService(storageRuntime.matchSettlementRepository())` and a lifecycle `Clock` into `MatchLifecycleService`.
- [x] Re-run the same test and confirm it passes.

## Task 6: Documentation And Verification

**Files:**
- Modify `ROADMAP.md`
- Modify `docs/ARCHITECTURE.md`
- Modify `docs/DECISIONS.md`
- Modify `docs/README.md`

- [x] Update Phase 6 status to say Phase 6A and Phase 6B are implemented.
- [x] Keep PostgreSQL, seasons, and import/export explicitly deferred.
- [x] Record the Phase 6B decision for idempotent settlement and basic aggregate stats.
- [x] Run:

```bash
git diff --check
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Expected: all pass.

## Execution Notes

- Keep active queue tickets, match repository state, and player sessions in memory for this slice.
- Do not add player-facing stat commands yet; Phase 7 can expose operator/player surfaces.
- Do not introduce PostgreSQL/Testcontainers in this branch; V2 is SQLite/Flyway only.
- Preserve unrelated untracked `error.log`.
