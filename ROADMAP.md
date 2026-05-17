# RevPrac Roadmap

## Purpose

This roadmap is the working plan for turning RevPrac from a minimal Paper plugin scaffold into a StrikePractice-like practice core for Modern Paper 1.21.11.

It is written for both humans and agents:

- humans need a clear build sequence, dependency policy, and validation path
- agents need stable boundaries, phase exit criteria, and a place to anchor future changes

## Scope

This roadmap covers:

- plugin architecture and domain boundaries
- player session safety and recovery
- arenas, kits, duels, queues, matchmaking, ratings, and persistence
- staff workflows, integrations, and operational hardening
- verification gates and documentation maintenance rules

This roadmap does not cover:

- unrelated repo experiments
- NMS or paperweight usage unless a documented feature requires it
- extra Gradle subprojects before they are justified by real optional integrations or public APIs

## Current State

RevPrac currently has:

- a Paper 1.21.11 API-only plugin scaffold
- Gradle 9.5.0 with Kotlin DSL
- Java 21 as the toolchain floor
- `plugin.yml` as the plugin metadata entrypoint
- JUnit Jupiter 6.0.3 and MockBukkit for plugin load/enable tests
- `scripts/smoke-run-paper.sh` for a real Paper boot smoke check

Current repo shape is intentionally staged:

- arena and kit registry groundwork exists; the direct duel, queue matchmaking, and match engine are implemented; durable player profiles, ratings, match history, per-player per-kit stats, logical seasons, player record lookup/transfer, rematch, post-match summaries, and runtime recovery sidecars are implemented; storage supports SQLite plus optional PostgreSQL, ratings/history/stats are scoped by the current logical active season, and managed player sessions, pending restorations, queue tickets, and active match shells are mirrored to recovery tables
- staff operations now expose diagnostics, safe registry reloads, integration presence checks, audit reads, metrics reads, and season lifecycle commands through `/revprac`
- public plugin-facing match lifecycle events are exposed as Bukkit events with an explicit contract version
- Phase 8 base hardening includes durable audit rows, lightweight operational metrics, season admin rollover, a minimal in-memory party service, and a minimal in-memory tournament service
- no Gradle subprojects yet
- physical PostgreSQL season partitioning remains deferred until there is evidence it is needed

## Product North Star

RevPrac should become a practice core that is:

- fast for players to enter and leave
- predictable for staff to operate
- safe under disconnects, reloads, and shutdowns
- easy to validate with unit tests, adapter tests, and a real Paper smoke run
- configurable without hiding state behind global singletons

The long-term feel should match a StrikePractice-style practice server, but the implementation should stay modern, explicit, and testable.

## Guiding Constraints

- Keep one Gradle module initially.
- Split code by ownership, not by convenience.
- Use these internal package boundaries:
  - `bootstrap`
  - `application`
  - `domain.arenas`
  - `domain.kits`
  - `domain.matches`
  - `domain.queues`
  - `domain.players`
  - `domain.ratings`
  - `ports`
  - `adapters.paper`
  - `adapters.storage`
  - `adapters.integrations`
- Stop Bukkit and Paper types at adapter boundaries.
- Model domain state with UUIDs, IDs, immutable value objects, and explicit transitions.
- Do not add a common/util dump package.
- Do not use static service locators.
- Use YAML for operator-managed registries.
- Use a database for mutable player data.
- Keep active queues and matches in memory.
- Follow a strict lifecycle:
  - `onLoad`: minimal bootstrap only
  - `onEnable`: validate config, migrate storage, register services and commands
  - `onDisable`: stop intake, restore players, flush persistence, cancel schedulers
- Keep docs as the source of truth for behavior, setup, and workflow changes.

## Roadmap Phases

### Phase 0: Foundation Baseline

Status: Current

Goal:

- keep the existing scaffold green and stable while the first real domain code is added

Exit criteria:

- current build, test, packaging, and Paper smoke checks still pass
- docs reflect the actual scaffold and the planned ownership boundaries
- no accidental drift toward unsupported internals or extra modules

Validation:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 1: Core Bootstrap and Contracts

Status: Implemented

Goal:

- introduce the service graph, core value objects, ports, result/error taxonomy, and immutable config models backed by a bundled `config.yml`
- implement one plain Java service that is testable without Paper

Exit criteria:

