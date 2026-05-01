# Phase 3 Arena and Kit Registries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Phase 3 arena and kit registry slice: typed definitions, validation, deterministic arena reservations, reset hooks, kit serialization, and admin setup commands for operator-managed content.

**Architecture:** Keep arena and kit rules in plain Java under `domain.arenas`, `domain.kits`, `application.arenas`, and `application.kits`. Paper/Bukkit details stay in `adapters.paper`, including command parsing, player inventory capture, YAML registry files, and world/location conversion.

**Tech Stack:** Java 21 records/enums, Gradle 9.5.0, Paper API 1.21.11, JUnit Jupiter, MockBukkit, Bukkit `YamlConfiguration`, standard `plugin.yml` commands.

---

## External API Notes

- Paper `plugin.yml` remains the stable command metadata surface for this repo. Declare the root admin command there and wire a separate executor with `JavaPlugin#getCommand(...).setExecutor(...)`.
- Keep `paper-plugin.yml`, Brigadier command registration, NMS, and `paperweight-userdev` out of Phase 3. The current Paper docs do not require them for plugin commands, YAML resources, or item serialization.
- Continue saving bundled resources before reading config or registry data. For custom YAML files, use `YamlConfiguration.loadConfiguration(file)` in the Paper adapter boundary.
- Use `ItemStack.serializeAsBytes()` / `ItemStack.deserializeBytes(...)` for kit item payloads and Base64 strings at the domain boundary. Do not store Bukkit `Inventory` objects.
- World, location, player, inventory, and command sender APIs belong only in Paper adapters and tests that are explicitly adapter or plugin tests.

## Phase Boundary Decisions

- Phase 3 introduces YAML-backed operator registries in `arenas.yml` and `kits.yml`, loaded and saved by Paper adapters in the plugin data folder.
- Arena reset behavior is a hook contract in Phase 3. The runtime adapter may be a no-op/logging reset hook until later match teardown work has real block rollback data.
- Arena reservations are in memory and deterministic. Durable match/arena state is deferred until Phase 6 persistence.
- Setup commands are intentionally small: `/revprac arena create <id> <radius>` captures a simple arena around the executing player, and `/revprac kit save <id>` captures the executing player's current loadout.
- Commands must delegate typed inputs into application services and persistence adapters; domain/application code must not know about command senders or Paper types.

## File Structure

- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaId.java`: normalized string arena identity.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaCuboid.java`: world key and integer bounds.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaSpawnPoint.java`: world key, coordinates, yaw, and pitch.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaDefinition.java`: immutable arena definition and validation.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaReservationId.java`: UUID reservation token.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaReservation.java`: reservation token, arena id, and owner key.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitId.java`: normalized string kit identity.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitInventory.java`: serialized item payloads and selected slot.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitPotionEffect.java`: serialized potion effect metadata.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitRules.java`: practice behavior flags.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitDefinition.java`: immutable kit definition and validation.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/arenas/ArenaRegistryRepository.java`: arena definition storage port.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/arenas/ArenaResetPort.java`: reset hook boundary.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/kits/KitRegistryRepository.java`: kit definition storage port.
- Create `src/main/java/io/github/xreatlabz/revprac/application/arenas/ArenaRegistryService.java`: arena registration, listing, reservation, release, and reset orchestration.
- Create `src/main/java/io/github/xreatlabz/revprac/application/kits/KitRegistryService.java`: kit registration and deterministic listing.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryArenaRegistryRepository.java`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryKitRegistryRepository.java`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/arenas/PaperArenaRegistryFiles.java`: load/save `arenas.yml`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/arenas/PaperArenaResetAdapter.java`: Phase 3 reset hook adapter.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitLoadoutAdapter.java`: capture/apply kits to players.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitRegistryFiles.java`: load/save `kits.yml`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracAdminCommand.java`: root admin setup command.
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`: load registries and register command executor.
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`: own arena/kit services for runtime lifetime.
- Modify `src/main/resources/plugin.yml`: declare `/revprac` and `revprac.admin`.
- Modify `src/main/resources/config.yml` only if a Phase 3 setting is needed.
- Add tests under matching `src/test/java/...` packages.
- Update `ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, `docs/DECISIONS.md`, and `docs/README.md`.

