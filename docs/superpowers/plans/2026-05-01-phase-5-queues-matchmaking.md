# Phase 5 Queues and Matchmaking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Phase 5 queue and matchmaking slice: ranked and unranked queue tickets, deterministic pairing, queue-to-match handoff, conflict prevention, queue command wiring, shutdown drain, and focused concurrency coverage.

**Architecture:** Add `domain.queues`, `application.queues`, and `ports.queues` beside the existing match stack. Queue tickets are the source of truth for queue membership; `PlayerContext.QUEUE` mirrors lifecycle state. Queued matches enter the existing match engine through a neutral match-start path, not synthetic duel requests.

**Tech Stack:** Java 21 records/enums, Gradle 9.5.0, Paper API 1.21.11, JUnit Jupiter, MockBukkit, standard `plugin.yml` command registration, one synchronous Paper queue ticker.

---

## Research Notes

- Paper-facing work stays in `adapters.paper` and standard `plugin.yml`; do not add `paper-plugin.yml`, NMS, CraftBukkit internals, or asynchronous Bukkit calls.
- Queue matching is tick-driven and deterministic. Use a synchronous Paper ticker because queue-to-match handoff touches players, sessions, arenas, and match state.
- `PlayerContext.QUEUE` and `TransitionReason.QUEUE_JOIN` already exist. Queue services should call `PlayerSessionService.transitionTo(...)` and `returnToLobby(...)` instead of mutating session repositories.
- `DuelRequestService` currently checks active matches but not queued players. Phase 5 must add a queue-availability boundary so queued players cannot send or accept direct duels.
- Arena reservation remains match-owned and happens only when a queue pair is promoted into a match. Queue join never reserves an arena.
- Ranked search rating is runtime-only in Phase 5. Durable ratings, seasons, stats, match history, and migrations are Phase 6.

## Phase Boundary Decisions

- Queue identity is `QueueMode + KitId`; enabled arenas are selected at match promotion time.
- Ranked and unranked are explicit queue modes. `KitRules.ranked` gates ranked eligibility, but ranked-capable kits can still expose unranked queues.
- Runtime ranked search rating is per `(PlayerId, KitId)`, seeded at `1000`, updated in memory only after ranked queue match completion work is introduced, and reset on restart.
- Direct `/duel` matches remain outside queue rating and queue ticket state.
- Queued players are busy. Direct duel request intake rejects players with active queue tickets.
- Shutdown closes queue intake, cancels the queue ticker, drains queued players back to lobby, then shuts down duel intake, match ticker, matches, and player sessions.

## File Structure

- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueueMode.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueueTicketId.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueueTicketState.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueueKey.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueueTicket.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/MatchmakingWindowPolicy.java`
- Create `src/main/java/io/github/xreatlabz/revprac/domain/queues/QueuedMatchAssignment.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/config/QueueConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/RevPracConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/queues/QueueTicketRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/queues/QueueRatingRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueTicketRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueRatingRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/PlayerAvailabilityService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueMatchmakingService.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/DuelRequestService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueTicker.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueLifecycleListener.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracQueueCommand.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify `src/main/resources/plugin.yml`
- Modify `src/main/resources/config.yml`
- Add matching tests under `src/test/java/io/github/xreatlabz/revprac/...`
- Update `ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, `docs/DECISIONS.md`, and `docs/README.md`

---

### Task 1: Queue Domain and Config Contracts

**Files:**
- Create all `src/main/java/io/github/xreatlabz/revprac/domain/queues/*.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/config/QueueConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/RevPracConfig.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java`
- Modify `src/main/resources/config.yml`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/queues/QueueTicketContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/queues/MatchmakingWindowPolicyContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigServiceContractTest.java`

- [ ] **Step 1: Write failing domain and config tests**

Write tests proving:
- `QueueTicketId` wraps a non-null UUID.
- `QueueMode` has exactly `UNRANKED` and `RANKED`.
- `QueueKey` stores non-null mode and kit id.
- `QueueTicket` stores ticket id, player id, queue key, joined-at tick, search rating, and state.
- joined-at tick must be non-negative.
- search rating is required for ranked tickets and ignored as `0` for unranked tickets.
- queue ticket transitions are one-way: `SEARCHING -> PAIRING -> MATCHED`, `SEARCHING -> CANCELLED`, and `SEARCHING -> EXPIRED`.
- terminal tickets cannot transition again.
- `MatchmakingWindowPolicy` returns windows `50`, `100`, `150`, `250`, and `400` for waited seconds `0`, `10`, `20`, `30`, and `45`.
- ranked compatibility uses absolute rating delta within the current window.
- unranked compatibility ignores rating and uses FIFO.
- `domain.queues` contains no Bukkit/Paper imports.
- `QueueConfig` validates positive `matchmaking-period-ticks`, non-negative `ranked-base-rating`, positive window thresholds, and sorted increasing ranked windows.
- `LoadValidatedConfigService` reads a `queues` section with defaults.

Run:

```bash
./gradlew test --tests '*QueueTicketContractTest' --tests '*MatchmakingWindowPolicyContractTest' --tests '*LoadValidatedConfigServiceContractTest'
```

Expected: new queue tests fail before the domain/config classes exist.

- [ ] **Step 2: Implement queue domain records**

Use these contracts:
- `QueueMode`: enum `UNRANKED`, `RANKED`.
- `QueueTicketId(UUID value)`: reject null.
- `QueueTicketState`: `SEARCHING`, `PAIRING`, `MATCHED`, `CANCELLED`, `EXPIRED`.
- `QueueKey(QueueMode mode, KitId kitId)`: reject nulls.
- `QueueTicket(QueueTicketId id, PlayerId playerId, QueueKey key, long joinedAtTick, int searchRating, QueueTicketState state)`: reject nulls and negative joined-at tick; ranked search rating must be positive; unranked search rating is normalized to `0`.
- `QueueTicket.markPairing()`, `markMatched()`, `cancel()`, and `expire()` return new immutable tickets and reject invalid transitions.
- `MatchmakingWindowPolicy` stores ordered window steps `(waitSeconds, ratingWindow)` and exposes `windowForWaitSeconds(long)` and `isCompatible(QueueTicket anchor, QueueTicket candidate, long currentTick, long ticksPerSecond)`.
- `QueuedMatchAssignment(QueueTicket first, QueueTicket second, QueueMode mode, KitId kitId, int ratingDelta)`: reject same player, mismatched queue keys, and non-pairing tickets.

- [ ] **Step 3: Implement queue config**

Add `QueueConfig` with defaults:
- `DEFAULT_MATCHMAKING_PERIOD_TICKS = 20`
- `DEFAULT_RANKED_BASE_RATING = 1000`
- `DEFAULT_TICKS_PER_SECOND = 20`
- ranked windows: `0:50`, `10:100`, `20:150`, `30:250`, `45:400`

Add `QueueConfig queues` to `RevPracConfig`, preserve the existing three-argument constructor by delegating to `QueueConfig.defaults()`, and read these config paths:
- `queues.matchmaking-period-ticks`
- `queues.ranked-base-rating`
- `queues.ranked-windows`

Use fail-closed config parsing. Bad `queues` parent type, bad list item, non-integer value, negative threshold, non-positive window, duplicate threshold, or unsorted threshold returns `Err<RevPracConfig>`.

Add defaults to `src/main/resources/config.yml`:

```yaml
queues:
  matchmaking-period-ticks: 20
  ranked-base-rating: 1000
  ranked-windows:
    - wait-seconds: 0
      rating-window: 50
    - wait-seconds: 10
      rating-window: 100
    - wait-seconds: 20
      rating-window: 150
    - wait-seconds: 30
      rating-window: 250
    - wait-seconds: 45
      rating-window: 400
```

- [ ] **Step 4: Re-run domain/config tests**

Run:

```bash
./gradlew test --tests '*QueueTicketContractTest' --tests '*MatchmakingWindowPolicyContractTest' --tests '*LoadValidatedConfigServiceContractTest'
```

Expected: queue domain/config tests pass.

---

### Task 2: Queue Repository, Rating Store, and Availability Boundary

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/ports/queues/QueueTicketRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/ports/queues/QueueRatingRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueTicketRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueRatingRepository.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/PlayerAvailabilityService.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/DuelRequestService.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueTicketRepositoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueRatingRepositoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/queues/PlayerAvailabilityServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/matches/DuelRequestServiceTest.java`

- [ ] **Step 1: Write failing repository and availability tests**

Write tests proving:
- `QueueTicketRepository#create` rejects duplicate ticket IDs and duplicate active player tickets.
- `find`, `findAll`, `findByPlayer`, `findSearchingByKey`, `save`, and `delete` return immutable snapshots.
- `claimPair(firstId, secondId)` atomically changes two `SEARCHING` tickets to `PAIRING` and returns both updated tickets.
- `claimPair` rejects same ticket, missing tickets, non-searching tickets, mismatched queue keys, and same-player pairs.
- concurrent same-player `create` calls produce exactly one active ticket.
- `restoreSearching(firstId, secondId)` restores pair tickets after a failed match promotion only when they are still `PAIRING`.
- `QueueRatingRepository` seeds missing ranked ratings to the configured base rating and stores per-player/per-kit values.
- `PlayerAvailabilityService` reports a player busy when they have an active match, spectator state, pending duel request, or active queue ticket.
- `DuelRequestService.request` rejects requester or target when `PlayerAvailabilityService` reports queued.
- `adapters.storage`, `ports.queues`, and `application.queues` contain no Bukkit/Paper imports.

Run:

```bash
./gradlew test --tests '*InMemoryQueueTicketRepositoryTest' --tests '*InMemoryQueueRatingRepositoryTest' --tests '*PlayerAvailabilityServiceTest' --tests '*DuelRequestServiceTest'
```

Expected: new queue repository/availability tests fail before the types exist.

- [ ] **Step 2: Implement queue ports and in-memory adapters**

`QueueTicketRepository` must expose:
- `Optional<QueueTicket> find(QueueTicketId ticketId)`
- `Collection<QueueTicket> findAll()`
- `Optional<QueueTicket> findByPlayer(PlayerId playerId)`
- `Collection<QueueTicket> findSearchingByKey(QueueKey queueKey)`
- `boolean create(QueueTicket ticket)`
- `void save(QueueTicket ticket)`
- `Optional<QueuedMatchAssignment> claimPair(QueueTicketId firstId, QueueTicketId secondId)`
- `void restoreSearching(QueueTicketId firstId, QueueTicketId secondId)`
- `void delete(QueueTicketId ticketId)`
- `void deleteByPlayer(PlayerId playerId)`

`InMemoryQueueTicketRepository` uses one mutex plus `ConcurrentHashMap`. Active states are `SEARCHING` and `PAIRING`; `MATCHED`, `CANCELLED`, and `EXPIRED` do not block future tickets.

`QueueRatingRepository` must expose:
- `int rating(PlayerId playerId, KitId kitId, int defaultRating)`
- `void save(PlayerId playerId, KitId kitId, int rating)`

`InMemoryQueueRatingRepository` stores ratings by a private record key `(PlayerId, KitId)` and rejects non-positive ratings.

- [ ] **Step 3: Implement shared availability and wire duel intake**

Create `PlayerAvailabilityService` with dependencies:
- `MatchRepository`
- `DuelRequestRepository`
- `QueueTicketRepository`

Expose:
- `void requireAvailableForQueue(PlayerId playerId)`
- `void requireAvailableForDuel(PlayerId playerId, String role)`
- `boolean isQueued(PlayerId playerId)`

Rules:
- active match participant -> busy
- active spectator -> busy
- pending duel request as requester or target -> busy
- active queue ticket -> busy

Modify `DuelRequestService` to accept `PlayerAvailabilityService` and replace its private `requireNotBusy(...)` logic with `availabilityService.requireAvailableForDuel(...)`. Preserve the existing public constructor by adding an overload for tests that do not yet pass queue dependencies, or update all harnesses in the same commit.

- [ ] **Step 4: Re-run repository and availability tests**

Run:

```bash
./gradlew test --tests '*InMemoryQueueTicketRepositoryTest' --tests '*InMemoryQueueRatingRepositoryTest' --tests '*PlayerAvailabilityServiceTest' --tests '*DuelRequestServiceTest'
```

Expected: repository, rating, availability, and updated duel tests pass.

---

### Task 3: Queue Services and Neutral Match Handoff

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueService.java`
- Create `src/main/java/io/github/xreatlabz/revprac/application/queues/QueueMatchmakingService.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleService.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/queues/QueueServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/queues/QueueMatchmakingServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/matches/MatchLifecycleServiceTest.java`

- [ ] **Step 1: Write failing queue service and match handoff tests**

Write tests proving:
- queue join moves an online, available lobby player to `PlayerContext.QUEUE`.
- unranked join is allowed for any enabled kit.
- ranked join is rejected for a kit whose `KitRules.ranked()` is false.
- duplicate queue join and cross-mode queue join are rejected.
- leave deletes the active ticket and returns the player to lobby.
- queued player quit deletes the ticket and lets player-session quit restore/pending behavior continue.
- `closeIntake()` rejects new joins.
- `shutdownAll()` closes intake, deletes all active tickets, and returns online queued players to lobby.
- unranked matchmaking pairs FIFO within the same kit.
- ranked matchmaking only pairs tickets inside the active MMR window and selects smallest rating delta, oldest joined tick, then ticket UUID as tie-breakers.
- one matchmaking sweep creates at most one match per player.
- pairing starts a match through `MatchLifecycleService.startQueuedMatch(...)`, deletes tickets on success, and transitions players from `QUEUE` to `MATCH`.
- failed arena reservation restores both tickets to `SEARCHING` while players remain queued.
- failed preparation after a queue match handoff removes both tickets and leaves match-service rollback behavior intact.
- `application.queues` contains no Bukkit/Paper imports and no static time/scheduler calls.

Run:

```bash
./gradlew test --tests '*QueueServiceTest' --tests '*QueueMatchmakingServiceTest' --tests '*MatchLifecycleServiceTest'
```

Expected: new queue service tests fail before services and neutral match handoff exist.

- [ ] **Step 2: Implement `QueueService`**

Dependencies:
- `QueueTicketRepository`
- `QueueRatingRepository`
- `PlayerAvailabilityService`
- `PlayerSessionService`
- `KitRegistryService`
- `MatchPlayerPort`
- `Clock`
- `QueueConfig`

Public methods:
- `QueueTicket join(PlayerId playerId, QueueMode mode, KitId kitId, long currentTick)`
- `Optional<QueueTicket> ticket(PlayerId playerId)`
- `QueueTicket leave(PlayerId playerId)`
- `void handleQuit(PlayerId playerId)`
- `void closeIntake()`
- `void shutdownAll()`

Join rules:
- intake must be open
- player must be online
- player must be available
- kit must exist and be enabled
- ranked mode requires `kit.rules().ranked() == true`
- call `playerSessionService.transitionTo(playerId, PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN)` before creating the ticket
- if ticket creation fails, return player to lobby and surface the error

- [ ] **Step 3: Add neutral queued-match handoff to `MatchLifecycleService`**

Add a public method:

```java
public Match startQueuedMatch(PlayerId firstPlayerId, PlayerId secondPlayerId, KitId kitId)
```

Requirements:
- reject same player
- select the first enabled arena sorted by arena id that can be reserved
- use a private shared match-start helper so direct duel and queued matches do not duplicate participant transition, kit preparation, repository create, event, and rollback logic
- keep arena reservation inside `MatchLifecycleService`
- for arena-unavailable failures before participant transition, throw without changing player sessions
- for failures after participant transition/preparation, rollback through the existing match-service cleanup path
- do not create a `DuelRequest`

- [ ] **Step 4: Implement `QueueMatchmakingService`**

Dependencies:
- `QueueTicketRepository`
- `QueueService`
- `MatchLifecycleService`
- `MatchmakingWindowPolicy`
- `QueueConfig`

Public methods:
- `void tick(long currentTick)`
- `void closeIntake()`

Algorithm:
- copy all `SEARCHING` tickets grouped by `QueueKey`
- sort each group by `joinedAtTick`, then `ticketId.value()`
- for each anchor not already used in this sweep:
  - choose a compatible candidate in the same key
  - unranked: earliest joined tick
  - ranked: smallest rating delta inside the current window, then earliest joined tick, then ticket UUID
  - call `claimPair(...)`
  - call `matchLifecycleService.startQueuedMatch(...)`
  - on success, delete both tickets
  - on arena-unavailable failure before session transition, call `restoreSearching(...)`
  - on later match-start failure, delete both tickets to avoid queue/session divergence

- [ ] **Step 5: Re-run service and match handoff tests**

Run:

```bash
./gradlew test --tests '*QueueServiceTest' --tests '*QueueMatchmakingServiceTest' --tests '*MatchLifecycleServiceTest'
```

Expected: queue services and match handoff tests pass.

---

### Task 4: Paper Queue Adapters, Command, and Bootstrap Runtime

**Files:**
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueTicker.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueLifecycleListener.java`
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracQueueCommand.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify `src/main/resources/plugin.yml`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueTickerTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/queues/PaperQueueLifecycleListenerTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracQueueCommandTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase5Test.java`

- [ ] **Step 1: Write failing Paper adapter and plugin tests**

Write tests proving:
- `/queue` is player-only and requires `revprac.queue`.
- `/queue join unranked <kit>` delegates to `QueueService.join`.
- `/queue join ranked <kit>` delegates to `QueueService.join`.
- `/queue leave` delegates to `QueueService.leave`.
- `/queue status` reports no active ticket or active ticket mode/kit.
- usage errors return command usage messages without mutating queue state.
- quit events call `QueueService.handleQuit(...)` before player-session cleanup.
- `PaperQueueTicker` schedules one synchronous repeating task using `QueueConfig.matchmakingPeriodTicks()`.
- ticker calls `QueueMatchmakingService.tick(...)` with a monotonically increasing tick counter.
- ticker cancellation is idempotent.
- plugin enable wires queue repository/services/ticker/listener/command and exposes them through `BootstrapRuntime`.
- plugin disable closes queue intake, cancels queue ticker, drains queues, then closes duel/match/player runtime in that order.

Run:

```bash
./gradlew test --tests '*PaperQueueTickerTest' --tests '*PaperQueueLifecycleListenerTest' --tests '*RevPracQueueCommandTest' --tests '*RevPracPluginPhase5Test'
```

Expected: tests fail before Paper queue adapters and bootstrap wiring exist.

- [ ] **Step 2: Implement queue command**

Command shape:

```text
/queue join unranked <kit>
/queue join ranked <kit>
/queue leave
/queue status
```

Rules:
- only players can use it
- permission: `revprac.queue`
- parse mode case-insensitively
- use exact `KitId` strings from command args
- catch `IllegalArgumentException` and `IllegalStateException` and send the message to the player

Add to `plugin.yml`:

```yaml
  queue:
    description: RevPrac ranked and unranked matchmaking queues.
    usage: /queue join <ranked|unranked> <kit>|leave|status
    permission: revprac.queue
    permission-message: You do not have permission to use this command.
```

Add permission:

```yaml
  revprac.queue:
    description: Allows RevPrac queue commands.
    default: true
```

- [ ] **Step 3: Implement Paper queue ticker and listener**

`PaperQueueTicker`:
- stores `JavaPlugin`, `QueueMatchmakingService`, and period ticks
- schedules a synchronous repeating task in `start()`
- increments an internal long `currentTick` by `periodTicks` each run
- calls `queueMatchmakingService.tick(currentTick)`
- `cancel()` is idempotent

`PaperQueueLifecycleListener`:
- on `PlayerQuitEvent`, call `queueService.handleQuit(new PlayerId(player.getUniqueId()))`
- do not decide match outcomes or session restore in the listener

- [ ] **Step 4: Wire bootstrap/runtime**

In `RevPracBootstrap`:
- create one `InMemoryQueueTicketRepository`
- create one `InMemoryQueueRatingRepository`
- create `PlayerAvailabilityService`
- pass it to `DuelRequestService`
- create `QueueService`
- create `QueueMatchmakingService`
- create `PaperQueueTicker`
- register `PaperQueueLifecycleListener`
- bind `/queue`
- start `PaperQueueTicker`

In `BootstrapRuntime`:
- store `QueueService`, `QueueMatchmakingService`, and `PaperQueueTicker`
- expose accessors for tests
- shutdown order:
  1. `queueService.closeIntake`
  2. `paperQueueTicker.cancel`
  3. `queueService.shutdownAll`
  4. `duelRequestService.closeIntake`
  5. `paperMatchTicker.cancel`
  6. `matchLifecycleService.shutdownAll`
  7. `playerSessionService.shutdownAll`

- [ ] **Step 5: Re-run adapter and plugin tests**

Run:

```bash
./gradlew test --tests '*PaperQueueTickerTest' --tests '*PaperQueueLifecycleListenerTest' --tests '*RevPracQueueCommandTest' --tests '*RevPracPluginPhase5Test'
```

Expected: Paper queue adapter and plugin bootstrap tests pass.

---

### Task 5: Documentation and Phase Exit Gates

**Files:**
- Modify `ROADMAP.md`
- Modify `docs/README.md`
- Modify `docs/ARCHITECTURE.md`
- Modify `docs/BUILDING.md`
- Modify `docs/DECISIONS.md`

- [ ] **Step 1: Update docs**

Update docs to record:
- Phase 5 implemented scope and deferred scope.
- `domain.queues`, `application.queues`, and `ports.queues` ownership.
- `/queue join <ranked|unranked> <kit>`, `/queue leave`, and `/queue status`.
- queue tickets as the source of truth for queue membership.
- `PlayerContext.QUEUE` as lifecycle mirror.
- neutral queued-match handoff through `MatchLifecycleService.startQueuedMatch(...)`.
- ranked search rating as runtime-only and reset on restart.
- queue shutdown order.
- focused Phase 5 verification commands.

Add `docs/DECISIONS.md` entry dated `2026-05-01` with:
- queue tickets are queue membership source of truth
- queued matches are not synthetic duel requests
- arena reservation happens at match creation, never queue join
- ranked/unranked are explicit queue modes
- Phase 5 rating is runtime-only until Phase 6 persistence

- [ ] **Step 2: Run focused Phase 5 checks**

Run:

```bash
./gradlew test --tests '*QueueTicketContractTest' --tests '*MatchmakingWindowPolicyContractTest'
./gradlew test --tests '*QueueServiceTest' --tests '*QueueMatchmakingServiceTest' --tests '*InMemoryQueueTicketRepositoryTest' --tests '*InMemoryQueueRatingRepositoryTest'
./gradlew test --tests '*PaperQueueTickerTest' --tests '*PaperQueueLifecycleListenerTest' --tests '*RevPracQueueCommandTest'
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*RevPracPluginPhase5Test'
```

Expected: all focused Phase 5 tests pass.

- [ ] **Step 3: Run boundary checks**

Run:

```bash
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/domain/queues src/main/java/io/github/xreatlabz/revprac/application/queues src/main/java/io/github/xreatlabz/revprac/ports/queues
rg -n "System.currentTimeMillis|Instant.now|LocalDateTime.now|Thread.sleep|runTaskLater|BukkitScheduler" src/main/java/io/github/xreatlabz/revprac/domain/queues src/main/java/io/github/xreatlabz/revprac/application/queues
```

Expected: both commands return no matches.

- [ ] **Step 4: Run full gate**

Run:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Expected: full build and real Paper smoke pass.

- [ ] **Step 5: Final review**

Dispatch a spec reviewer and code reviewer over the full Phase 5 diff. Fix all correctness, architecture, missing-test, and documentation issues before pushing.
