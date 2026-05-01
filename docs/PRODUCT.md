# Product Direction

RevPrac is a Minecraft practice core plugin for Paper 1.21.11. It is intended to provide the foundation for a competitive practice server experience similar in spirit to StrikePractice-style workflows.

## Audience

- Server owners who want a configurable practice core.
- Staff teams who need predictable tools for arenas, kits, queues, matches, and player management.
- Developers and agents extending the plugin over time.

## Long-Term Feature Direction

- Arena registration, validation, availability, and safe spawn handling.
- Kit definitions with inventory, armor, effects, and rule metadata.
- Queue flows for unranked, ranked, party, and event-style practice modes.
- Duel and match lifecycle management from request through teardown.
- Player state, statistics, ratings, cooldowns, and session restoration.
- Commands and permissions for players, staff, and administrators.
- Configuration and storage that are readable, versioned, and migration-friendly.
- Integration points for scoreboards, tab lists, placeholders, combat logging, and external services.

## Current Non-Goals

- Arena and kit registry/setup groundwork exists, but player-facing practice gameplay is not implemented yet.
- No queue orchestration, match lifecycle, ratings or stats tracking, or other competitive progression systems yet.
- No durable persistence beyond operator-managed local YAML registry/setup files.
- No compatibility promise beyond the documented Paper 1.21.11 direction.

## Product Principles

- Practice flows should be fast for players and predictable for staff.
- Core domain logic should be testable without a live Minecraft server whenever possible.
- Configuration should be explicit and recoverable from bad input.
- Future features should be introduced through small, documented, verifiable slices.
