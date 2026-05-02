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
- Plain Java contracts: `application.config`, `application.players`, `application.arenas`, `application.kits`, `application.matches`, `application.queues`, `application.result`, `domain.players`, `domain.arenas`, `domain.kits`, `domain.matches`, `domain.queues`, `ports.config`, `ports.lifecycle`, `ports.players`, `ports.arenas`, `ports.kits`, `ports.matches`, and `ports.queues` stay free of Bukkit/Paper imports.
- Player-session safety: `domain.players` owns immutable session/snapshot contracts; `application.players.PlayerSessionService` owns transitions, duplicate-join behavior, pending restorations, and shutdown recovery; `adapters.paper.players` owns Paper capture/restore and join/quit listener wiring.
- Arena registry: `domain.arenas` owns arena IDs, bounds, spawn points, definitions, and reservation value objects; `application.arenas.ArenaRegistryService` owns deterministic registration, reservation, release, and reset-hook orchestration; `adapters.paper.arenas` owns YAML registry files and Paper reset logging.
- Kit registry: `domain.kits` owns kit IDs, serialized inventory sections, potion effects, rules, and definitions; `application.kits.KitRegistryService` owns deterministic registration and enabled-kit listing; `adapters.paper.kits` owns Paper inventory/effect capture, apply, and YAML registry files.
- Match engine: `domain.matches` owns duel requests, match IDs, participants, sides, rulesets, origins, outcomes, end reasons, states, lifecycle events, and durable history entries; `application.matches` owns request intake, accept/deny/cancel, countdown, completion, settlement, teardown, spectator flow, and shutdown replay; `adapters.storage` owns in-memory match/request repositories and test settlement fixtures; `adapters.paper.matches` owns the listener, ticker, and player preparation boundary.
- Queue system: `domain.queues` owns queue modes, keys, tickets, ticket states, and matchmaking-window compatibility; `application.queues.QueueService` owns `/queue` join/leave/status, queue availability gating, queue session transitions, ranked base-rating seeding, and shutdown drainage; `application.queues.QueueMatchmakingService` owns deterministic sweeps and queued match handoff; `adapters.storage` owns the in-memory queue ticket repository; `adapters.paper.commands.RevPracQueueCommand` owns `/queue` parsing; `adapters.paper.queues` owns the synchronous ticker and quit listener.
- Queue config: `application.config.QueueConfig` owns `queues.matchmaking-period-ticks`, `queues.ranked-base-rating`, `queues.ticks-per-second`, and `queues.ranked-windows`.
- Storage runtime: `application.config.StorageConfig` owns `storage.backend`, `storage.sqlite-path`, and `storage.pool-maximum-size`; `adapters.storage.jdbc` owns the HikariCP datasource, Flyway migration, JDBC-backed player profile and player rating repositories, and the SQLite match settlement repository; `src/main/resources/plugin.yml` declares the runtime libraries; `BootstrapRuntime` shuts storage down after queue, match, and player teardown, while failed post-storage bootstrap closes storage before rethrowing.
- Command surface: `adapters.paper.commands.RevPracAdminCommand` owns `/revprac arena create <id> <radius>` and `/revprac kit save <id>` parsing, permission checks, player-only checks, and YAML-first persistence before runtime mutation; `adapters.paper.commands.RevPracDuelCommand` owns `/duel <player> <arena> <kit>`, explicit `/duel request <player> <arena> <kit>`, accept, deny/decline, cancel, spectate, and forfeit parsing through the Paper command layer.
- Match config: `application.config.MatchConfig` owns `matches.duel-request-expiry-seconds`, `matches.countdown-ticks`, `matches.max-duration-ticks`, and `matches.spectators-enabled` with documented defaults from `config.yml`.
- Tests: JUnit Jupiter and MockBukkit for plugin load/enable plus player-session adapter and lifecycle coverage.
- Runtime check: `scripts/smoke-run-paper.sh` boots a real Paper 1.21.11 server and confirms RevPrac enables.

## Planned Domains

- `arenas`: arena definitions, bounds, spawn points, validation, occupancy, and reset hooks.
- `kits`: kit metadata, inventories, armor, effects, rules, and serialization.
- `queues`: queue registration, matchmaking policy, ranked and unranked flow, active-ticket lifecycle, and leave/rejoin behavior.
- `players`: player profiles, session state, cooldowns, statistics, ratings, and persistence-facing models.
- `commands`: player, staff, and admin command surfaces with permission checks.
- `config-storage`: config loading, validation, migrations, and persistence adapters.
- `integrations`: optional hooks for scoreboards, placeholders, tab, combat logs, parties, rematch, post-match summaries, and external services.

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
- Pending player restorations are in memory until the persistence phase introduces durable player data.
- Arena and kit registry files are operator-managed YAML files in the plugin data folder: `arenas.yml` and `kits.yml`.
- Paper registry adapters must fail closed: invalid YAML, malformed item payloads, unknown potion effect keys, duplicate IDs, and mismatched inventory section sizes should reject load before publishing partial definitions.
- Kit item payloads are Base64 strings produced from `ItemStack.serializeAsBytes()`; domain kit records never store Bukkit objects.
- Arena world references use namespaced world keys such as `minecraft:world`.
- Arena reset is a port boundary in Phase 3. The Paper adapter logs reset requests; block rollback is intentionally deferred.
- Admin setup commands save YAML first and mutate in-memory registry services only after persistence succeeds.
- Queue tickets are in-memory runtime state only; ranked queue search ratings are seeded from durable player ratings, but join does not reserve an arena, and queued matches claim a pair first before `MatchLifecycleService.startQueuedMatch()` reserves an arena for the match.
- Storage bootstrap is fail-closed: bad config, invalid SQLite paths, Flyway migration failures, or post-storage bootstrap failures prevent the runtime from exposing repositories and close any opened storage runtime.
- JDBC-backed player profiles and ratings are durable Phase 6A records; `first_seen_at` is immutable after insert, and profile touches keep `last_seen_at` monotonic under clock rollback.
- Phase 6B records completed match history in `match_history` and aggregate per-player per-kit counters in `player_kit_stats`. Match settlement is idempotent by `match_id`: if history already exists, stat counters are not incremented again.
- `MatchLifecycleService` captures the completion instant on the completed match and settles it before teardown deletes it. If settlement fails, the completed match remains retained and players stay in their managed match context for operator retry through the same completed-match drain path.
- `MatchOrigin` records whether history came from a direct duel, ranked queue, or unranked queue. Active match state still remains in memory and only the completed history/stat result is durable in this slice.
- Seasons, PostgreSQL, import/export, rating progression updates, rematch, post-match summaries, and player-facing stat commands remain future slices.
- Ranked queue eligibility is gated by `KitRules.ranked`, but ranked-capable kits can still be used in unranked queues.
- Direct duel and queue flows share `PlayerAvailabilityService`, so active duel, match, or queue players cannot start a conflicting flow.
- The queue ticker is synchronous and uses the current server tick when it sweeps matchmaking.

## Next Architecture Step

The next code should add the remaining Phase 6 persistence slices: seasons, PostgreSQL, import/export, and competitive progression on top of the existing duel, match, queue, Phase 6A durable profile/rating storage, and Phase 6B match history/stat settlement. Keep match and queue rules testable without a live server, keep Paper adapters thin, and preserve in-memory active-match and queue-ticket assumptions until those later persistence slices are introduced.
