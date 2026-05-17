# Decision Log

This log records accepted project decisions. Add new entries when a choice affects architecture, workflow, compatibility, setup, or long-term product direction.

## 2026-04-30: Bootstrap as Public MIT Repository

- Decision: Create `XreatLabz/RevPrac` as a public GitHub repository.
- Decision: License the project under MIT.
- Rationale: The project is intended to be shareable and easy to adopt or extend.

## 2026-04-30: Start Docs-Only

- Decision: The first commit establishes project identity, documentation, and agent workflow only.
- Decision: Do not scaffold plugin code, Gradle, CI, or runtime harness in the first commit.
- Rationale: RevPrac should begin with a clear repository contract before implementation choices are locked in.

## 2026-04-30: Target Paper 1.21.11

- Decision: RevPrac targets Paper/Minecraft 1.21.11 for future plugin work.
- Decision: Future setup should follow official PaperMC documentation.
- Rationale: Paper 1.21.11 keeps the initial architecture aligned with current plugin development practices while avoiding legacy compatibility complexity.

## 2026-04-30: Adopt Harness Engineering

- Decision: `AGENTS.md` is a short map, and `docs/` is the source of truth.
- Decision: Meaningful project decisions and behavior changes must update documentation.
- Rationale: Agent-first development works best when context is repository-local, concise, inspectable, and verifiable.

## 2026-04-30: Use ROADMAP.md as Execution Roadmap

- Decision: `ROADMAP.md` is the execution roadmap for phased implementation work.
- Decision: Phase exit criteria are the gate for advancing scope, and dependency additions should stay within the documented phase unless a later phase explicitly requires them.
- Decision: `docs/` remains the source of truth for behavior, setup, and workflow changes; the roadmap coordinates execution, not policy.
- Rationale: The repository needs a single working plan with clear gates so implementation stays staged, dependency growth stays intentional, and docs remain authoritative.

## 2026-04-30: Scaffold API-Only Paper Base

- Decision: Scaffold RevPrac as an API-only Paper plugin targeting Paper/Minecraft `1.21.11`.
- Decision: Use Gradle Wrapper `9.5.0`, Kotlin DSL, Java 21 toolchain, and `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.
- Decision: Use standard `plugin.yml`, not experimental `paper-plugin.yml`.
- Decision: Do not add `paperweight-userdev`, Shadow, NMS, databases, commands, or practice feature modules in the base scaffold.
- Decision: Verify the base with JUnit Jupiter, MockBukkit, Spotless, JaCoCo report generation, and a real Paper 1.21.11 smoke boot.
- Rationale: The project needs a small, verifiable runtime base before feature work, while keeping internals and packaging complexity out until a documented feature requires them.

## 2026-04-30: Phase 1 Bootstrap Config Boundary

- Decision: Keep the Phase 1 bootstrap path on standard `plugin.yml` plus a bundled `config.yml` resource.
- Decision: Keep the plugin constructor side-effect free and perform bootstrap/config wiring in lifecycle methods, with config save and validation in `onEnable()`.
- Decision: Treat `paper-plugin.yml` as experimental and out of scope for this slice.
- Rationale: PaperMC docs keep `plugin.yml` as the stable main-class entrypoint, recommend avoiding constructor work, and expose config through `JavaPlugin#getConfig()` after the resource has been saved.

## 2026-04-30: Phase 2 Player Session Safety Boundary

- Decision: Model player safety with plain Java `domain.players` session and snapshot contracts plus `application.players.PlayerSessionService`.
- Decision: Keep Paper/Bukkit player capture, restore, and join/quit events in `adapters.paper.players`.
- Decision: Capture the baseline snapshot once on first transition from `LOBBY` into a managed context and preserve it across managed-context transitions until restoration.
- Decision: Defer Paper join restoration to the next server tick so no teleport or restore path runs directly inside `PlayerJoinEvent`.
- Decision: Include inventory cursor state in Phase 2 snapshots and close open inventories before Paper restore reapplies baseline inventory.
- Decision: Schedule player-session initialization for players already online when the plugin enables.
- Decision: Use in-memory active session and pending restoration repositories for Phase 2; durable restart/crash recovery is deferred to Phase 6 persistence.
- Decision: Let `BootstrapRuntime.shutdown()` close intake and restore online managed players through `PlayerSessionService.shutdownAll()` before marking runtime shutdown complete.
- Rationale: Phase 2 needs interruption safety before arenas, kits, and matches exist, but adding durable storage before the persistence phase would broaden the dependency surface too early.

## 2026-05-01: Phase 3 Arena and Kit Registry Boundary

