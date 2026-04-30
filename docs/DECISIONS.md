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

## 2026-04-30: Target Paper 1.21.11

- Decision: RevPrac targets Paper/Minecraft 1.21.11 for future plugin work.
- Decision: Future setup should follow official PaperMC documentation.
- Rationale: Paper 1.21.11 keeps the initial architecture aligned with current plugin development practices while avoiding legacy compatibility complexity.

## 2026-04-30: Adopt Harness Engineering

- Decision: `AGENTS.md` is a short map, and `docs/` is the source of truth.
- Decision: Meaningful project decisions and behavior changes must update documentation.
- Rationale: Agent-first development works best when context is repository-local, concise, inspectable, and verifiable.

## 2026-04-30: Use ROADMAP.md as Execution Roadmap

- Decision: `ROADMAP.md` is the execution roadmap for phased implementation work.
- Decision: Phase exit criteria are the gate for advancing scope, and dependency additions should stay within the documented phase unless a later phase explicitly requires them.
- Decision: `docs/` remains the source of truth for behavior, setup, and workflow changes; the roadmap coordinates execution, not policy.
- Rationale: The repository needs a single working plan with clear gates so implementation stays staged, dependency growth stays intentional, and docs remain authoritative.

## 2026-04-30: Scaffold API-Only Paper Base

- Decision: Scaffold RevPrac as an API-only Paper plugin targeting Paper/Minecraft `1.21.11`.
- Decision: Use Gradle Wrapper `9.5.0`, Kotlin DSL, Java 21 toolchain, and `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.
- Decision: Use standard `plugin.yml`, not experimental `paper-plugin.yml`.
- Decision: Do not add `paperweight-userdev`, Shadow, NMS, databases, commands, or practice feature modules in the base scaffold.
- Decision: Verify the base with JUnit Jupiter, MockBukkit, Spotless, JaCoCo report generation, and a real Paper 1.21.11 smoke boot.
- Rationale: The project needs a small, verifiable runtime base before feature work, while keeping internals and packaging complexity out until a documented feature requires them.

## 2026-04-30: Phase 1 Bootstrap Config Boundary

- Decision: Keep the Phase 1 bootstrap path on standard `plugin.yml` plus a bundled `config.yml` resource.
- Decision: Keep the plugin constructor side-effect free and perform bootstrap/config wiring in lifecycle methods, with config save and validation in `onEnable()`.
- Decision: Treat `paper-plugin.yml` as experimental and out of scope for this slice.
- Rationale: PaperMC docs keep `plugin.yml` as the stable main-class entrypoint, recommend avoiding constructor work, and expose config through `JavaPlugin#getConfig()` after the resource has been saved.

## 2026-04-30: Phase 2 Player Session Safety Boundary

- Decision: Model player safety with plain Java `domain.players` session and snapshot contracts plus `application.players.PlayerSessionService`.
- Decision: Keep Paper/Bukkit player capture, restore, and join/quit events in `adapters.paper.players`.
- Decision: Capture the baseline snapshot once on first transition from `LOBBY` into a managed context and preserve it across managed-context transitions until restoration.
- Decision: Defer Paper join restoration to the next server tick so no teleport or restore path runs directly inside `PlayerJoinEvent`.
- Decision: Include inventory cursor state in Phase 2 snapshots and close open inventories before Paper restore reapplies baseline inventory.
- Decision: Schedule player-session initialization for players already online when the plugin enables.
- Decision: Use in-memory active session and pending restoration repositories for Phase 2; durable restart/crash recovery is deferred to Phase 6 persistence.
- Decision: Let `BootstrapRuntime.shutdown()` close intake and restore online managed players through `PlayerSessionService.shutdownAll()` before marking runtime shutdown complete.
- Rationale: Phase 2 needs interruption safety before arenas, kits, and matches exist, but adding durable storage before the persistence phase would broaden the dependency surface too early.