- core bootstrap wiring is explicit and discoverable
- domain contracts do not depend on Bukkit/Paper types
- config bootstrap saves the bundled resource before `JavaPlugin#getConfig()` is read
- config parsing and validation fail fast with clear errors
- the first service has direct unit coverage

Validation:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 2: Player Session Safety

Status: Implemented

Goal:

- add a player context model for `LOBBY`, `QUEUE`, `MATCH`, `SPECTATOR`, and `EDITOR`
- implement inventory, location, and state snapshots
- recover cleanly on join, quit, and plugin disable

Implemented scope:

- plain Java `domain.players` contracts for player IDs, contexts, immutable snapshots, pending restorations, sessions, and transition policy
- plain Java `application.players.PlayerSessionService` for join, quit, managed-context transitions, lobby return, duplicate-join handling, pending restoration, and shutdown recovery
- in-memory player-session and pending-restoration repositories
- Paper adapter/listener wiring for online player capture, restore, deferred post-join handling, already-online player initialization, quit handling, and disable-time restoration
- bootstrap/runtime wiring so `onDisable()` closes intake and drains online managed sessions before runtime shutdown completes

Phase boundary:

- pending restorations are in memory only; durable restart or crash recovery is deferred to Phase 6 persistence
- join restore is scheduled one tick after `PlayerJoinEvent` to avoid teleporting during the join event
- disable-time restore uses synchronous Paper restore operations for currently online players
- inventory snapshots include cursor state; restore closes open inventories, validates the target location before inventory mutation, and then reapplies inventory state

Exit criteria:

- players can be returned to a safe state after interruptions
- session transitions are explicit and tested
- join/quit/disable flows are safe without a live server

Validation:

```bash
./gradlew test --tests '*PlayerContextContractTest' --tests '*PlayerSnapshotContractTest' --tests '*PlayerSessionTransitionPolicyTest'
./gradlew test --tests '*PlayerSessionServiceTest' --tests '*InMemoryPlayerSessionRepositoryTest' --tests '*InMemoryPendingRestorationRepositoryTest'
./gradlew test --tests '*PaperPlayerStateAdapterTest' --tests '*PaperPlayerSessionListenerTest' --tests '*RevPracPluginSessionSafetyTest'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 3: Arena and Kit Registries

Status: Implemented

Goal:

- add typed arena and kit definitions
- validate registry content
- support occupancy and reservation rules
- implement reset hooks and kit serialization
- add admin setup commands for operator-managed content

Implemented scope:

- plain Java `domain.arenas` contracts for arena IDs, bounds, spawn points, enabled definitions, reservation IDs, and reservations
- plain Java `domain.kits` contracts for kit IDs, serialized inventory sections, potion effects, rules, and enabled definitions
- plain Java `application.arenas.ArenaRegistryService` and `application.kits.KitRegistryService` for deterministic registration, listing, enabled-kit filtering, arena reservation, release, and reset orchestration
- in-memory arena and kit registry repositories with atomic create semantics
- Paper YAML adapters for `arenas.yml` and `kits.yml`, including fail-closed validation for malformed registry content
- Paper kit loadout capture/apply adapter using Base64 `ItemStack.serializeAsBytes()` payloads and namespaced potion effect keys
- `ArenaResetPort` plus a Phase 3 Paper reset adapter that logs reset requests without block rollback
- `/revprac arena create <id> <radius>` and `/revprac kit save <id>` admin setup commands backed by `plugin.yml` command metadata and `revprac.admin`
- bootstrap/runtime wiring that loads YAML registries on enable, shares services with commands, and routes invalid registry files through the existing startup failure path

Phase boundary:

- arena reservations are in memory only; durable reservation or match state is deferred to Phase 6 persistence
- `arenas.yml` and `kits.yml` are operator-managed YAML registry files in the plugin data folder
- arena reset is a hook boundary only in Phase 3; real block rollback or region reset work is deferred until match teardown needs it
- setup commands intentionally cover the smallest useful operator flow: create a cuboid arena around the executing player and save the executing player's current kit
- arena world references use Paper namespaced world keys, matching player-state snapshots
- command persistence saves YAML first and mutates in-memory registries only after file persistence succeeds

Exit criteria:

- invalid arena and kit data is rejected before use
- registry operations are deterministic and safe under contention
- arena reservation and reset behavior is covered by tests
- setup commands do not leak domain concerns into Paper handlers

Validation:

```bash
./gradlew test --tests '*ArenaDefinitionContractTest' --tests '*KitDefinitionContractTest'
./gradlew test --tests '*ArenaRegistryServiceTest' --tests '*KitRegistryServiceTest' --tests '*InMemoryArenaRegistryRepositoryTest' --tests '*InMemoryKitRegistryRepositoryTest'
./gradlew test --tests '*PaperArenaRegistryFilesTest' --tests '*PaperKitLoadoutAdapterTest' --tests '*PaperKitRegistryFilesTest'
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*RevPracPluginPhase3Test'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 4: Duel and Match Engine

