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

Current repo shape is intentionally small:

- arena and kit registry groundwork exists; the direct duel and match engine is implemented; queue, ranked progression, rating, stats, and durable persistence feature modules are still planned
- no Gradle subprojects yet
- no public plugin API surface yet

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

- queueing, matchmaking, ranked progression, ratings, stats, parties, rematch, post-match summaries, and durable persistence stay future
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

Status: Planned

Goal:

- add unranked and ranked queues
- issue and manage queue tickets
- prevent double-queue states
- apply MMR windows and selection policy
- keep concurrency behavior safe

Exit criteria:

- a player cannot be placed into conflicting queue states
- matchmaking decisions are predictable and testable
- ranked and unranked flows stay separate in the domain model
- concurrency-sensitive code has regression coverage

Validation:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 6: Persistence, Ratings, and Migrations

Status: Planned

Goal:

- persist profiles, stats, ratings, seasons, and match history
- use SQLite as the default local store
- add optional PostgreSQL support
- add Flyway migrations
- support import/export for operator workflows

Exit criteria:

- persistent data survives restart and reload cycles
- migrations apply cleanly from empty and upgraded states
- rating updates and season transitions are deterministic
- persistence adapters are isolated behind ports

Validation:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

When persistence work lands, add dedicated fixture coverage for temp-dir and Testcontainers-backed runs and keep them wired into `./gradlew test`.

### Phase 7: Staff Operations and Integrations

Status: Planned

Goal:

- add admin diagnostics and safe partial reloads
- support scoreboard, placeholder, tab, combat-log, and party integrations
- expose public plugin-facing events for external extensions

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

Status: Deferred until core flows are stable

Goal:

- add rematch, party queue, events and tournaments, seasons, replay or audit support, profiling, compatibility policy, and operational metrics

Exit criteria:

- core duel and queue flows are already stable
- new features do not force a redesign of the base architecture
- performance costs are measured, not guessed

Validation:

```bash
./gradlew test
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
- add Testcontainers-backed coverage for migration and storage adapters when PostgreSQL support is introduced
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