- Decision: Model arenas and kits with plain Java domain records and application services; keep Bukkit/Paper types in `adapters.paper`.
- Decision: Use operator-managed YAML files named `arenas.yml` and `kits.yml` in the plugin data folder for Phase 3 registry content.
- Decision: Keep arena reservations in memory and use atomic create semantics for registry repositories.
- Decision: Treat arena reset as a port boundary in Phase 3; the Paper adapter logs reset requests, and block rollback is deferred until match teardown work needs it.
- Decision: Store kit item payloads as Base64 strings from `ItemStack.serializeAsBytes()` and validate payloads before publishing or applying kits.
- Decision: Register `/revprac` through standard `plugin.yml` command metadata with a `revprac.admin` permission node.
- Decision: Admin setup commands persist YAML before mutating runtime registry state, so save failures do not desynchronize memory from disk.
- Decision: Arena setup stores namespaced Paper world keys instead of raw world names.
- Rationale: Phase 3 needs operator-editable content and safe setup flows without introducing persistence, NMS, or match-engine complexity before later roadmap phases.

## 2026-05-01: Phase 4 Direct Duel and Match Engine Boundary

- Decision: Model Phase 4 as direct 1v1 duel requests plus a single match aggregate, not queueing, parties, or matchmaking.
- Decision: Keep duel requests and active matches in memory for now through in-memory repositories; durable persistence is deferred to a later phase.
- Decision: Expose the public match command surface through `/duel` for request, accept, deny/decline, cancel, spectate, and forfeit actions. Normal requests use `/duel <player> <arena> <kit>`, and the explicit `/duel request <player> <arena> <kit>` form exists for target names that collide with subcommands.
- Decision: Use the Paper ticker as the deterministic source of countdown and timeout progression, with `MatchConfig` defaults of 30 seconds request expiry, 100 countdown ticks, 12000 max duration ticks, and spectators enabled.
- Decision: Shutdown order closes duel intake, cancels the ticker, tears down matches, and then shuts down player sessions.
- Decision: Keep richer event logging, metrics, block rollback, parties, rematch, post-match summaries, ranked progression, ratings, and stats outside the Phase 4 scope.
- Rationale: Phase 4 should deliver a safe, testable duel engine with clear lifecycle boundaries before queueing, persistence, and broader competitive progression are introduced.

## 2026-05-01: Phase 5 Queues and Matchmaking Boundary

- Decision: Model queueing as explicit `UNRANKED` and `RANKED` modes with in-memory active tickets and in-memory per-player plus kit search ratings.
- Decision: Gate ranked queue entry by `KitRules.ranked`, but allow ranked-capable kits to join unranked queues.
- Decision: Register `/queue` through standard `plugin.yml` with `/queue join ranked <kit>`, `/queue join unranked <kit>`, `/queue leave`, and `/queue status`.
- Decision: Keep queue matchmaking deterministic with FIFO unranked pairing, ranked wait-time plus rating-window selection, and synchronous Paper ticker sweeps using the current server tick.
- Decision: Share availability between direct duel and queue flows, and start queued matches only after a ticket pair is claimed and handed to `MatchLifecycleService.startQueuedMatch()`.
- Decision: Defer durable ratings, progression, stats, seasons, parties, rematch, public events, metrics, and persistence to Phase 6.
- Rationale: Phase 5 should deliver safe matchmaking now while keeping long-term competitive progression and storage concerns isolated for the persistence phase.

## 2026-05-01: Phase 5 Queue Review Corrections

- Decision: `QueueService` uses `PlayerStatePort` for generic online checks during queue join and shutdown drain instead of depending on the match-specific `MatchPlayerPort`.
- Decision: `BootstrapRuntime.shutdown()` closes `QueueMatchmakingService` intake before cancelling the queue ticker and draining queue tickets.
- Rationale: Queue presence checks belong on the shared player-state boundary, and shutdown must stop new matchmaking sweeps before queue-drain teardown begins.

## 2026-05-02: Phase 6A Durable Profiles and Ratings

- Decision: Implement Phase 6A as a SQLite-only durable slice for player profiles and queue rating seeds, with `storage.backend`, `storage.sqlite-path`, and `storage.pool-maximum-size` owned by `StorageConfig`.
- Decision: Declare HikariCP, Flyway, and sqlite-jdbc as runtime libraries in `plugin.yml`, and run Flyway migrations from `classpath:db/migration` during bootstrap before repositories are exposed.
- Decision: Keep storage bootstrap fail-closed and close the storage runtime after queue, match, and player teardown in `BootstrapRuntime.shutdown()`.
- Decision: Defer match-history settlement, stats, seasons, PostgreSQL, and import/export to later Phase 6 slices.
- Rationale: RevPrac needs a small durable spine for player identity and queue seeding before broad competitive history and alternative storage backends are added.

