# RevPrac Documentation

This directory is the source of truth for RevPrac. `AGENTS.md` should stay short and point here instead of becoming a long manual.

## Start Here

- `PRODUCT.md`: product vision, audience, feature direction, and current non-goals.
- `ARCHITECTURE.md`: planned domain boundaries and dependency direction.
- `HARNESS_ENGINEERING.md`: how this repository adopts agent-first harness engineering.
- `DECISIONS.md`: accepted project decisions and defaults.

## Documentation Rules

- Add or update docs when project behavior, architecture, setup, verification, or contributor workflow changes.
- Prefer concise pages with clear ownership over one large instruction file.
- Record important decisions in `DECISIONS.md` close to the work that introduced them.
- Keep docs verifiable. If a page describes a command or workflow, include the command that proves it still works.

## Current State

RevPrac is in a docs-only bootstrap state. No plugin source, build system, CI, or runtime harness has been scaffolded yet.
