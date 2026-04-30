# RevPrac Agent Guide

This file is the short map for agents working in RevPrac. Keep it compact. Put durable project knowledge in `docs/` and link to it from here.

## Read First

- `docs/README.md` is the documentation index.
- `docs/PRODUCT.md` defines the product direction and non-goals.
- `docs/ARCHITECTURE.md` defines planned domain boundaries.
- `docs/HARNESS_ENGINEERING.md` defines how this repo adopts harness engineering.
- `docs/DECISIONS.md` records accepted project decisions.

## Working Rules

- Treat `docs/` as the source of truth for repository knowledge.
- Before changing code, behavior, architecture, workflow, or public docs, read the relevant `docs/` page first.
- When a meaningful decision is made, update `docs/DECISIONS.md`.
- When implementation changes alter product behavior, architecture, setup, or verification commands, update the matching docs in the same change.
- Prefer small, verifiable changes with clear acceptance checks.
- Keep generated or experimental work out of the main code path unless it is documented and intentionally adopted.

## Project Direction

RevPrac is planned as a Modern Paper 1.21 Minecraft practice core plugin inspired by StrikePractice-style practice workflows. Future plugin setup should follow official PaperMC documentation and avoid introducing server internals unless a documented feature requires them.

## Delegation

Use subagents only when delegation is appropriate and explicitly useful for the active task. Give each subagent a concrete, bounded objective, and keep final responsibility in the parent session: inspect results, integrate changes, run verification, and explain the outcome.
