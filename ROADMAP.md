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

- no arena, kit, queue, match, rating, or persistence feature modules yet
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

Status: Planned

Goal:

- introduce the service graph, core value objects, ports, result/error taxonomy, and immutable config models
- implement one plain Java service that is testable without Paper

Exit criteria:

- core bootstrap wiring is explicit and discoverable
- domain contracts do not depend on Bukkit/Paper types
- config parsing and validation fail fast with clear errors
- the first service has direct unit coverage

Validation:

```bash
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 2: Player Session Safety

Status: Planned

Goal:

- add a player context model for `LOBBY`, `QUEUE`, `MATCH`, `SPECTATOR`, and `EDITOR`
- implement inventory, location, and state snapshots
- recover cleanly on join, quit, and plugin disable

Exit criteria:

- players can be returned to a safe state after interruptions
- session transitions are explicit and tested
- join/quit/disable flows are safe without a live server

Validation:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 3: Arena and Kit Registries

Status: Planned

Goal:

- add typed arena and kit definitions
- validate registry content
- support occupancy and reservation rules
- implement reset hooks and kit serialization
- add admin setup commands for operator-managed content

Exit criteria:

- invalid arena and kit data is rejected before use
- registry operations are deterministic and safe under contention
- arena reservation and reset behavior is covered by tests
- setup commands do not leak domain concerns into Paper handlers

Validation:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
```

### Phase 4: Duel and Match Engine

Status: Planned

Goal:

- implement duel requests, countdowns, match aggregate state, win/loss/forfeit/timeout handling, spectator flow, teardown, and domain events

Exit criteria:

- a match can be created, run, completed, and torn down without orphaned state
- countdown and timeout behavior is deterministic
- spectators are isolated from active combat state
- domain events exist for match lifecycle transitions

Validation:

```bash
./gradlew test
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