Status: Implemented

Goal:

- implement direct 1v1 duel requests, countdowns, a match aggregate, win/death/forfeit/timeout handling, spectator flow, teardown, and domain events

Implemented scope:

- `domain.matches` contracts for duel request IDs/state, match IDs, participants, sides, rulesets, outcomes, end reasons, aggregate transitions, and lifecycle events
- `application.matches.DuelRequestService` for request, accept, deny, cancel, duplicate-pending checks, expiry, and intake closure
- `application.matches.MatchLifecycleService` for countdown ticking, match start/completion, death/forfeit/quit/timeout/shutdown completion paths, spectator join/leave, teardown, and event publishing
- `ports.matches` for match/request repository and match-player adapter boundaries
- `adapters.storage` in-memory match and duel-request repositories that keep active request and match state in memory
- `adapters.paper.matches` listener, ticker, and player-prep adapters that enforce countdown freeze, spectator protections, death/quit handling, and per-tick progression
- `/duel <player> <arena> <kit>` plus explicit `/duel request <player> <arena> <kit>` request forms, with accept, deny/decline, cancel, spectate, and forfeit command surface
- `application.config.MatchConfig` defaults and bootstrap wiring for duel-request expiry, countdown length, max duration, and spectator toggles

Phase boundary:

- Phase 4 does not include queueing or matchmaking; those are covered in Phase 5. Ranked progression, ratings, stats, parties, rematch, post-match summaries, and durable persistence remain Phase 6+.
- match/request state stays in memory for now; durable storage comes later
- arena reset remains a port boundary; block rollback or arena restoration is deferred to the phase that owns teardown recovery
- domain event emission is in scope; richer event logging or metrics are not

Exit criteria:

- a match can be created, run, completed, and torn down without orphaned state
- countdown and timeout behavior is deterministic
- spectators are isolated from active combat state
- domain events exist for match lifecycle transitions

Validation:

