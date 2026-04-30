# Architecture

RevPrac currently has a minimal Paper plugin scaffold. This page records the active platform choices and the domain map future code should grow into.

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
- Plain Java contracts: `application.config`, `application.result`, `ports.config`, and `ports.lifecycle` stay free of Bukkit/Paper imports.
- Tests: JUnit Jupiter and MockBukkit for plugin load/enable.
- Runtime check: `scripts/smoke-run-paper.sh` boots a real Paper 1.21.11 server and confirms RevPrac enables.

## Planned Domains

- `arenas`: arena definitions, bounds, spawn points, validation, occupancy, and reset hooks.
- `kits`: kit metadata, inventories, armor, effects, rules, and serialization.
- `queues`: queue registration, matchmaking policy, ranked and unranked flow, and leave/rejoin behavior.
- `matches`: match creation, countdowns, state transitions, win/loss handling, teardown, and recovery.
- `players`: player profiles, session state, cooldowns, statistics, ratings, and persistence-facing models.
- `commands`: player, staff, and admin command surfaces with permission checks.
- `config-storage`: config loading, validation, migrations, and persistence adapters.
- `integrations`: optional hooks for scoreboards, placeholders, tab, combat logs, parties, and external services.

## Boundary Rules

- Keep core practice logic separate from Bukkit/Paper event handlers where practical.
- Parse and validate external data at boundaries before passing it into domain logic.
- Keep runtime integrations explicit rather than hidden behind global lookups.
- Keep bootstrap/config parsing at the edge, then pass immutable config models inward.
- Add tests around domain decisions before relying on live server behavior.

## Next Architecture Step

The next code should build on the bootstrap/config boundary before adding commands or Paper event handlers. Keep practice logic testable without a live server, keep config loading at the Paper boundary, and use Paper adapters only at runtime boundaries.
