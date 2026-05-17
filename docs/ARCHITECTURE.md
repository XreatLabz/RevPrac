# Architecture

RevPrac currently has a minimal Paper plugin scaffold plus early practice registry contracts. This page records the active platform choices and the domain map future code should grow into.

## Target Platform

- Paper/Minecraft 1.21.11.
- Java 21 toolchain and runtime floor.
- API-only Paper dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.
- Standard Bukkit/Paper `plugin.yml`; do not use experimental `paper-plugin.yml` yet.
- Official PaperMC documentation is the primary source for setup and API usage.
- Avoid server internals unless a documented feature requires them.

## Bootstrap And Config Boundary

- The plugin main class stays a `JavaPlugin` entry point named in `plugin.yml`.
- Keep the constructor side-effect free; lifecycle work belongs in `onLoad`, `onEnable`, and `onDisable`.
- Use `onLoad` for minimal bootstrap only.
- Use `onEnable` to save the bundled `config.yml` resource, read `JavaPlugin#getConfig()`, validate config, and wire runtime services.
- Store operator-editable defaults in `src/main/resources/config.yml` and copy them into the plugin data folder before config reads.
- Treat `paper-plugin.yml` as experimental and out of scope for this Phase 1 slice.

## Current Scaffold

- Build: Gradle 9.5.0 wrapper with Kotlin DSL.
- Entrypoint: `io.github.xreatlabz.revprac.RevPracPlugin`.
- Metadata: `src/main/resources/plugin.yml`.
- Bundled config: `src/main/resources/config.yml`.
- Runtime boundary: `bootstrap` wires Paper adapters to plain Java config validation and stores a shutdown-capable runtime.
- Plain Java contracts: `application.config`, `application.players`, `application.arenas`, `application.kits`, `application.matches`, `application.queues`, `application.operations`, `application.parties`, `application.recovery`, `application.result`, `application.seasons`, `application.tournaments`, `domain.players`, `domain.arenas`, `domain.kits`, `domain.matches`, `domain.queues`, `domain.parties`, `domain.seasons`, `domain.tournaments`, `ports.config`, `ports.integrations`, `ports.lifecycle`, `ports.operations`, `ports.players`, `ports.arenas`, `ports.kits`, `ports.matches`, `ports.parties`, `ports.queues`, `ports.recovery`, `ports.seasons`, and `ports.tournaments` stay free of Bukkit/Paper imports.
- Player-session safety: `domain.players` owns immutable session/snapshot contracts; `application.players.PlayerSessionService` owns transitions, duplicate-join behavior, pending restorations, and shutdown recovery; `adapters.paper.players` owns Paper capture/restore and join/quit listener wiring.
- Arena registry: `domain.arenas` owns arena IDs, bounds, spawn points, definitions, and reservation value objects; `application.arenas.ArenaRegistryService` owns deterministic registration, reservation, release, and reset-hook orchestration; `adapters.paper.arenas` owns YAML registry files and Paper reset logging.
- Kit registry: `domain.kits` owns kit IDs, serialized inventory sections, potion effects, rules, and definitions; `application.kits.KitRegistryService` owns deterministic registration and enabled-kit listing; `adapters.paper.kits` owns Paper inventory/effect capture, apply, and YAML registry files.
- Match engine: `domain.matches` owns duel requests, match IDs, participants, sides, rulesets, origins, outcomes, end reasons, states, lifecycle events, and durable history entries; `application.matches` owns request intake, accept/deny/cancel, countdown, completion, settlement, teardown, spectator flow, and shutdown replay; `adapters.storage` owns in-memory match/request repositories and test settlement fixtures; `adapters.paper.matches` owns the listener, ticker, and player preparation boundary.
- Queue system: `domain.queues` owns queue modes, keys, tickets, ticket states, and matchmaking-window compatibility; `application.queues.QueueService` owns `/queue` join/leave/status, queue availability gating, queue session transitions, ranked base-rating seeding, and shutdown drainage; `application.queues.QueueMatchmakingService` owns deterministic sweeps and queued match handoff; `adapters.storage` owns the in-memory queue ticket repository; `adapters.paper.commands.RevPracQueueCommand` owns `/queue` parsing; `adapters.paper.queues` owns the synchronous ticker and quit listener.
- Queue config: `application.config.QueueConfig` owns `queues.matchmaking-period-ticks`, `queues.ranked-base-rating`, `queues.ticks-per-second`, and `queues.ranked-windows`.
- Storage runtime: `application.config.StorageConfig` owns `storage.backend`, backend-specific path/connection settings, and `storage.pool-maximum-size`; `adapters.storage.jdbc` owns the HikariCP datasource, backend-specific Flyway migration locations, JDBC-backed player profile/rating/match-settlement repositories, and the JDBC season repository; `src/main/resources/plugin.yml` declares the runtime libraries; `BootstrapRuntime` shuts storage down after queue, match, and player teardown, while failed post-storage bootstrap closes storage before rethrowing.
- Runtime recovery: `ports.recovery.RuntimeRecoveryRepository` and `adapters.storage.jdbc.JdbcRuntimeRecoveryRepository` mirror managed player sessions, pending restorations, queue tickets, and match shells to sidecar tables. `application.recovery.RuntimeRecoveryService` hydrates safe state on bootstrap, keeps offline queue tickets lazy until join, resets pairing tickets to searching, and restarts active matches from a fresh countdown only when both combatants are online.
- Rating and records surface: `application.ratings` owns ranked settlement progression; `application.players.PlayerRecordQueryService` owns persisted stats/rating summary/history reads; `application.players.PlayerDirectoryService` owns exact player selector resolution; `application.players.PlayerRecordTransferService` owns YAML bundle import/export orchestration; `application.matches.RematchService` owns history-backed duel recreation within the configured duel-request TTL; `application.matches.PostMatchSummaryService` owns participant-only post-match summary formatting and best-effort delivery; `adapters.paper.commands.RevPracStatsCommand` owns self-only `/stats` parsing; `adapters.paper.commands.RevPracRecordsCommand` owns cross-player `/records` parsing; `adapters.paper.players.PaperPlayerRecordTransferFiles` owns the transfer artifact file boundary; `adapters.paper.matches.PaperPostMatchSummaryPort` owns plain-chat participant delivery.
- Operations and integrations: `application.operations.StaffOperationsService` owns staff diagnostics, safe registry reload, season lifecycle, audit reads, and metrics snapshots. `adapters.paper.integrations.PaperIntegrationProbe` performs fail-soft integration presence checks. `adapters.paper.events.PaperMatchEventBridge` publishes domain match events as versioned Bukkit `RevPracMatchEvent` instances.
- Party and tournament base: `domain.parties` and `application.parties.PartyService` own minimal in-memory party membership and queue eligibility snapshots; `domain.tournaments` and `application.tournaments.TournamentService` own minimal tournament lifecycle state. They are owned by `BootstrapRuntime` but do not yet have a Paper command UX.
- Command surface: `adapters.paper.commands.RevPracAdminCommand` owns `/revprac arena create <id> <radius>`, `/revprac kit save <id>`, `/revprac status`, `/revprac metrics`, `/revprac integrations`, `/revprac audit [limit]`, `/revprac reload registries`, and `/revprac season <list|create <id>|activate <id>>`; `adapters.paper.commands.RevPracDuelCommand` owns `/duel <player> <arena> <kit>`, explicit `/duel request <player> <arena> <kit>`, `/duel rematch <player>`, accept, deny/decline, cancel, spectate, and forfeit parsing through the Paper command layer.
- Match config: `application.config.MatchConfig` owns `matches.duel-request-expiry-seconds`, `matches.countdown-ticks`, `matches.max-duration-ticks`, and `matches.spectators-enabled` with documented defaults from `config.yml`.
- Tests: JUnit Jupiter and MockBukkit for plugin load/enable plus player-session adapter and lifecycle coverage.
- Runtime check: `scripts/smoke-run-paper.sh` boots a real Paper 1.21.11 server and confirms RevPrac enables.

