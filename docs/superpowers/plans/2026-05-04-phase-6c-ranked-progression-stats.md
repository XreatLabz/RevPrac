# Phase 6C Ranked Progression And Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the next focused Phase 6 slice: deterministic ranked rating progression for completed ranked queue matches and a self-facing `/stats` command for persisted per-kit stats and recent match history.

**Architecture:** Keep active queues and matches in memory. Keep storage behind existing ports and use the existing SQLite/Flyway durable spine. Rating progression is applied during match settlement and remains idempotent by the existing `match_history` insert gate. The command adapter stays Paper-only and delegates to a plain Java query service.

**Tech Stack:** Java 21, Paper 1.21.11 API, JUnit Jupiter, MockBukkit, SQLite JDBC, HikariCP, Flyway, Gradle 9.5.0.

---

## Scope Decisions

- Implement in this PR: ranked queue rating progression, `/stats summary <kit>`, `/stats history [page]`, docs updates, focused tests, full Gradle/Paper verification, and PR creation.
- Keep deferred: PostgreSQL, seasons, import/export, rematch, post-match summaries, offline target lookup, public cross-player stat lookup, active match recovery, active queue recovery, and season partitioning.
- Rating rule: only `MatchOrigin.QUEUE_RANKED` with `WIN` or `FORFEIT` changes ratings. `DIRECT_DUEL`, `QUEUE_UNRANKED`, `TIMEOUT`, and `SHUTDOWN` do not change ratings.
- Rating formula: simple Elo with `K = 32`, score `1` for winner and `0` for loser, `Math.round(32 * (score - expected))`, minimum non-zero winner gain/loss of `1`, and rating floor `1`.
- Query command: `/stats` is self-only and has permission `revprac.stats`, default `true`.

## File Ownership Map

- Rating progression worker owns:
  - `src/main/java/io/github/xreatlabz/revprac/application/ratings/RatingService.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/ratings/RatingProgression.java`
  - `src/main/java/io/github/xreatlabz/revprac/domain/stats/MatchSettlement.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementService.java`
  - `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`
  - `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchSettlementRepository.java`
  - `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`
  - `src/test/java/io/github/xreatlabz/revprac/application/ratings/RatingServiceTest.java`
  - `src/test/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementServiceTest.java`
  - `src/test/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcStorageFactoryTest.java`

- Query service worker owns:
  - `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerRecordQueryService.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerKitSummaryView.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerMatchHistoryPage.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerMatchHistoryLineItem.java`
  - `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerRatingView.java`
  - `src/test/java/io/github/xreatlabz/revprac/application/players/PlayerRecordQueryServiceTest.java`

- Command/bootstrap worker owns:
  - `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracStatsCommand.java`
  - `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
  - `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
  - `src/main/resources/plugin.yml`
  - `src/test/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracStatsCommandTest.java`
  - `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase6Test.java`

- Docs/verification worker owns:
  - `README.md`
  - `ROADMAP.md`
  - `docs/README.md`
  - `docs/PRODUCT.md`
  - `docs/ARCHITECTURE.md`
  - `docs/BUILDING.md`
  - `docs/DECISIONS.md`

### Task 1: Rating Progression And Idempotent Settlement