```bash
./gradlew test --tests '*DuelRequestServiceTest' --tests '*MatchLifecycleServiceTest' --tests '*InMemoryDuelRequestRepositoryTest' --tests '*InMemoryMatchRepositoryTest'
./gradlew test --tests '*PaperMatchLifecycleListenerTest' --tests '*PaperMatchPlayerAdapterTest' --tests '*PaperMatchTickerTest' --tests '*RevPracDuelCommandTest'
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*RevPracPluginPhase4Test'
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application/matches src/main/java/io/github/xreatlabz/revprac/domain/matches src/main/java/io/github/xreatlabz/revprac/ports/matches src/main/java/io/github/xreatlabz/revprac/adapters/storage
rg -n "domain\\.matches|application\\.matches|ports\\.matches|adapters\\.paper\\.matches|RevPracDuelCommand|MatchConfig|BootstrapRuntime|RevPracBootstrap|plugin.yml|config.yml" src/main/java src/main/resources
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 5: Queues and Matchmaking

Status: Implemented

Goal:

- add unranked and ranked queues
- issue and manage queue tickets
- prevent double-queue states
- apply deterministic matchmaking windows and selection policy
- keep queue intake, matchmaking, and shutdown drainage safe while active queue state remains in memory

Implemented scope:

- `domain.queues` owns queue modes, keys, tickets, states, and matchmaking-window compatibility
- `application.queues.QueueService` owns join/leave/status, queue availability gating, queue session transitions, ranked base-rating seeding, and shutdown drainage
- `application.queues.QueueMatchmakingService` owns deterministic sweeps, unranked FIFO matching, ranked rating-window matching, claim/rollback handling, and queued match handoff
- `ports.queues` plus in-memory repositories keep active queue tickets in memory; Phase 5 originally seeded per-player+kit search ratings in memory before Phase 6A moved player rating seeds to durable storage
- `adapters.paper.queues` owns the synchronous ticker and quit listener, and `adapters.paper.commands.RevPracQueueCommand` owns `/queue` parsing through standard `plugin.yml`
- `application.config.QueueConfig` owns `queues.matchmaking-period-ticks`, `queues.ranked-base-rating`, `queues.ticks-per-second`, and `queues.ranked-windows`
- queued matches are started via `MatchLifecycleService.startQueuedMatch`, which reserves an arena after a pair is claimed instead of reserving one on queue join
- direct duel and queued match flows share availability, so active duel or match players cannot queue and queued players cannot send or accept direct duels
- queue shutdown drains active tickets and restores online queued players through session safety

Phase boundary:

- queue tickets are runtime-only state; durable ratings, progression, stats, seasons, parties, rematch, public events, metrics, and broader persistence remain Phase 6+
- ranked and unranked queues are explicit modes; ranked eligibility is gated by `KitRules.ranked`, but ranked-capable kits can still join unranked queues
- matchmaking is deterministic: unranked uses FIFO within mode+kit, ranked uses wait-time rating windows with deterministic tie-breakers
- the Paper queue ticker is synchronous, so `/queue join` records the actual server tick and matchmaking sweeps pass the current server tick into the policy

Exit criteria:

- a player cannot hold conflicting queue and combat states
- ranked and unranked matchmaking paths are separate and predictable
- queue intake, quit handling, and shutdown drainage leave no stranded active tickets
- queue behavior is covered by focused domain, application, adapter, and plugin tests

Validation:

```bash
./gradlew test --tests '*QueueTicketContractTest' --tests '*MatchmakingWindowPolicyContractTest'
./gradlew test --tests '*QueueServiceTest' --tests '*QueueMatchmakingServiceTest' --tests '*InMemoryQueueTicketRepositoryTest' --tests '*InMemoryQueueRatingRepositoryTest'
./gradlew test --tests '*PaperQueueLifecycleListenerTest' --tests '*PaperQueueTickerTest' --tests '*RevPracQueueCommandTest' --tests '*RevPracPluginPhase5Test'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 6: Persistence, Ratings, and Migrations

Status: Implemented

Goal:

- introduce the first durable persistence slices for RevPrac: backend-aware storage config, SQLite plus optional PostgreSQL migrations, durable player profiles, durable queue rating seeds, completed match history, basic per-player per-kit stats, logical active-season scoping, ranked rating progression, and player-record query plus transfer surfaces

Implemented scope:

- `application.config.StorageConfig` owns `storage.backend`, backend-specific path/connection settings, and `storage.pool-maximum-size`
- `plugin.yml` declares the runtime libraries for `com.zaxxer:HikariCP:7.0.2`, `org.flywaydb:flyway-core:12.5.0`, `org.flywaydb:flyway-database-postgresql:12.5.0`, `org.postgresql:postgresql:42.7.11`, and `org.xerial:sqlite-jdbc:3.53.0.0`
- `JdbcStorageFactory` resolves the SQLite path when needed, opens HikariCP for the selected backend, runs backend-specific Flyway migrations, and only then exposes repositories
- `JdbcStorageRuntime` exposes JDBC-backed player profile, player rating, match settlement, and season repositories
- `BootstrapRuntime` closes storage after queue, match, and player teardown
- the durable data slices cover player profiles, queue rating seeds, completed match history, and aggregate player-kit stats
- logical active seasons are seeded with `default`, and repositories resolve the current active season for each rating/history/stats operation so rollovers take effect without reopening storage
- ranked settlement progression is deterministic and limited to completed ranked queue matches with `WIN` or `FORFEIT`; direct duel, unranked queue, timeout, and shutdown completions do not change ratings
- ranked progression uses simple Elo with `K = 32`, applies a floor of `1`, and only writes rating updates when the `match_history` insert creates a new row
- `/stats` is self-only, gated by `revprac.stats` with a default of `true`, and exposes summary and recent-history views backed by persisted per-kit stats plus ranked-kit ratings
- `/records` is operator-facing, gated by `revprac.records`, exact player resolution, `revprac.records.lookup`, and `revprac.records.transfer`, and exposes cross-player summary/history plus schema-versioned YAML import/export
- `MatchLifecycleService` captures a completion instant, settles completed matches before teardown, and preserves that completion time across retry; settlement failure retains the completed match and prevents teardown from returning players early
- match history records direct duel, ranked queue, and unranked queue origins through `MatchOrigin`
- active queues and active matches remain in memory as live state, with JDBC recovery sidecars used to rehydrate safe runtime state after restart
- managed player baselines, pending restorations, queue tickets, and match shells are mirrored to runtime recovery tables; pairing tickets recover as searching, offline tickets recover lazily on join, and active matches restart from a fresh countdown only when both combatants are online