## Planned Domains

- `arenas`: arena definitions, bounds, spawn points, validation, occupancy, and reset hooks.
- `kits`: kit metadata, inventories, armor, effects, rules, and serialization.
- `queues`: queue registration, matchmaking policy, ranked and unranked flow, active-ticket lifecycle, and leave/rejoin behavior.
- `players`: player profiles, session state, cooldowns, statistics, ratings, and persistence-facing models.
- `ratings`: ranked progression rules and rating persistence behavior.
- `stats`: per-player summary and history query models.
- `commands`: player, staff, and admin command surfaces with permission checks.
- `config-storage`: config loading, validation, migrations, and persistence adapters.
- `integrations`: optional hooks for scoreboards, placeholders, tab, combat logs, parties, richer post-match UX, and external services.

## Boundary Rules

- Keep core practice logic separate from Bukkit/Paper event handlers where practical.
- Parse and validate external data at boundaries before passing it into domain logic.
- Keep runtime integrations explicit rather than hidden behind global lookups.
- Keep bootstrap/config parsing at the edge, then pass immutable config models inward.
- Resolve SQLite storage paths under the plugin data folder unless the configured path is absolute, and run Flyway migrations before exposing repositories.
- Add tests around domain decisions before relying on live server behavior.
- Do not restore or teleport directly inside `PlayerJoinEvent`; Paper join handling that may restore location is deferred to the next server tick.
- Paper restore closes open inventories, validates the target world/location before inventory mutation, and includes cursor state in the captured inventory snapshot.
- Bootstrap schedules session initialization for players already online when the plugin enables.
- Pending player restorations are live in memory and mirrored to runtime recovery storage.
- Arena and kit registry files are operator-managed YAML files in the plugin data folder: `arenas.yml` and `kits.yml`.
- Paper registry adapters must fail closed: invalid YAML, malformed item payloads, unknown potion effect keys, duplicate IDs, and mismatched inventory section sizes should reject load before publishing partial definitions.
- Kit item payloads are Base64 strings produced from `ItemStack.serializeAsBytes()`; domain kit records never store Bukkit objects.
- Arena world references use namespaced world keys such as `minecraft:world`.
- Arena reset is a port boundary in Phase 3. The Paper adapter logs reset requests; block rollback is intentionally deferred.
- Admin setup commands save YAML first and mutate in-memory registry services only after persistence succeeds.
- Queue tickets are in-memory runtime state mirrored to recovery storage; ranked queue search ratings are seeded from durable player ratings, but join does not reserve an arena, and queued matches claim a pair first before `MatchLifecycleService.startQueuedMatch()` reserves an arena for the match.
- Storage bootstrap is fail-closed: bad config, invalid SQLite paths, Flyway migration failures, or post-storage bootstrap failures prevent the runtime from exposing repositories and close any opened storage runtime.
- JDBC-backed player profiles and ratings are durable Phase 6A records; `first_seen_at` is immutable after insert, and profile touches keep `last_seen_at` monotonic under clock rollback.
- Phase 6B records completed match history in `match_history` and aggregate per-player per-kit counters in `player_kit_stats`. Match settlement is idempotent by `match_id`: if history already exists, stat counters are not incremented again.
- Phase 6C adds deterministic ranked progression for completed ranked queue matches only: `WIN` and `FORFEIT` change ratings, direct duel/unranked queue/timeout/shutdown outcomes do not, the formula uses simple Elo with `K = 32`, and ratings floor at `1`. Ranked progression still applies only after the `match_history` insert proves the settlement is new.
- Seasons are logical storage scope only in the current slice: the single active season row is resolved per repository operation, ratings/history/stats follow that active season, and `player_profiles` remain global across seasons.
- `MatchLifecycleService` captures the completion instant on the completed match and settles it before teardown deletes it. If settlement fails, the completed match remains retained and players stay in their managed match context for operator retry through the same completed-match drain path.
- `MatchOrigin` records whether history came from a direct duel, ranked queue, or unranked queue. Active match state still remains in memory and only the completed history/stat result is durable in this slice.
- `PlayerRecordQueryService` and `/stats` stay self-only, with `revprac.stats` defaulting to `true`. `/records` is operator-facing, uses UUID-first then exact case-insensitive `lastKnownName` resolution, and exposes current-season summary/history plus bounded YAML import/export through `revprac.records.lookup` and `revprac.records.transfer`.
- Player-record transfer imports fail closed on duplicate logical rows (`ratings.kit-id`, `stats.kit-id`, `history.match-id`), roll back profile/rating/stat/player-history mutations together on import failure, and JDBC transfer export snapshots the active season through one connection so profile, ratings, stats, and history come from the same current-season read.
- SQLite remains the default backend, PostgreSQL is an optional backend, and physical PostgreSQL season partitioning remains a future scale slice.
- Rematch reuses the latest mutual completed match inside the configured duel-request TTL, while post-match summaries are plain-chat, participant-only, non-persistent, and sent only after settlement plus teardown fully succeed.
- Registry reload is partial by design: it only reloads arena and kit YAML after validation, and only when no active queue tickets, matches, or arena reservations exist. Queue/match timing config still requires a restart because the live services and tickers snapshot those values at construction.
- Season activation requires no active queue tickets, matches, or pending duel requests. Ratings/history/stats switch by logical active-season lookup; profiles remain global.
- Public Bukkit events are observational only. They wrap the existing domain `MatchEvent` payload in `RevPracMatchEvent` with `CONTRACT_VERSION = 1`; listener failures are caught at the domain event publisher boundary.
- Ranked queue eligibility is gated by `KitRules.ranked`, but ranked-capable kits can still be used in unranked queues.
- Direct duel and queue flows share `PlayerAvailabilityService`, so active duel, match, or queue players cannot start a conflicting flow.
- The queue ticker is synchronous and uses the current server tick when it sweeps matchmaking.

## Next Architecture Step

The next code should deepen the Phase 8 base only where real usage justifies it: player-facing party and tournament command UX, measured load/profiling harnesses, richer optional integration adapters, and physical PostgreSQL season partitioning if active-season scale evidence demands it. Keep match and queue rules testable without a live server, keep Paper adapters thin, and preserve in-memory active-match and queue-ticket ownership unless a documented scale problem requires a new model.