---

### Task 1: Domain Arena and Kit Contracts

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaId.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaCuboid.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaSpawnPoint.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaDefinition.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaReservationId.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/arenas/ArenaReservation.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitId.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitInventory.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitPotionEffect.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitRules.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/kits/KitDefinition.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/arenas/ArenaDefinitionContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/kits/KitDefinitionContractTest.java`

- [ ] **Step 1: Write failing arena contract tests**

Write tests proving:
- `ArenaId` normalizes ids to lowercase and rejects blank ids, spaces, and punctuation outside `[a-z0-9_-]`
- `ArenaCuboid` rejects inverted bounds and blank world keys
- `ArenaDefinition` rejects blank display names, mismatched spawn worlds, and spawns outside bounds
- arena records are immutable value objects
- `domain.arenas` has no `org.bukkit` or `io.papermc.paper` imports

Run:

```bash
./gradlew test --tests '*ArenaDefinitionContractTest'
```

Expected: tests fail because arena domain classes do not exist.

- [ ] **Step 2: Write failing kit contract tests**

Write tests proving:
- `KitId` normalizes ids to lowercase and rejects blank ids, spaces, and punctuation outside `[a-z0-9_-]`
- `KitInventory` preserves null item slots, defensively copies lists, and rejects selected slots outside `0..8`
- `KitPotionEffect` rejects blank effect keys, negative durations, and negative amplifiers
- `KitDefinition` rejects blank display names and defensively copies effects
- `domain.kits` has no `org.bukkit` or `io.papermc.paper` imports

Run:

```bash
./gradlew test --tests '*KitDefinitionContractTest'
```

Expected: tests fail because kit domain classes do not exist.

- [ ] **Step 3: Implement minimal domain contracts**

Use Java records and compact constructors. Preserve null item slots with an unmodifiable `ArrayList` copy like the existing `InventorySnapshot` pattern.

Implementation details:
- normalize id strings with `trim().toLowerCase(Locale.ROOT)`
- use one shared id regex per id class: `[a-z0-9][a-z0-9_-]{0,62}`
- validate all coordinates with `Double.isFinite(...)`
- require arena spawns to share `ArenaCuboid.worldKey()`
- check spawn containment by flooring spawn coordinates to block coordinates

- [ ] **Step 4: Re-run domain tests**

Run:

```bash
./gradlew test --tests '*ArenaDefinitionContractTest' --tests '*KitDefinitionContractTest'
```

Expected: domain tests pass.

---

### Task 2: Application Registries and Reset Hook

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/arenas/ArenaRegistryRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/arenas/ArenaResetPort.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/kits/KitRegistryRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/arenas/ArenaRegistryService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/kits/KitRegistryService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryArenaRegistryRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryKitRegistryRepository.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/arenas/ArenaRegistryServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/kits/KitRegistryServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryArenaRegistryRepositoryTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryKitRegistryRepositoryTest.java`

- [ ] **Step 1: Write failing arena service tests**

Write tests proving:
- valid arena definitions can be registered and listed in deterministic id order
- duplicate arena ids are rejected
- disabled arenas cannot be reserved
- reserving an available arena returns an `ArenaReservation`
- releasing a reservation frees the arena and calls `ArenaResetPort.reset(...)` exactly once
- releasing an unknown reservation id is rejected without calling reset
- two threads racing to reserve the same arena produce exactly one success and one deterministic failure
- `application.arenas` and `ports.arenas` have no Bukkit/Paper imports

Run:

```bash
./gradlew test --tests '*ArenaRegistryServiceTest'
```

Expected: tests fail because application arena classes do not exist.

- [ ] **Step 2: Write failing kit service and repository tests**

Write tests proving:
- valid kit definitions can be registered and listed in deterministic id order
- duplicate kit ids are rejected
- disabled kits are kept in the registry but excluded from an `enabledKits()` query
- repositories defensively copy map/list views through immutable return values
- `application.kits` and `ports.kits` have no Bukkit/Paper imports

Run:

```bash
./gradlew test --tests '*KitRegistryServiceTest' --tests '*InMemoryKitRegistryRepositoryTest'
```

Expected: tests fail because application kit classes do not exist.

- [ ] **Step 3: Implement ports, services, and in-memory repositories**

Implementation details:
- Use `ReentrantLock` in `ArenaRegistryService` and `KitRegistryService` for mutation serialization.
- Store active reservations in `ArenaRegistryService`, keyed by `ArenaReservationId`.
- Generate reservation ids with `UUID.randomUUID()` inside the service.
- Return deterministic lists sorted by normalized id string.
- Use `IllegalArgumentException` for invalid or duplicate definitions and `IllegalStateException` for unavailable reservation/release state.
- In-memory repositories should use `ConcurrentHashMap` and return immutable snapshots.

- [ ] **Step 4: Re-run application and storage tests**

Run:

```bash
./gradlew test --tests '*ArenaRegistryServiceTest' --tests '*KitRegistryServiceTest' --tests '*InMemoryArenaRegistryRepositoryTest' --tests '*InMemoryKitRegistryRepositoryTest'
```

Expected: application and storage tests pass.

---

### Task 3: Paper YAML Registries and Kit Loadout Adapter

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/arenas/PaperArenaRegistryFiles.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/arenas/PaperArenaResetAdapter.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitLoadoutAdapter.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitRegistryFiles.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/arenas/PaperArenaRegistryFilesTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitLoadoutAdapterTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/kits/PaperKitRegistryFilesTest.java`

- [ ] **Step 1: Write failing YAML registry tests**

Write temp-dir tests proving:
- missing registry files load as empty lists and can be saved
- valid `arenas.yml` loads into `ArenaDefinition` records
- valid `kits.yml` loads into `KitDefinition` records
- save then load round-trips definitions without reordering ids
- duplicate ids fail closed
- missing required fields include the YAML path in the exception message
- invalid YAML content does not publish partial definitions

Run:

```bash
./gradlew test --tests '*PaperArenaRegistryFilesTest' --tests '*PaperKitRegistryFilesTest'
```

Expected: tests fail because registry file adapters do not exist.

- [ ] **Step 2: Write failing kit loadout adapter tests**

Write MockBukkit tests proving:
- player storage, armor, offhand/extra contents, selected slot, and potion effects capture into a `KitDefinition`
- applying a captured kit restores those items/effects to a player
- null item slots survive capture -> save -> load -> apply
- unsupported or malformed Base64 item payloads fail with a clear `IllegalArgumentException`

Run:

```bash
./gradlew test --tests '*PaperKitLoadoutAdapterTest'
```

Expected: tests fail because the kit loadout adapter does not exist.

- [ ] **Step 3: Implement Paper registry files and kit adapter**

Implementation details:
- Keep `YamlConfiguration`, `ItemStack`, `PotionEffect`, `NamespacedKey`, and `Registry` imports in `adapters.paper`.
- Persist arena registry under top-level `arenas.<id>`.
- Persist kit registry under top-level `kits.<id>`.
- Store serialized items as Base64 strings or `null`; do not store Bukkit objects in domain records.
- Use `ItemStack.serializeAsBytes()` and `ItemStack.deserializeBytes(...)`.
- `PaperArenaResetAdapter` should implement `ArenaResetPort` and log reset requests without block rollback in Phase 3.

- [ ] **Step 4: Re-run adapter tests**

Run:

```bash
./gradlew test --tests '*PaperArenaRegistryFilesTest' --tests '*PaperKitLoadoutAdapterTest' --tests '*PaperKitRegistryFilesTest'
```

Expected: Paper registry and loadout adapter tests pass.

---

### Task 4: Admin Command and Bootstrap Wiring

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracAdminCommand.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Modify: `src/main/resources/plugin.yml`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/commands/RevPracAdminCommandTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginPhase3Test.java`