Phase boundary:

- physical PostgreSQL season partitioning is deferred to a future scale slice
- migrations must fail closed during bootstrap; the runtime should not start with a broken storage layer
- durable persistence here does not include physical season partitioning

Exit criteria:

- durable player profiles and ratings survive restart and reload cycles
- completed match history and aggregate player-kit stats survive restart and duplicate settlement retries without double-counting
- ranked queue settlements update ratings exactly once and only for decisive ranked outcomes
- `/stats` exposes self-only summary/history reads from persisted data without requiring a live server state lookup
- `/records` resolves players exactly, exposes cross-player summary/history, and imports or exports current-season records without double-counting on repeated import
- migrations apply cleanly from empty and upgraded states
- storage adapters remain isolated behind ports
- runtime shutdown closes storage after gameplay teardown has completed
- runtime recovery sidecars hydrate safe queue, match, player-session, and pending-restoration state during bootstrap

Validation:

```bash
./gradlew test --tests '*LoadValidatedConfigServiceContractTest' --tests '*MatchHistoryEntryTest' --tests '*PlayerKitStatsTest' --tests '*MatchSettlementServiceTest'
./gradlew test --tests '*RematchServiceTest' --tests '*PostMatchSummaryServiceTest' --tests '*RevPracDuelCommandTest'
./gradlew test --tests '*MatchLifecycleServiceTest' --tests '*PlayerAvailabilityServiceTest' --tests '*RevPracPluginPhase6Test'
./gradlew test --tests '*RatingServiceTest' --tests '*PlayerRecordQueryServiceTest' --tests '*RevPracStatsCommandTest'
./gradlew test --tests '*JdbcStorageFactoryTest' --tests '*PostgresJdbcStorageFactoryTest'
./gradlew test --tests '*RuntimeRecoveryServiceTest' --tests '*PaperPlayerSessionListenerTest' --tests '*QueueServiceTest' --tests '*MatchLifecycleServiceTest'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 7: Staff Operations and Integrations

Status: Implemented

Goal:

- add admin diagnostics and safe partial reloads
- support scoreboard, placeholder, tab, combat-log, and party integrations
- expose public plugin-facing events for external extensions

Implemented scope:

- `/revprac status`, `/revprac metrics`, `/revprac integrations`, `/revprac audit [limit]`, `/revprac reload registries`, and `/revprac season <list|create|activate>` are operator-facing staff tools under `revprac.admin`
- safe partial reload is intentionally scoped to arena and kit registries, and fails closed while active queue tickets, matches, or arena reservations exist
- optional integration support starts as fail-soft presence probing for scoreboard, PlaceholderAPI, TAB, combat-log, and party surfaces without making any integration a hard dependency
- match lifecycle domain events are bridged into a versioned Bukkit `RevPracMatchEvent`; listener exceptions stay observational and do not break gameplay mutations

Exit criteria:

- staff can inspect and recover system state without unsafe reload behavior
- integrations are optional and fail soft when absent
- public events are documented and versioned
- adapter boundaries remain intact

Validation:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 8: Hardening and Scale

Status: Implemented base slice

Goal:

- add party queue, events and tournaments, seasons, replay or audit support, profiling, compatibility policy, and operational metrics

Implemented scope:

- logical seasons now have admin lifecycle operations for list, create, and activate; activation requires no active queue tickets, matches, or pending duel requests
- durable audit rows are stored through a V5 JDBC migration and cover staff operations plus lifecycle events
- operational metrics track published events, duel requests, completed matches, and torn-down matches
- the compatibility policy is explicit: Paper/Minecraft 1.21.11 is the supported target, standard `plugin.yml` remains the entrypoint, and public event API compatibility starts at `RevPracMatchEvent.CONTRACT_VERSION = 1`
- `domain.parties`, `application.parties`, `ports.parties`, and `InMemoryPartyRepository` provide a minimal party model with create, join, leave, status, leader promotion, disband, and queue eligibility snapshots
- `domain.tournaments`, `application.tournaments`, `ports.tournaments`, and `InMemoryTournamentRepository` provide a minimal tournament lifecycle with create, open, register, start, and complete transitions
- richer party matchmaking brackets, tournament command UX, physical PostgreSQL partitioning, and load-test harnesses remain future expansion points

Exit criteria:

- core duel and queue flows are already stable
- new features do not force a redesign of the base architecture
- performance costs are measured, not guessed

Validation:

```bash
./gradlew test --tests '*PartyTest' --tests '*PartyServiceTest' --tests '*InMemoryPartyRepositoryTest'
./gradlew test --tests '*TournamentTest' --tests '*TournamentServiceTest' --tests '*InMemoryTournamentRepositoryTest'
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*PaperMatchEventBridgeTest' --tests '*JdbcStorageFactoryTest'
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

