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

RevPrac now has a minimal Paper 1.21.11 plugin scaffold with Gradle, tests, CI, and a real Paper smoke harness. No arena, kit, queue, match, command, storage, or player-stat feature modules exist yet.