- [ ] **Step 1: Write failing command tests**

Write MockBukkit tests proving:
- `/revprac` is declared in plugin metadata
- command execution requires `revprac.admin`
- `/revprac arena create <id> <radius>` requires a player sender, captures the sender world/location, saves an enabled arena, and persists `arenas.yml`
- `/revprac kit save <id>` requires a player sender, captures the sender loadout, saves an enabled kit, and persists `kits.yml`
- invalid argument counts return usage without mutating registries
- command code delegates typed domain objects into application services and keeps domain errors as operator-facing messages

Run:

```bash
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*RevPracPluginPhase3Test'
```

Expected: tests fail because command and wiring do not exist.

- [ ] **Step 2: Wire runtime registries**

Implementation details:
- In `RevPracBootstrap.enable(...)`, create in-memory arena/kit repositories, load YAML definitions, register them into `ArenaRegistryService` and `KitRegistryService`, then build `BootstrapRuntime`.
- Startup should fail through the existing `handleStartupFailure(...)` path when registry files are invalid.
- `BootstrapRuntime` should own `ArenaRegistryService`, `KitRegistryService`, `PaperArenaRegistryFiles`, and `PaperKitRegistryFiles` so commands can use the same runtime state.
- Preserve the current player-session shutdown behavior exactly.

- [ ] **Step 3: Implement command executor**

Command behavior:
- `revprac.admin` is the permission node.
- `/revprac arena create <id> <radius>`:
  - sender must be a `Player`
  - radius must be an integer from `1` to `256`
  - bounds are centered on the player's current block with y-range `location.blockY - 8` through `location.blockY + 8`
  - both spawns initially use the player's current location
  - command saves the definition in memory and to `arenas.yml`
- `/revprac kit save <id>`:
  - sender must be a `Player`
  - capture current inventory/effects with `PaperKitLoadoutAdapter`
  - command saves the definition in memory and to `kits.yml`
- Send concise success or error messages with no stack traces to users.

- [ ] **Step 4: Re-run command and plugin tests**

Run:

```bash
./gradlew test --tests '*RevPracAdminCommandTest' --tests '*RevPracPluginPhase3Test'
```

Expected: command and bootstrap tests pass.

---

### Task 5: Documentation, Final Verification, and PR

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUILDING.md`
- Modify: `docs/DECISIONS.md`
- Modify: `docs/README.md`

- [ ] **Step 1: Update docs**

Record:
- Phase 3 status and implemented scope
- arena/kit domain and application package boundaries
- YAML registry files and setup command behavior
- Phase 3 reset hook limitation
- focused verification commands
- decision to keep API-only Paper, `plugin.yml`, and in-memory reservation state

- [ ] **Step 2: Run focused boundary checks**

Run:

```bash
rg -n "Arena and Kit Registries|Phase 3|arenas.yml|kits.yml|revprac.admin" ROADMAP.md docs src/main/resources/plugin.yml
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

Expected: docs and plugin metadata mention Phase 3; import check returns no matches.

- [ ] **Step 3: Run full verification**

Run:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
git diff --check
```

Expected: all commands pass.

- [ ] **Step 4: Run review passes**

Dispatch a spec reviewer and code reviewer over the full Phase 3 diff. Fix all correctness, boundary, missing-test, and documentation issues before pushing.

- [ ] **Step 5: Push and create PR**

Use atomic commits where practical and include verification evidence in the PR body.