## 2026-05-02: Phase 6A Persistence Failure-Path Hardening

- Decision: Close the JDBC storage runtime if any bootstrap step after storage creation fails before `RevPracPlugin` receives a `BootstrapRuntime`.
- Decision: Preserve the stored profile `first_seen_at` on JDBC conflict updates and keep profile `last_seen_at` monotonic when the system clock moves backward.
- Rationale: Failed enable paths must not leak Hikari resources, and durable player identity timestamps should remain stable even when host time is corrected.

## 2026-05-02: Phase 6B Match History and Stats Settlement

- Decision: Persist completed match history and aggregate per-player per-kit stats as a SQLite/Flyway V2 slice using `match_history` and `player_kit_stats`.
- Decision: Keep active match state, queue tickets, duel requests, player sessions, and pending restorations in memory; only completed history and stat aggregates are durable in this slice.
- Decision: Settlement is owned by `MatchLifecycleService` through `MatchSettlementService`, runs after a completed match is saved and before teardown deletes it, captures the completion instant on the retained `Match`, and retries through the same completed-match drain path.
- Decision: Make settlement idempotent by inserting `match_history` first and applying stat deltas only when that insert creates a new row.
- Decision: Record `MatchOrigin` for direct duel, ranked queue, and unranked queue history, while leaving rating progression, seasons, PostgreSQL, import/export, rematch, post-match summaries, and player-facing stat commands deferred.
- Rationale: Completed match data must survive restart without making active gameplay state durable yet, and teardown retry paths must not double-count stats.

## 2026-05-04: Phase 6C Ranked Progression And Self Stats

- Decision: Apply ranked rating progression only for completed ranked queue matches with `WIN` or `FORFEIT` outcomes. Direct duel, unranked queue, timeout, and shutdown completions do not change ratings.
- Decision: Use a simple Elo progression with `K = 32` and a floor of `1` for ranked ratings. Rating updates remain idempotent because settlement only applies them when the `match_history` insert creates a new row.
- Decision: Expose `/stats` as a self-only command with `revprac.stats` defaulting to `true`, and support `summary <kit>` plus `history [page]` over persisted per-kit stats, ranked-kit ratings, and recent match history.
- Decision: Cap `/stats history [page]` to a stable maximum page of `100`, and render missing opponent profile names as `Unknown player` instead of exposing raw UUIDs in player-facing history output.
- Decision: Keep PostgreSQL, seasons, import/export, offline/cross-player lookup, active match recovery, active queue recovery, and season partitioning deferred.
- Rationale: Phase 6C adds the competitive progression and self-service query slice without making active gameplay state durable yet or broadening the storage surface prematurely.

## 2026-05-06: Task 1 Optional PostgreSQL And Logical Active Seasons

- Decision: Storage now supports SQLite as the default backend and PostgreSQL as an optional backend, with backend-specific config validation so `storage.sqlite-path` is required only for SQLite.
- Decision: The active season is logical scope, not process-start state: repositories resolve the current active season for each rating/history/stats operation, so season changes take effect without reopening storage.
- Decision: `player_profiles` remain global across seasons, while ratings, match history, and per-kit stats are season-scoped.
- Decision: Physical PostgreSQL table partitioning by season remains deferred, along with the rest of the unfinished Phase 6 completion work at that point: import/export, offline/cross-player lookup, active match recovery, and active queue recovery.
- Rationale: Task 1 broadens the storage adapter safely without changing active runtime ownership boundaries, and it keeps logical season rollover available now without committing to a premature physical partitioning design.

## 2026-05-06: Task 2 Records Lookup And Transfer

- Decision: Keep `/stats` self-only, and expose cross-player lookup plus transfer through a separate `/records` command with `revprac.records` defaulting to `op`, `revprac.records.lookup`, and `revprac.records.transfer`.
- Decision: Player selection for `/records` is UUID-first, then exact case-insensitive `lastKnownName`; ambiguous names fail closed and require UUID disambiguation.
- Decision: Player record transfer artifacts are schema-versioned YAML bundles stored under `exports/player-records/<uuid>.yml` and imported only from simple `.yml` filenames under `imports/player-records/`.
- Decision: Transfer imports fail closed on duplicate logical rows: repeated `ratings.kit-id`, `stats.kit-id`, or `history.match-id` values are invalid bundle content.
- Decision: JDBC-backed transfer export captures the active season once and reads profile, ratings, stats, and history through one JDBC connection for a consistent current-season snapshot.
- Decision: Transfer imports are atomic for the addressed player record state: profile, current-season ratings, current-season stats, and any player-involving history rows added by the import roll back together if a later import step fails.
- Decision: Import/export operates on the current active season for ratings, stats, and history while keeping `player_profiles` global, and repeated import must be idempotent by replacing per-player rating/stat aggregates while only upserting match-history rows.
- Rationale: Operator-facing lookup and transfer need an exact, bounded contract that stays safe for seasonal storage, avoids path traversal, and does not reintroduce double-counting on repeated imports.

