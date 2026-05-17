# RevPrac Documentation

This directory is the source of truth for RevPrac. `AGENTS.md` should stay short and point here instead of becoming a long manual.

## Start Here

- `PRODUCT.md`: product vision, audience, feature direction, and current non-goals.
- [`ROADMAP.md`](../ROADMAP.md): implementation phases, dependency policy, and verification gates.
- `ARCHITECTURE.md`: planned domain boundaries and dependency direction.
- `BUILDING.md`: local build, test, smoke, CI, and dependency update workflow.
- `HARNESS_ENGINEERING.md`: how this repository adopts agent-first harness engineering.
- `DECISIONS.md`: accepted project decisions and defaults.

## Documentation Rules

- Add or update docs when project behavior, architecture, setup, verification, or contributor workflow changes.
- Prefer concise pages with clear ownership over one large instruction file.
- Record important decisions in `DECISIONS.md` close to the work that introduced them.
- Keep docs verifiable. If a page describes a command or workflow, include the command that proves it still works.

## Current State

RevPrac now has a Paper 1.21.11 practice-core scaffold with Gradle, tests, CI, a real Paper smoke harness, player-session safety, arena/kit setup, direct duels, queue matchmaking, durable profiles/ratings/history/stats, logical seasons, player record lookup/transfer, rematch, post-match summaries, runtime recovery sidecars, staff operations, public match events, audit, metrics, and minimal party/tournament domain services. Storage supports SQLite and optional PostgreSQL; ratings/history/stats are scoped by the current logical active season while `player_profiles` remain global. Live queue and match repositories stay in memory, with JDBC recovery sidecars used to recover safe runtime state after restart. Physical PostgreSQL season partitioning and richer scale harnesses remain future work.
