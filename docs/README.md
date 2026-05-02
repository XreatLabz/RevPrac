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

RevPrac now has a minimal Paper 1.21.11 plugin scaffold with Gradle, tests, CI, a real Paper smoke harness, Phase 1 bootstrap/config contracts, Phase 2 player-session safety contracts, Phase 3 arena/kit registry setup, Phase 4 duel/match engine contracts and adapters, Phase 5 queue/matchmaking contracts and adapters, and Phase 6A durable player profile plus rating storage. Queue tickets remain in-memory runtime state, while match-history settlement, stats, seasons, PostgreSQL, import/export, rematch, post-match summaries, and other future persistence-backed feature modules remain deferred.