## 2026-05-06: Task 3 Rematch And Post-Match Summaries

- Decision: Add `/duel rematch <player>` under the existing `revprac.duel` permission, while preserving `/duel request <player> <arena> <kit>` for target names that collide with reserved subcommands such as `rematch`.
- Decision: Resolve rematch eligibility from the latest mutual completed current-season match between the requester and target, regardless of participant order or original flow, and reuse `matches.duel-request-expiry-seconds` as the exclusive rematch window.
- Decision: Rematch creates a fresh normal duel request from the current time through `DuelRequestService.request(...)`, so duplicate, busy, intake-closed, and resource-validation failures stay centralized and unchanged.
- Decision: Post-match summaries are plain chat, participant-only, best-effort, non-persistent messages sent only after settlement, lobby return, arena release, and match deletion have all succeeded.
- Decision: Shutdown outcomes do not send summaries, while ranked decisive queue summaries include exact per-recipient rating deltas derived from the pre/post progression captured during the first successful settlement attempt and replayed across teardown retries without recomputing ratings.
- Rationale: Rematch should stay a thin history-backed convenience path on top of the existing duel-request contract, and post-match messaging must never compromise teardown or accidentally double-apply ranked progression during retryable drains.

## 2026-05-17: Task 4 Runtime Recovery Sidecars

- Decision: Keep live active queue tickets, active matches, player sessions, and pending restorations in memory, and mirror recoverable runtime state into JDBC sidecar tables instead of replacing live repositories with fully durable repositories.
- Decision: Recover pending restorations first, then managed sessions, queue tickets, and match shells during bootstrap before already-online player tracking and ticker startup.
- Decision: Offline managed sessions become `PLUGIN_DISABLE` pending restorations, offline queue tickets stay in recovery storage until the player rejoins, and `PAIRING` queue tickets recover as `SEARCHING`.
- Decision: Active matches recover only when both combatants are online, restart from a fresh countdown, and drop spectators. Completed retained matches can recover without online combatants.
- Decision: Match history uniqueness is season-scoped with `(season_id, match_id)`, so current-season imports can copy the same historical match id into a newly active season without being skipped by global match-id idempotency.
- Rationale: Runtime recovery should prevent common restart/reload loss without pretending to preserve unsafe mid-fight Bukkit state or widening active queue/match ownership beyond the existing in-memory model.

## 2026-05-17: Phase 7 Staff Operations And Public Events

- Decision: `/revprac` now owns staff diagnostics, registry reload, integration probes, audit reads, metrics reads, and season lifecycle commands under the existing `revprac.admin` permission.
- Decision: Partial reload is intentionally limited to arena and kit registries. It validates YAML first, swaps services only after validation, and refuses to run with active queue tickets, matches, or arena reservations.
- Decision: Optional integration support begins as fail-soft presence checks for scoreboard, PlaceholderAPI, TAB, combat-log, and party surfaces without making those plugins hard dependencies.
- Decision: Public plugin-facing events wrap the existing domain `MatchEvent` stream in Bukkit `RevPracMatchEvent` with `CONTRACT_VERSION = 1`; event listeners are observational and cannot break gameplay mutations.
- Rationale: Staff operations should improve recovery and observability without mutating live queue/match config in place, and public events should expose the existing domain lifecycle rather than duplicating it.

## 2026-05-17: Phase 8 Hardening Base

- Decision: Add V5 `audit_log` storage and lightweight in-memory operational metrics. Metrics count event publications and key lifecycle totals; audit rows capture staff operations and match lifecycle events.
- Decision: Season admin supports list, create, and activate. Activation is transactional in JDBC and requires no active queue tickets, matches, or pending duel requests.
- Decision: Paper/Minecraft `1.21.11` remains the compatibility target, standard `plugin.yml` remains the entrypoint, and `RevPracMatchEvent.CONTRACT_VERSION = 1` starts the public event API compatibility contract.
- Decision: Add party and tournament support as separate plain-Java modules instead of widening the existing single-player `QueueTicket` and 1v1 `MatchParticipants` models in place.
- Decision: The party slice covers in-memory create, join, leave, leader promotion, disband, status, and exact-size queue eligibility snapshots. The tournament slice covers in-memory create, open, register, start, and complete lifecycle.
- Rationale: Phase 8 should harden and extend the product without forcing a redesign of the proven duel, queue, and match lifecycle paths before richer UX and load evidence exist.
