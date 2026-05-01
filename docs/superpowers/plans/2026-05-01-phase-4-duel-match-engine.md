# Phase 4 Duel and Match Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Phase 4 direct 1v1 duel and match-engine slice: duel requests, countdowns, active matches, win/loss/forfeit/timeout handling, spectator state, teardown, and internal lifecycle events.

**Architecture:** Keep duel and match rules in plain Java under `domain.matches`, `application.matches`, and `ports.matches`. Paper/Bukkit details stay in `adapters.paper.matches` and command adapters; bootstrap only composes services, starts one synchronous ticker, and shuts match state down before player sessions drain.

**Tech Stack:** Java 21 records/sealed interfaces/enums, Gradle 9.5.0, Paper API 1.21.11, JUnit Jupiter, MockBukkit, standard `plugin.yml` commands, one Paper scheduler ticker.

---

## Research Notes

- Paper lifecycle work belongs in `onEnable()` / `onDisable()`, not constructors or `onLoad()`. Keep `RevPracBootstrap` as the composition root.
- Paper scheduler work is tick-based. Phase 4 uses one synchronous repeating ticker adapter that calls plain Java services; domain/application code must not call Bukkit scheduler APIs, `Thread.sleep`, or wall-clock static methods.
- Teleport, inventory mutation, game mode changes, damage/death events, and command parsing remain Paper adapter responsibilities.
- `GameMode.SPECTATOR` is an adapter detail. Application code decides spectator membership and lifecycle; the Paper adapter applies spectator mechanics.
- `EntityDamageEvent`, `PlayerDeathEvent`, and `PlayerQuitEvent` should translate into narrow application calls. Paper listeners must not decide winners directly.

## Phase Boundary Decisions

- Phase 4 covers direct 1v1 duels only. Queue tickets, matchmaking, ranked/unranked policy, parties, rematches, ratings, stats, and persistence are deferred.
- Duel requests and active matches are in memory only.
- Domain events are internal Java records, not public Bukkit events yet.
- Arena reset remains the existing Phase 3 reset hook. Real block rollback remains deferred.
- Teardown is the single terminal path for death, forfeit, timeout, quit, shutdown, and admin/manual cleanup.
- If teardown fails, the completed match stays in memory so retry is possible.

## File Structure

- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/DuelRequestId.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/DuelRequestState.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/DuelRequest.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchId.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchState.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchSide.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchParticipants.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchRuleset.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchEndReason.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchOutcome.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/Match.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/matches/MatchEvent.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/config/MatchConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/RevPracConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/DuelRequestRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchPlayerPort.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/DuelRequestService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryDuelRequestRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchPlayerAdapter.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchLifecycleListener.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchTicker.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracDuelCommand.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify `src/main/resources/plugin.yml`
- Modify `src/main/resources/config.yml`
- Add matching tests under `src/test/java/io/github/xreatlabz/revprac/...`
- Update `ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, `docs/DECISIONS.md`, and `docs/README.md`

---

### Task 1: Domain Match Contracts

**Files:**
- Create all `src/main/java/io/github/xreatlabz/revprac/domain/matches/*.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/matches/DuelRequestContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/matches/MatchAggregateContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/matches/MatchDomainEventContractTest.java`

- [ ] **Step 1: Write failing domain tests**

Write tests proving:
- `DuelRequest` rejects self-duels, non-pending terminal transitions, and invalid expiry order.
- `MatchParticipants` requires two distinct players and resolves side/opponent lookups.
- `MatchRuleset` rejects non-positive countdown and max-duration ticks.
- `Match` starts in `COUNTDOWN`, ticks to `ACTIVE`, completes exactly once, preserves spectators separately from participants, and rejects mutation after completion except spectator cleanup.
- `MatchOutcome` supports win/loss, forfeit, timeout, and shutdown reasons without requiring a winner for timeout or shutdown.
- `MatchEvent` records are immutable, ordered by explicit `sequence`, and contain no Bukkit/Paper types.
- `domain.matches` contains no `org.bukkit` or `io.papermc.paper` imports.

Run:

```bash
./gradlew test --tests '*DuelRequestContractTest' --tests '*MatchAggregateContractTest' --tests '*MatchDomainEventContractTest'
```

Expected: tests fail before the domain classes exist.

- [ ] **Step 2: Implement minimal domain model**

Use Java records and compact constructors. Reuse existing `PlayerId`, `ArenaId`, `ArenaReservationId`, and `KitId`.

Required behavior:
- `DuelRequestId` and `MatchId` wrap non-null `UUID`.
- `DuelRequestState`: `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`, `EXPIRED`.
- `MatchState`: `COUNTDOWN`, `ACTIVE`, `COMPLETED`.
- `MatchSide`: `ONE`, `TWO`.
- `MatchEndReason`: `WIN`, `FORFEIT`, `TIMEOUT`, `SHUTDOWN`.
- `MatchParticipants` stores `playerOne` and `playerTwo`, rejects duplicates, and exposes `contains(PlayerId)`, `sideOf(PlayerId)`, and `opponentOf(PlayerId)`.
- `MatchRuleset` stores `countdownTicks`, `maxDurationTicks`, and `spectatorsEnabled`.
- `Match` stores ids, participants, arena, kit, reservation, state, countdown ticks remaining, active ticks elapsed, spectators as an immutable set, and optional outcome.
- `Match.tickCountdown()` decrements one tick and switches to `ACTIVE` at zero.
- `Match.tickActive()` increments active elapsed ticks and returns a timeout-completed match when max duration is reached.
- `Match.complete(MatchOutcome)` is the only terminal transition.
- `MatchEvent` is a sealed interface with record events for request, accept, countdown start, match start, completion, spectator join/leave, and teardown.

- [ ] **Step 3: Re-run domain tests**

Run:

```bash
./gradlew test --tests '*DuelRequestContractTest' --tests '*MatchAggregateContractTest' --tests '*MatchDomainEventContractTest'
```

Expected: domain tests pass.

---

### Task 2: Application Services and Storage

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/DuelRequestRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/matches/MatchPlayerPort.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/DuelRequestService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryDuelRequestRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchRepository.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/matches/DuelRequestServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryDuelRequestRepositoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryMatchRepositoryTest.java`

- [ ] **Step 1: Write failing service and storage tests**

Write tests proving:
- request creation rejects duplicate pending requests, self-duels, offline/busy players, missing arena IDs, and missing kit IDs.
- accept marks a request accepted and starts a countdown match with exactly one arena reservation.
- decline, cancel, and expiry remove pending intake without creating matches.
- countdown ticks deterministically from configured ticks to `ACTIVE`.
- win/loss, forfeit, quit, timeout, and shutdown all use one completion/teardown path.
- teardown returns both participants to lobby, clears spectators, releases the arena once, deletes the match only after successful teardown, and is retryable after failure.
- spectators can join active matches only when enabled, cannot be participants, and are tracked separately.
- in-memory repositories expose immutable snapshots and atomic create semantics.
- `application.matches` and `ports.matches` contain no Bukkit/Paper imports and no scheduler/static time calls.

Run:

```bash
./gradlew test --tests '*DuelRequestServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*InMemoryDuelRequestRepositoryTest' --tests '*InMemoryMatchRepositoryTest'
```

Expected: tests fail before the services and repositories exist.

- [ ] **Step 2: Implement ports and repositories**

Repository requirements:
- `DuelRequestRepository#create(DuelRequest)` returns `false` for duplicate ids.
- expose `find`, `findAll`, `save`, and `delete`.
- expose request lookup by requester/target pair for command accept/deny flows.
- `MatchRepository#create(Match)` returns `false` for duplicate ids or players already in an active match.
- expose `find`, `findAll`, `findByPlayer`, `findBySpectator`, `save`, and `delete`.
- store all state in `ConcurrentHashMap` and return immutable snapshots.

- [ ] **Step 3: Implement services**

Service requirements:
- `DuelRequestService` injects `DuelRequestRepository`, `MatchRepository`, `ArenaRegistryService`, `KitRegistryService`, `Clock`, request TTL, and an event sink list/consumer.
- `request(...)` creates `DuelRequest` with `Instant createdAt` and `Instant expiresAt`.
- `accept(...)`, `decline(...)`, `cancel(...)`, and `expirePendingRequests()` are serialized with a `ReentrantLock`.
- `closeIntake()` prevents new requests during shutdown.
- `MatchLifecycleService` injects repositories, `PlayerSessionService`, `ArenaRegistryService`, `KitRegistryService`, `MatchPlayerPort`, `MatchRuleset`, and event sink list/consumer.
- `startAcceptedDuel(DuelRequest)` reserves the arena, transitions both players to `MATCH`, prepares both combatants at arena spawns with the requested kit, saves the countdown match, and emits countdown events.
- `tick()` advances all countdown/active matches deterministically.
- `completeByDeath(PlayerId)`, `forfeit(PlayerId)`, `handleQuit(PlayerId)`, and timeout all call one `completeMatch(...)` path.
- `tearDown(MatchId)` clears adapter match state, returns players/spectators to lobby, releases arena, emits teardown event, and deletes only after all teardown steps succeed.
- `shutdownAll()` closes intake, completes active matches with `SHUTDOWN`, retries teardown, and leaves failed teardown state in memory.

- [ ] **Step 4: Re-run application tests**

Run:

```bash
./gradlew test --tests '*DuelRequestServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*InMemoryDuelRequestRepositoryTest' --tests '*InMemoryMatchRepositoryTest'
```

Expected: application and storage tests pass.

---

### Task 3: Paper Match Adapters and Duel Command

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchPlayerAdapter.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchLifecycleListener.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchTicker.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracDuelCommand.java`
- Modify `src/main/resources/plugin.yml`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchLifecycleListenerTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/matches/PaperMatchPlayerAdapterTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracDuelCommandTest.java`

- [ ] **Step 1: Write failing adapter tests**

Write tests proving:
- duel command usage covers `/duel <player> <arena> <kit>`, `/duel accept <player>`, `/duel deny <player>`, `/duel cancel <player>`, `/duel forfeit`, and `/duel spectate <player>`.
- command parsing resolves online players and delegates to application services without mutating domain state directly.
- death events call `completeByDeath` exactly once for active participants.
- quit events call the match service before player-session quit restoration removes state.
- spectator damage/interact attempts are cancelled by the listener.
- the ticker calls `tick()` on the match service and can be cancelled during plugin disable.
- adapter tests stay the only Phase 4 tests with Bukkit/Paper imports.

Run:

```bash
./gradlew test --tests '*PaperMatchLifecycleListenerTest' --tests '*PaperMatchPlayerAdapterTest' --tests '*RevPracDuelCommandTest'
```

Expected: tests fail before adapters exist.

- [ ] **Step 2: Implement Paper player adapter**

Adapter requirements:
- Resolve players from `Server#getPlayer(UUID)` and fail with clear `IllegalStateException` when absent.
- Teleport combatants to `ArenaDefinition.spawnOne()` / `spawnTwo()` locations and apply the selected `KitDefinition` through `PaperKitLoadoutAdapter`.
- Set and clear countdown frozen state using an internal concurrent set exposed to the listener.
- Prepare spectators by transitioning their visible Paper state to spectator mode and teleporting to an arena spawn.
- Clear spectator/combatant adapter state on teardown without restoring the player-session baseline directly; `PlayerSessionService.returnToLobby(...)` owns baseline restore.

- [ ] **Step 3: Implement listener, ticker, and command**

Implementation requirements:
- `PaperMatchLifecycleListener` listens to player death, quit, movement while frozen, damage/interact attempts by spectators, and calls application services only.
- `PaperMatchTicker` starts one synchronous repeating task with `runTaskTimer(plugin, 1L, 1L)` and cancels idempotently.
- `RevPracDuelCommand` is a separate executor from `RevPracAdminCommand`.
- `plugin.yml` declares `duel` with permission `revprac.duel`, default `true`.
- Command messages are concise and operational; they should not expose stack traces.

- [ ] **Step 4: Re-run adapter tests**

Run:

```bash
./gradlew test --tests '*PaperMatchLifecycleListenerTest' --tests '*PaperMatchPlayerAdapterTest' --tests '*RevPracDuelCommandTest'
```

Expected: adapter tests pass.

---

### Task 4: Bootstrap, Config, Runtime Shutdown, and Plugin Tests

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/application/config/MatchConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/RevPracConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify `src/main/resources/config.yml`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigServiceContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase4Test.java`

- [ ] **Step 1: Write failing config and plugin tests**

Write tests proving:
- `MatchConfig` parses `matches.duel-request-expiry-seconds`, `matches.countdown-ticks`, `matches.max-duration-ticks`, and `matches.spectators-enabled`.
- missing match config values use documented defaults.
- non-positive durations return config problems naming the exact path.
- plugin enable wires duel command, match listener, ticker, and match services.
- plugin disable cancels the ticker and calls match shutdown before player-session shutdown.

Run:

```bash
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*RevPracPluginPhase4Test'
```

Expected: tests fail before config/runtime wiring exists.

- [ ] **Step 2: Implement config parsing**

Defaults:
- `matches.duel-request-expiry-seconds`: `30`
- `matches.countdown-ticks`: `100`
- `matches.max-duration-ticks`: `12000`
- `matches.spectators-enabled`: `true`

Parsing rules:
- integer fields must be whole positive numbers.
- boolean field must be boolean.
- return `ProblemCategory.CONFIGURATION` with the exact path on invalid values.

- [ ] **Step 3: Implement bootstrap/runtime wiring**

Runtime requirements:
- `BootstrapRuntime` owns `DuelRequestService`, `MatchLifecycleService`, and `PaperMatchTicker`.
- expose match services for tests and command wiring.
- `shutdown()` closes duel intake, cancels ticker, runs `MatchLifecycleService.shutdownAll()`, then calls `PlayerSessionService.shutdownAll()`.
- preserve existing runtime shutdown idempotence and verbose lifecycle logs.

Bootstrap requirements:
- create in-memory duel/match repositories.
- create `PaperMatchPlayerAdapter`, `PaperMatchLifecycleListener`, and `PaperMatchTicker`.
- register match listener and start ticker after successful config/registry load.
- bind `/duel` executor.

- [ ] **Step 4: Re-run bootstrap/config tests**

Run:

```bash
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*RevPracPluginPhase4Test'
```

Expected: config and plugin tests pass.

---

### Task 5: Documentation and Phase Exit Gates

**Files:**
- Modify `ROADMAP.md`
- Modify `docs/ARCHITECTURE.md`
- Modify `docs/BUILDING.md`
- Modify `docs/DECISIONS.md`
- Modify `docs/README.md`

- [ ] **Step 1: Update docs**

Document:
- Phase 4 implemented scope and deferred scope.
- match domain/application/adapter package boundaries.
- in-memory request/match state and no durable recovery until persistence.
- command surface and permissions.
- focused Phase 4 verification commands.
- decision log entry for direct 1v1 match engine boundary.

- [ ] **Step 2: Run focused Phase 4 checks**

Run:

```bash
./gradlew test --tests '*DuelRequestContractTest' --tests '*MatchAggregateContractTest' --tests '*MatchDomainEventContractTest'
./gradlew test --tests '*DuelRequestServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*InMemoryDuelRequestRepositoryTest' --tests '*InMemoryMatchRepositoryTest'
./gradlew test --tests '*PaperMatchLifecycleListenerTest' --tests '*PaperMatchPlayerAdapterTest' --tests '*RevPracDuelCommandTest' --tests '*RevPracPluginPhase4Test'
```

Expected: all focused Phase 4 tests pass.

- [ ] **Step 3: Run boundary checks**

Run:

```bash
! rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/ports
! rg -n "System\\.currentTimeMillis|Instant\\.now|LocalDateTime\\.now|Thread\\.sleep|runTaskLater|BukkitScheduler" src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/application
```

Expected: both commands return no matches.

- [ ] **Step 4: Run full gate**

Run:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Expected: full Gradle gate and real Paper smoke pass.
