# Phase 2 Player Session Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Phase 2 player-session safety slice: explicit player contexts, immutable snapshots, recoverable join/quit/disable flows, and tested boundaries.

**Architecture:** Keep all player state rules in plain Java under `domain.players` and `application.players`. Paper/Bukkit objects are captured and restored only in `adapters.paper.players`, with bootstrap wiring in `RevPracBootstrap` and shutdown orchestration in `BootstrapRuntime`.

**Tech Stack:** Java 21 records/enums, Gradle 9.5.0, JUnit Jupiter, MockBukkit, Paper API 1.21.11.

---

## External API Notes

- Paper lifecycle cleanup belongs in `JavaPlugin#onDisable`; keep constructor work empty.
- `PlayerJoinEvent` is safe for post-join logic, but Paper Javadocs say teleporting during that event has undefined behavior. Phase 2 restore should use synchronous `Player#teleport` only in the adapter restore path and avoid `teleportAsync().join()` on the main thread.
- Snapshot/restore may use `Player#getInventory()`, `PlayerInventory#getStorageContents()`, `getArmorContents()`, `getExtraContents()`, hand-specific APIs, `HumanEntity#getEnderChest()`, `HumanEntity#getGameMode()`, `Damageable#getHealth()`, food/saturation, and active potion effects.
- Avoid deprecated string join/quit message APIs and deprecated hand/max-health APIs.

## Phase Boundary Decisions

- Phase 2 uses in-memory session and pending-restoration repositories. Durable restart recovery is deferred to Phase 6 persistence to avoid introducing file/database storage early.
- A baseline snapshot is captured exactly once on the first transition from `LOBBY` to a managed context. Transitions between managed contexts preserve that baseline.
- `BootstrapRuntime.shutdown()` closes intake and restores online managed sessions before completing runtime shutdown.

## File Structure

- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerId.java`: UUID-backed player identity.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerContext.java`: `LOBBY`, `QUEUE`, `MATCH`, `SPECTATOR`, `EDITOR`.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/TransitionReason.java`: `JOIN`, `QUEUE_JOIN`, `MATCH_START`, `SPECTATE`, `EDITOR_OPEN`, `RETURN_TO_LOBBY`, `QUIT`, `PLUGIN_DISABLE`.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSafetySnapshot.java`: immutable snapshot root.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/LocationSnapshot.java`: world key, coordinates, yaw, pitch.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/InventorySnapshot.java`: storage, armor, extra, ender chest, selected slot.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerStatusSnapshot.java`: game mode, health, food, saturation, exp, level, flight flags, potion effects.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PotionEffectSnapshot.java`: effect key, duration, amplifier, ambient, particles, icon.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSession.java`: active session aggregate.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PendingRestoration.java`: pending restore ticket.
- Create `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSessionTransitionPolicy.java`: allowed transition rules.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerStatePort.java`: capture/restore/isOnline boundary.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerSessionRepository.java`: active session storage port.
- Create `src/main/java/io/github/xreatlabz/revprac/ports/players/PendingRestorationRepository.java`: pending restore storage port.
- Create `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerSessionService.java`: join, quit, context transition, return, disable recovery.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryPlayerSessionRepository.java`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryPendingRestorationRepository.java`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerStateAdapter.java`.
- Create `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerSessionListener.java`.
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`.
- Modify `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`.
- Modify `src/main/java/io/github/xreatlabz/revprac/application/result/ProblemCategory.java` only if the service needs a non-configuration category.
- Add tests under matching `src/test/java/...` packages.
- Update `ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/BUILDING.md`, `docs/DECISIONS.md`, and `docs/README.md`.

---

### Task 1: Domain Player Session Contracts

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerId.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerContext.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/TransitionReason.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/LocationSnapshot.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/InventorySnapshot.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerStatusSnapshot.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PotionEffectSnapshot.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSafetySnapshot.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSession.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PendingRestoration.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/domain/players/PlayerSessionTransitionPolicy.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/players/PlayerContextContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/players/PlayerSnapshotContractTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/domain/players/PlayerSessionTransitionPolicyTest.java`

- [ ] **Step 1: Write failing domain contract tests**

Write tests proving:
- context enum has exactly `LOBBY`, `QUEUE`, `MATCH`, `SPECTATOR`, `EDITOR`
- snapshot records are immutable and preserve null inventory slots
- domain sources contain no `org.bukkit` or `io.papermc.paper`
- transition policy allows `LOBBY -> QUEUE`, `QUEUE -> MATCH`, managed-to-managed transitions, and managed-to-`LOBBY`
- transition policy rejects undeclared or same-context transitions
- managed sessions require a return snapshot and lobby sessions do not

Run:

```bash
./gradlew test --tests '*PlayerContextContractTest' --tests '*PlayerSnapshotContractTest' --tests '*PlayerSessionTransitionPolicyTest'
```

Expected: tests fail because the domain classes do not exist.

- [ ] **Step 2: Implement minimal domain classes**

Use records and enums only. `InventorySnapshot` should store `List<String>` payloads for storage, armor, extra, and ender chest so the domain stays item-model-free. `PlayerStatusSnapshot` should store string keys for game mode and effect types.

- [ ] **Step 3: Re-run domain tests**

Run:

```bash
./gradlew test --tests '*PlayerContextContractTest' --tests '*PlayerSnapshotContractTest' --tests '*PlayerSessionTransitionPolicyTest'
```

Expected: domain tests pass.

---

### Task 2: Application Session Recovery Service

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerStatePort.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/players/PlayerSessionRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/ports/players/PendingRestorationRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/application/players/PlayerSessionService.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryPlayerSessionRepository.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryPendingRestorationRepository.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/application/players/PlayerSessionServiceTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryPlayerSessionRepositoryTest.java`

- [ ] **Step 1: Write failing service and repository tests**

Write tests proving:
- join without pending restoration creates a lobby session
- first managed transition captures one baseline snapshot
- managed-to-managed transition preserves the baseline snapshot
- return to lobby restores the baseline and clears managed state
- quit from lobby removes the active session without pending restore
- quit from managed context creates one pending restoration
- join with pending restoration restores once, deletes pending ticket, and opens a lobby session
- shutdown closes intake, restores online managed players, leaves offline pending tickets alone, and is idempotent
- service sources contain no Bukkit/Paper imports

Run:

```bash
./gradlew test --tests '*PlayerSessionServiceTest' --tests '*InMemoryPlayerSessionRepositoryTest'
```

Expected: tests fail because ports, repositories, and service do not exist.

- [ ] **Step 2: Implement ports, in-memory repositories, and service**

Keep repositories synchronized or backed by concurrent maps. `PlayerStatePort` is the only application-facing interface allowed to capture/restore player state.

- [ ] **Step 3: Re-run service tests**

Run:

```bash
./gradlew test --tests '*PlayerSessionServiceTest' --tests '*InMemoryPlayerSessionRepositoryTest'
```

Expected: service and repository tests pass.

---

### Task 3: Paper Adapter, Listener, and Bootstrap Wiring

**Files:**
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerStateAdapter.java`
- Create: `src/main/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerSessionListener.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/RevPracBootstrap.java`
- Modify: `src/main/java/io/github/xreatlabz/revprac/bootstrap/BootstrapRuntime.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerStateAdapterTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/adapters/paper/players/PaperPlayerSessionListenerTest.java`
- Test: `src/test/java/io/github/xreatlabz/revprac/RevPracPluginSessionSafetyTest.java`

- [ ] **Step 1: Write failing Paper adapter and lifecycle tests**

Write MockBukkit tests proving:
- adapter captures inventory storage, armor, extra/offhand, selected slot, location, game mode, health, food, saturation, exp, level, flight flags, and potion effect snapshots
- adapter restores a captured snapshot to the same player without dropping empty slots
- listener delegates join and quit to the service
- plugin enable registers the listener
- plugin disable calls runtime shutdown and restores tracked online managed players

Run:

```bash
./gradlew test --tests '*PaperPlayerStateAdapterTest' --tests '*PaperPlayerSessionListenerTest' --tests '*RevPracPluginSessionSafetyTest'
```

Expected: tests fail because Paper adapter/listener wiring does not exist.

- [ ] **Step 2: Implement adapter and listener**

Use the current plugin manager registration pattern in `RevPracBootstrap`. Keep all Bukkit/Paper imports inside `RevPracPlugin`, `bootstrap`, and `adapters.paper`.

- [ ] **Step 3: Wire runtime shutdown**

`BootstrapRuntime` should own the `PlayerSessionService` and call `shutdownAll()` once before logging shutdown. Preserve existing idempotence.

- [ ] **Step 4: Re-run adapter and lifecycle tests**

Run:

```bash
./gradlew test --tests '*PaperPlayerStateAdapterTest' --tests '*PaperPlayerSessionListenerTest' --tests '*RevPracPluginSessionSafetyTest'
```

Expected: adapter and lifecycle tests pass.

---

### Task 4: Documentation and Phase Gate Updates

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUILDING.md`
- Modify: `docs/DECISIONS.md`
- Modify: `docs/README.md`

- [ ] **Step 1: Update docs**

Record:
- Phase 2 status and exact scope implemented
- in-memory pending restoration decision and Phase 6 durable persistence deferral
- player-session package boundaries
- adapter-only Paper snapshot policy
- new focused verification commands

- [ ] **Step 2: Run documentation and import checks**

Run:

```bash
rg -n "Player Session Safety|Phase 2|PendingRestoration|PlayerSessionService" ROADMAP.md docs
rg -n "import (org\\.bukkit|io\\.papermc\\.paper)" src/main/java/io/github/xreatlabz/revprac/application src/main/java/io/github/xreatlabz/revprac/domain src/main/java/io/github/xreatlabz/revprac/ports
```

Expected: docs mention Phase 2 details; import check returns no application/domain/ports Bukkit/Paper imports.

---

### Task 5: Final Verification and PR Preparation

**Files:**
- No code changes unless verification exposes defects.

- [ ] **Step 1: Run full verification**

Run:

```bash
./gradlew test
./gradlew spotlessCheck test jacocoTestReport jar
./scripts/smoke-run-paper.sh
git diff --check
```

Expected: all commands pass.

- [ ] **Step 2: Run review passes**

Dispatch a spec reviewer and code reviewer over the full Phase 2 diff. Fix all correctness, boundary, or missing-test issues before pushing.

- [ ] **Step 3: Commit, push, and create PR**

Use atomic commits where practical and include verification evidence in the PR body.