**Files:**
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/ratings/RatingService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/ratings/RatingProgression.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/domain/stats/MatchSettlement.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchSettlementService.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchSettlementRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`
- Modify tests listed in the rating progression ownership map.

- [ ] **Step 1: Write failing rating progression tests**

Add tests to `RatingServiceTest` proving:
- equal `1000` vs `1000` ranked win becomes winner `1016` with `wins=1`, loser `984` with `losses=1`
- large underdog win produces a larger gain
- first ranked settlement without rows uses the configured base rating
- non-decisive outcomes return no updates

Run:

```bash
./gradlew test --tests '*RatingServiceTest'
```

Expected before implementation: compile or assertion failure because progression API does not exist.

- [ ] **Step 2: Implement pure rating progression**

Create `RatingProgression` as a package-private or public record/helper in `application.ratings` with winner/loser `PlayerRating` updates. Extend `RatingService` with a method that returns zero or two updates for ranked decisive matches. Keep queue seed behavior unchanged.

- [ ] **Step 3: Write failing settlement tests**

Extend `MatchSettlementServiceTest` to prove:
- ranked queue `WIN` and `FORFEIT` include two rating updates
- direct duel and unranked queue settlements include no rating updates
- timeout/shutdown include no rating updates

Run:

```bash
./gradlew test --tests '*MatchSettlementServiceTest'
```

Expected before settlement implementation: failures because `MatchSettlement` carries no rating updates.

- [ ] **Step 4: Carry rating updates through settlement**

Extend `MatchSettlement` to include `List<PlayerRating> ratingUpdates`. Update `MatchSettlementService` to accept a `RatingService` plus default ranked base rating, build rating updates only for ranked decisive outcomes, and preserve `noOp()` behavior for tests/legacy constructors.

- [ ] **Step 5: Make repository writes idempotent**

Update `MatchSettlementRepository.record()` implementations so rating updates are applied only when the history insert is new. In JDBC, write ratings inside the same transaction after stat deltas and before commit. In memory, update a rating map only when the match was not already recorded.

- [ ] **Step 6: Add focused JDBC idempotency coverage**

Extend `JdbcStorageFactoryTest` so duplicate settlement retries do not double-apply stats or rating progression.

Run:

```bash
./gradlew test --tests '*RatingServiceTest' --tests '*MatchSettlementServiceTest' --tests '*JdbcStorageFactoryTest'
```

Expected after implementation: pass.

### Task 2: Player Record Query Service

**Files:**
- Modify: `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchSettlementRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchSettlementRepository.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/jdbc/JdbcMatchSettlementRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerRecordQueryService.java`
- Create view records listed in the query service ownership map.
- Create: `src/test/java/io/github/xreatlabz/revprac/application/players/PlayerRecordQueryServiceTest.java`

- [ ] **Step 1: Write failing query service tests**

Cover:
- `summary(player, kit)` returns zero stats when no stats row exists
- summary includes persisted rating for ranked kits and no rating for unranked-only kits
- unknown or disabled kit throws `IllegalArgumentException("unknown kit: <id>")`
- `recentHistory(player, page, pageSize)` orders newest first, includes both player-one and player-two matches, and reports `hasNextPage`
- repository failures become `IllegalStateException("player records are temporarily unavailable")`

Run:

```bash
./gradlew test --tests '*PlayerRecordQueryServiceTest'
```

Expected before implementation: compile failure because service/view records do not exist.

- [ ] **Step 2: Add recent-history port method**

Add:

```java
List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset);
```

to `MatchSettlementRepository`, in-memory storage, and JDBC storage. JDBC should query matches where the player is either participant, order by `completed_at desc`, then limit/offset.

- [ ] **Step 3: Implement the query service**

Implement `PlayerRecordQueryService` with dependencies on `KitRegistryService`, `MatchSettlementRepository`, `PlayerRatingRepository`, `PlayerProfileRepository`, and `QueueConfig`. Keep it Paper-free.

- [ ] **Step 4: Add focused JDBC query coverage**

Extend `JdbcStorageFactoryTest` for `findRecentHistory` ordering, participant matching, and pagination.

Run:

```bash
./gradlew test --tests '*PlayerRecordQueryServiceTest' --tests '*JdbcStorageFactoryTest'
```

Expected after implementation: pass.

### Task 3: `/stats` Paper Command And Bootstrap Wiring

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracStatsCommand.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify: `src/main/resources/plugin.yml`
- Create: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracStatsCommandTest.java`
- Modify: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase6Test.java`

- [ ] **Step 1: Write failing command tests**

Cover:
- console sender gets `Only players can use /stats.`
- permission failure gets `You do not have permission to use this command.`
- empty args and bad arity return `Usage: /stats summary <kit>|history [page]`
- `/stats summary nodebuff` prints one summary line with matches, wins, losses, and rating when present
- `/stats history` prints an empty-state message when no history exists
- non-positive history page returns `page must be a positive integer`

Run:

```bash
./gradlew test --tests '*RevPracStatsCommandTest'
```

Expected before implementation: compile failure because command does not exist.

- [ ] **Step 2: Implement command adapter**

Implement `RevPracStatsCommand` with parsing only. Delegate to `PlayerRecordQueryService`. Keep `PAGE_SIZE = 5`. Catch `IllegalArgumentException` and `IllegalStateException` like the existing command adapters.

- [ ] **Step 3: Wire command through plugin metadata and bootstrap**

Add `stats` command and `revprac.stats` permission to `plugin.yml`. In `RevPracBootstrap`, create `PlayerRecordQueryService` after storage and kit services exist, register the `/stats` executor, and expose it from `BootstrapRuntime` only if tests need it.

- [ ] **Step 4: Extend plugin tests**

Update `RevPracPluginPhase6Test` to assert `plugin.yml` declares `/stats`, `revprac.stats`, and that enabled plugin has a non-null stats command.

Run:

```bash
./gradlew test --tests '*RevPracStatsCommandTest' --tests '*RevPracPluginPhase6Test'
```

Expected after implementation: pass.

### Task 4: Docs And Roadmap Update

**Files:**
- Modify docs listed in the docs ownership map.

- [ ] **Step 1: Fix stale product docs**

Update `docs/PRODUCT.md` so durable ratings, player profiles, match history, and basic stats are no longer described as future/non-existent.

- [ ] **Step 2: Update Phase 6 roadmap**

Update `ROADMAP.md` to add Phase 6C implemented scope for ranked rating progression and `/stats`; leave PostgreSQL, seasons, import/export, rematch, post-match summaries, offline/cross-player lookup, and active-state recovery deferred.

- [ ] **Step 3: Update architecture/building/docs index**

Update `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, and `docs/README.md` to describe the new query surface and verification commands.

- [ ] **Step 4: Record decision**

Add a `2026-05-04` decision in `docs/DECISIONS.md` for ranked-only Elo progression and self-only stats/history command.

- [ ] **Step 5: Run doc sanity scans**

Run:

```bash
rg -n "durable ratings, stats tracking|No durable persistence for mutable match or player state|player-facing stat commands remain future" docs README.md ROADMAP.md
```

Expected: no stale contradictions.

### Task 5: Final Review And Verification

**Files:**
- No intended production ownership unless review findings require targeted fixes.

- [ ] **Step 1: Run focused Phase 6 tests**

```bash
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*MatchHistoryEntryTest' --tests '*PlayerKitStatsTest' --tests '*RatingServiceTest' --tests '*MatchSettlementServiceTest'
./gradlew test --tests '*MatchLifecycleServiceTest' --tests '*PlayerAvailabilityServiceTest'
./gradlew test --tests '*JdbcStorageFactoryTest' --tests '*RevPracPluginPhase6Test' --tests '*PlayerRecordQueryServiceTest' --tests '*RevPracStatsCommandTest'
```

- [ ] **Step 2: Run boundary scans**

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
rg -n "import (java\\.sql|javax\\.sql|org\\.flywaydb|org\\.sqlite|org\\.postgresql|com\\.zaxxer\\.hikari)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

Expected: no matches.

- [ ] **Step 3: Run full gate**

```bash
git diff --check
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

- [ ] **Step 4: Review and PR**

Run a final code review agent, fix valid findings, verify again, commit atomic changes, push `feature/phase-6-completion`, and open a PR against `main`.
