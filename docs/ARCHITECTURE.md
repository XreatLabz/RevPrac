# Planned Architecture

RevPrac has no plugin implementation yet. This page records the initial domain map so future code can grow with clear boundaries.

## Target Platform

- Modern Paper 1.21.
- Java plugin entrypoint when scaffolding begins.
- Official PaperMC documentation is the primary source for setup and API usage.
- Avoid server internals unless a documented feature requires them.

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
- Add tests around domain decisions before relying on live server behavior.

## Future Scaffold Notes

The first code scaffold should introduce only the smallest useful Paper plugin shell: build files, plugin metadata, a main plugin class, a startup verification path, and tests for non-server domain code.