Add load or scenario-specific gates only when the feature exists and the harness is real enough to trust.

## Dependency and Framework Policy

### Adopt now or early

- Paper native `plugin.yml`
- Paper Brigadier command API
- Adventure and MiniMessage
- Paper scheduler plus a dedicated executor for long IO
- YAML with explicit serializers for operator-managed config and registries
- HikariCP plus JDBC plus Flyway once persistence starts
- Caffeine for in-memory caches once cacheable data exists
- bStats only if and when telemetry policy is explicitly adopted

### Evaluate later

- Lombok only for narrowly scoped test builders or internal adapter DTOs
- Configurate
- PlaceholderAPI
- FastBoard or TAB
- Triumph GUI
- PacketEvents
- jOOQ
- Cloud v2

### Avoid for now

- ACF
- ProtocolLib by default
- `paper-plugin.yml`
- JPA or ORM by default
- paperweight or NMS unless a documented feature requires it

### Lombok Guidance

- Prefer Java 21 records, explicit constructors, and small factory methods for domain, config, and public APIs.
- If Lombok is used later, keep it out of core domain types unless a specific test or internal adapter case is better served by it.
- Do not let Lombok replace clear model boundaries or validation logic.

## Verification Gates

RevPrac should use escalating gates as the implementation grows.

### Current required gates

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Early feature gates

- keep unit tests for domain logic under `./gradlew test`
- add MockBukkit adapter tests when Paper-facing behavior needs coverage without a live server
- keep the full build-and-smoke gate in place for changes that touch boot, enable, disable, config, or packaging behavior

### Persistence gates

- add temp-dir persistence tests under `./gradlew test`
- PostgreSQL support and Testcontainers-backed migration/storage coverage are already introduced; keep extending that coverage as new storage paths land, especially future physical PostgreSQL season partitioning
- verify migration fixtures against empty, current, and upgraded schemas

### Scenario and scale gates

- add PvP scenario tests once the match engine can be driven end to end
- add performance or load gates once there is a repeatable harness and a meaningful baseline
- add a release matrix only when supported versions or compatibility promises become explicit

## Maintenance Rules

- Update this roadmap when the architecture, phase order, or verification model changes.
- Move stable decisions into `docs/DECISIONS.md` so the roadmap can stay focused on the next steps.
- Keep `docs/README.md` aligned with this file so agents can find the current plan quickly.
- When a phase completes, mark it here and trim any obsolete guidance instead of leaving stale instructions behind.
- Prefer small edits that preserve the current module structure until a clear reason exists to split it.

## Open Questions and Deferred Decisions

- Should ranked matchmaking use a simple MMR window, a tiered ladder, or a hybrid policy?
- Should player rating be global only, or season-scoped with an archived history model?
- Should import/export target YAML, JSON, or both for operator workflows?
- Which integrations become first-class dependencies and which stay optional soft hooks?
- Should PostgreSQL become a documented optional production target or remain a late-stage adapter?
- Which public events are stable enough to document as part of the plugin-facing contract?

These are intentionally deferred until the matching phase introduces real implementation pressure.

## Revision History

- 2026-04-30: Initial roadmap drafted from the current Paper scaffold, docs policy, architecture constraints, and phase sequence.
