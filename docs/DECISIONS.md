# Decision Log

This log records accepted project decisions. Add new entries when a choice affects architecture, workflow, compatibility, setup, or long-term product direction.

## 2026-04-30: Bootstrap as Public MIT Repository

- Decision: Create `XreatLabz/RevPrac` as a public GitHub repository.
- Decision: License the project under MIT.
- Rationale: The project is intended to be shareable and easy to adopt or extend.

## 2026-04-30: Start Docs-Only

- Decision: The first commit establishes project identity, documentation, and agent workflow only.
- Decision: Do not scaffold plugin code, Gradle, CI, or runtime harness in the first commit.
- Rationale: RevPrac should begin with a clear repository contract before implementation choices are locked in.

## 2026-04-30: Target Modern Paper 1.21

- Decision: RevPrac targets Modern Paper 1.21 direction for future plugin work.
- Decision: Future setup should follow official PaperMC documentation.
- Rationale: Modern Paper keeps the initial architecture aligned with current plugin development practices while avoiding legacy compatibility complexity.

## 2026-04-30: Adopt Harness Engineering

- Decision: `AGENTS.md` is a short map, and `docs/` is the source of truth.
- Decision: Meaningful project decisions and behavior changes must update documentation.
- Rationale: Agent-first development works best when context is repository-local, concise, inspectable, and verifiable.
