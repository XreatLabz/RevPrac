package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MatchLifecycleServiceTest {

    @Test
    void countdownTicksDeterministicallyToActive() {
        Harness harness = new Harness(new MatchRuleset(3, 8, true));
        Match created = harness.startAcceptedDuel();

        assertEquals("COUNTDOWN", created.state().name());

        harness.matchLifecycleService.tick();
        Match afterOneTick = harness.matchRepository.find(created.id()).orElseThrow();
        assertEquals(2, afterOneTick.countdownTicksRemaining());
        assertEquals("COUNTDOWN", afterOneTick.state().name());

        harness.matchLifecycleService.tick();
        harness.matchLifecycleService.tick();
        Match active = harness.matchRepository.find(created.id()).orElseThrow();

        assertEquals("ACTIVE", active.state().name());
        assertTrue(
                harness.events.stream().anyMatch(MatchEvent.MatchStarted.class::isInstance),
                "countdown completion should emit a match-start event");
    }

    @Test
    void deathForfeitQuitTimeoutAndShutdownUseTheSameCompletionAndTeardownPath() {
        Map<MatchEndReason, Harness> harnesses = new EnumMap<>(MatchEndReason.class);
        harnesses.put(MatchEndReason.WIN, new Harness(new MatchRuleset(1, 8, true)));
        harnesses.put(MatchEndReason.FORFEIT, new Harness(new MatchRuleset(1, 8, true)));
        harnesses.put(MatchEndReason.TIMEOUT, new Harness(new MatchRuleset(1, 1, true)));
        harnesses.put(MatchEndReason.SHUTDOWN, new Harness(new MatchRuleset(1, 8, true)));

        for (Harness harness : harnesses.values()) {
            harness.startAcceptedDuel();
            harness.matchLifecycleService.tick();
            harness.join(harness.spectator());
            harness.matchLifecycleService.spectate(harness.spectator(), harness.requester());
        }

        harnesses.get(MatchEndReason.WIN).matchLifecycleService.completeByDeath(harnesses.get(MatchEndReason.WIN).target());
        harnesses.get(MatchEndReason.FORFEIT).matchLifecycleService.handleQuit(harnesses.get(MatchEndReason.FORFEIT).target());
        harnesses.get(MatchEndReason.TIMEOUT).matchLifecycleService.tick();
        harnesses.get(MatchEndReason.SHUTDOWN).matchLifecycleService.shutdownAll();

        assertTerminalOutcome(harnesses.get(MatchEndReason.WIN), MatchEndReason.WIN);
        assertTerminalOutcome(harnesses.get(MatchEndReason.FORFEIT), MatchEndReason.FORFEIT);
        assertTerminalOutcome(harnesses.get(MatchEndReason.TIMEOUT), MatchEndReason.TIMEOUT);
        assertTerminalOutcome(harnesses.get(MatchEndReason.SHUTDOWN), MatchEndReason.SHUTDOWN);

        Harness explicitForfeitHarness = new Harness(new MatchRuleset(1, 8, true));
        explicitForfeitHarness.startAcceptedDuel();
        explicitForfeitHarness.matchLifecycleService.tick();
        explicitForfeitHarness.join(explicitForfeitHarness.spectator());
        explicitForfeitHarness.matchLifecycleService.spectate(
                explicitForfeitHarness.spectator(), explicitForfeitHarness.requester());
        explicitForfeitHarness.matchLifecycleService.forfeit(explicitForfeitHarness.target());
        assertTerminalOutcome(explicitForfeitHarness, MatchEndReason.FORFEIT);
    }

    @Test
    void teardownFailureKeepsCompletedMatchForRetry() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.matchPlayerPort.failClearFor(harness.requester());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.completeByDeath(harness.target()));
        assertEquals("clear failed for requester", failure.getMessage());

        Match completed = harness.matchRepository.find(match.id()).orElseThrow();
        assertEquals("COMPLETED", completed.state().name());
        assertEquals(MatchOutcome.win(harness.requester(), harness.target()), completed.outcome().orElseThrow());
        assertEquals(0, harness.arenaResetPort.resetCalls.size(), "Failed teardown must not release the arena yet");

        harness.matchPlayerPort.clearFailures.clear();
        harness.matchLifecycleService.tearDown(match.id());

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "Successful retry should drain the completed match");
        assertEquals(1, harness.arenaResetPort.resetCalls.size(), "Retry should release the arena exactly once");
    }

    @Test
    void teardownFailureDoesNotReturnAnyPlayersToLobbyBeforeCleanupSucceeds() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchLifecycleService.spectate(harness.spectator(), harness.requester());
        harness.matchPlayerPort.failClearFor(harness.requester(), "clear failed for requester");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.completeByDeath(harness.target()));

        assertEquals("clear failed for requester", failure.getMessage());
        assertEquals(match.id(), harness.matchRepository.find(match.id()).orElseThrow().id());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(harness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(harness.target()).orElseThrow().context());
        assertEquals(PlayerContext.SPECTATOR, harness.playerSessions.find(harness.spectator()).orElseThrow().context());
        assertEquals(List.of(), harness.playerStatePort.restoredPlayers, "cleanup failure must stop before lobby returns");
    }

    @Test
    void teardownRetryAfterLateLobbyFailureCompletesWithIdempotentCleanup() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchLifecycleService.spectate(harness.spectator(), harness.requester());
        harness.playerStatePort.failRestoreOnceFor(harness.target(), "return failed once for target");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.completeByDeath(harness.target()));

        assertEquals("return failed once for target", failure.getMessage());
        Match retained = harness.matchRepository.find(match.id()).orElseThrow();
        assertEquals("COMPLETED", retained.state().name());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(harness.target()).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.spectator()).orElseThrow().context());
        assertEquals(
                Set.of(harness.requester(), harness.target(), harness.spectator()),
                Set.copyOf(harness.matchPlayerPort.clearedPlayers),
                "adapter cleanup should have run once before the late restore failure");
        assertTrue(
                harness.matchPlayerPort.matchStateClearedPlayers.isEmpty(),
                "late restore failures must not leave fake adapter match state behind");

        harness.matchLifecycleService.tearDown(match.id());

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "Successful retry should drain the retained match");
        assertEquals(1, harness.arenaResetPort.resetCalls.size(), "Retry should release the arena exactly once");
        assertEquals(
                Set.of(harness.requester(), harness.target(), harness.spectator()),
                harness.matchPlayerPort.redundantClearPlayers,
                "retry should safely replay adapter cleanup for already-cleared players");
        assertTrue(
                harness.matchPlayerPort.matchStateClearedPlayers.isEmpty(),
                "idempotent cleanup should leave no fake adapter match state behind");
    }

    @Test
    void startRollbackSurfacesCleanupFailuresAsSuppressedExceptions() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        harness.matchPlayerPort.failPrepareFor(harness.target(), "prepare failed for target");
        harness.matchPlayerPort.failClearFor(harness.requester(), "clear failed for requester");
        harness.playerStatePort.failRestoreFor(harness.requester(), "return failed for requester");

        IllegalStateException failure = assertThrows(IllegalStateException.class, harness::startAcceptedDuel);

        assertEquals("prepare failed for target", failure.getMessage());
        assertEquals(
                List.of("clear failed for requester", "return failed for requester"),
                List.of(failure.getSuppressed()[0].getMessage(), failure.getSuppressed()[1].getMessage()));
        assertTrue(harness.matchRepository.findAll().isEmpty(), "failed start must not retain a match");
        assertEquals(1, harness.arenaResetPort.resetCalls.size(), "failed start should release the arena reservation");
        assertEquals(
                PlayerContext.MATCH,
                harness.playerSessions.find(harness.requester()).orElseThrow().context(),
                "failed rollback should keep the managed session for operator retry");
        assertEquals(
                PlayerContext.LOBBY,
                harness.playerSessions.find(harness.target()).orElseThrow().context(),
                "successful rollback should return unaffected participants to lobby");
    }

    @Test
    void shutdownRetriesTransientTeardownFailureBeforeSurfacingAnError() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchLifecycleService.spectate(harness.spectator(), harness.requester());
        harness.matchPlayerPort.failClearOnceFor(harness.requester(), "clear failed once for requester");

        harness.matchLifecycleService.shutdownAll();

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "Successful retry should drain the retained match");
        assertEquals(1, harness.arenaResetPort.resetCalls.size(), "Retry success should still release the arena once");
        assertEquals(1,
                harness.events.stream().filter(MatchEvent.MatchCompleted.class::isInstance).count(),
                "shutdown should emit one completion event");
        assertEquals(1,
                harness.events.stream().filter(MatchEvent.MatchTornDown.class::isInstance).count(),
                "shutdown should emit one teardown event after retry succeeds");
    }

    @Test
    void spectatorsCanJoinOnlyActiveEnabledMatchesAndCannotBeParticipants() {
        Harness enabledHarness = new Harness(new MatchRuleset(2, 8, true));
        Match countdown = enabledHarness.startAcceptedDuel();
        enabledHarness.join(enabledHarness.spectator());

        IllegalStateException countdownFailure = assertThrows(
                IllegalStateException.class,
                () -> enabledHarness.matchLifecycleService.spectate(enabledHarness.spectator(), enabledHarness.requester()));
        assertEquals("only active matches can accept new spectators", countdownFailure.getMessage());

        enabledHarness.matchLifecycleService.tick();
        enabledHarness.matchLifecycleService.tick();
        enabledHarness.matchLifecycleService.spectate(enabledHarness.spectator(), enabledHarness.requester());

        Match withSpectator = enabledHarness.matchRepository.find(countdown.id()).orElseThrow();
        assertEquals(Set.of(enabledHarness.spectator()), withSpectator.spectators());
        assertEquals(PlayerContext.SPECTATOR, enabledHarness.playerSessions.find(enabledHarness.spectator()).orElseThrow().context());

        Harness participantHarness = new Harness(new MatchRuleset(1, 8, true));
        participantHarness.startAcceptedDuel();
        participantHarness.matchLifecycleService.tick();

        IllegalArgumentException participantFailure = assertThrows(
                IllegalArgumentException.class,
                () -> participantHarness.matchLifecycleService.spectate(
                        participantHarness.requester(), participantHarness.target()));
        assertEquals("participants cannot become spectators in their own match", participantFailure.getMessage());

        Harness disabledHarness = new Harness(new MatchRuleset(1, 8, false));
        disabledHarness.startAcceptedDuel();
        disabledHarness.matchLifecycleService.tick();
        disabledHarness.join(disabledHarness.spectator());

        IllegalStateException disabledFailure = assertThrows(
                IllegalStateException.class,
                () -> disabledHarness.matchLifecycleService.spectate(disabledHarness.spectator(), disabledHarness.requester()));
        assertEquals("spectators are disabled for this match", disabledFailure.getMessage());
    }

    @Test
    void spectatorQuitKeepsRepositoryStateUntilCleanupSucceeds() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchLifecycleService.spectate(harness.spectator(), harness.requester());
        harness.matchPlayerPort.failClearFor(harness.spectator(), "clear failed for spectator");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.handleQuit(harness.spectator()));
        assertEquals("clear failed for spectator", failure.getMessage());
        assertEquals(
                match.id(),
                harness.matchRepository.findBySpectator(harness.spectator()).orElseThrow().id(),
                "spectator should remain tracked after cleanup failure");
        assertEquals(
                PlayerContext.SPECTATOR,
                harness.playerSessions.find(harness.spectator()).orElseThrow().context(),
                "failed cleanup should retain spectator session for retry");
        assertEquals(0, harness.events.stream().filter(MatchEvent.MatchSpectatorLeft.class::isInstance).count());

        harness.matchPlayerPort.clearFailures.clear();
        harness.matchLifecycleService.handleQuit(harness.spectator());

        assertTrue(harness.matchRepository.findBySpectator(harness.spectator()).isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.spectator()).orElseThrow().context());
        assertEquals(1, harness.events.stream().filter(MatchEvent.MatchSpectatorLeft.class::isInstance).count());
    }

    @Test
    void spectatorJoinRollbackClearsPartialAdapterStateBeforeLobbyReturn() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchPlayerPort.failPrepareSpectatorFor(harness.spectator(), "prepare spectator failed");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.spectate(harness.spectator(), harness.requester()));

        assertEquals("prepare spectator failed", failure.getMessage());
        assertEquals(List.of(harness.spectator()), harness.matchPlayerPort.clearAttempts);
        assertEquals(List.of(harness.spectator()), harness.playerStatePort.restoredPlayers);
        assertTrue(harness.matchRepository.findBySpectator(harness.spectator()).isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.spectator()).orElseThrow().context());
    }

    @Test
    void spectatorJoinRollbackSuppressesCleanupFailures() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true));
        harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        harness.join(harness.spectator());
        harness.matchPlayerPort.failPrepareSpectatorFor(harness.spectator(), "prepare spectator failed");
        harness.matchPlayerPort.failClearFor(harness.spectator(), "clear spectator rollback failed");
        harness.playerStatePort.failRestoreFor(harness.spectator(), "return failed for spectator");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchLifecycleService.spectate(harness.spectator(), harness.requester()));

        assertEquals("prepare spectator failed", failure.getMessage());
        assertEquals(
                List.of("clear spectator rollback failed", "return failed for spectator"),
                List.of(failure.getSuppressed()[0].getMessage(), failure.getSuppressed()[1].getMessage()));
        assertEquals(List.of(harness.spectator()), harness.matchPlayerPort.clearAttempts);
        assertEquals(List.of(harness.spectator()), harness.playerStatePort.restoredPlayers);
    }

    @Test
    void matchLifecycleIgnoresEventSinkFailuresAcrossStartTickAndTeardown() {
        Harness harness = new Harness(new MatchRuleset(1, 8, true), event -> {
            throw new IllegalStateException("listener failed");
        });

        Match match = harness.startAcceptedDuel();
        harness.matchLifecycleService.tick();
        Match active = harness.matchRepository.find(match.id()).orElseThrow();
        assertEquals("ACTIVE", active.state().name());
        harness.matchLifecycleService.completeByDeath(harness.target());

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "event sink failures must not block teardown");
        assertEquals(1, harness.arenaResetPort.resetCalls.size());
    }

    @Test
    void queuedMatchesRejectTheSamePlayerAndChooseTheFirstEnabledAvailableArenaById() {
        Harness samePlayerHarness = new Harness(new MatchRuleset(2, 8, true));
        samePlayerHarness.join(samePlayerHarness.spectator());
        samePlayerHarness.playerSessionService.transitionTo(
                samePlayerHarness.spectator(), PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);

        IllegalArgumentException samePlayer = assertThrows(
                IllegalArgumentException.class,
                () -> samePlayerHarness.matchLifecycleService.startQueuedMatch(
                        samePlayerHarness.spectator(), samePlayerHarness.spectator(), samePlayerHarness.kitId()));
        assertEquals("queued match requires distinct players", samePlayer.getMessage());

        Harness selectionHarness = new Harness(new MatchRuleset(2, 8, true));
        selectionHarness.registerArena("arena-a");
        selectionHarness.registerArena("arena-z");
        selectionHarness.queueParticipants();
        selectionHarness.reserveArena("arena-a", "busy");

        Match match = selectionHarness.matchLifecycleService.startQueuedMatch(
                selectionHarness.requester(), selectionHarness.target(), selectionHarness.kitId());

        assertEquals(new ArenaId("arena-one"), match.arenaId());
        assertEquals(PlayerContext.MATCH, selectionHarness.playerSessions.find(selectionHarness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.MATCH, selectionHarness.playerSessions.find(selectionHarness.target()).orElseThrow().context());
    }

    @Test
    void queuedMatchKitValidationFailuresDoNotReserveArenaOrBlockLaterValidStart() {
        Harness missingKitHarness = new Harness(new MatchRuleset(2, 8, true));
        missingKitHarness.queueParticipants();
        assertQueuedKitFailureDoesNotReserveArena(missingKitHarness, new KitId("missing-kit"));

        Harness disabledKitHarness = new Harness(new MatchRuleset(2, 8, true));
        KitId disabledKitId = new KitId("disabled-kit");
        disabledKitHarness.registerKit(disabledKitId, false);
        disabledKitHarness.queueParticipants();
        assertQueuedKitFailureDoesNotReserveArena(disabledKitHarness, disabledKitId);
    }

    @Test
    void queuedMatchArenaUnavailableFailsBeforeSessionTransitionAndLateFailuresReuseRollbackCleanup() {
        Harness unavailableHarness = new Harness(new MatchRuleset(2, 8, true));
        unavailableHarness.registerArena("arena-a");
        unavailableHarness.queueParticipants();
        unavailableHarness.reserveAllArenas();

        IllegalStateException unavailable = assertThrows(
                IllegalStateException.class,
                () -> unavailableHarness.matchLifecycleService.startQueuedMatch(
                        unavailableHarness.requester(), unavailableHarness.target(), unavailableHarness.kitId()));
        assertInstanceOf(MatchLifecycleService.ArenaUnavailableException.class, unavailable);
        assertEquals(PlayerContext.QUEUE, unavailableHarness.playerSessions.find(unavailableHarness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.QUEUE, unavailableHarness.playerSessions.find(unavailableHarness.target()).orElseThrow().context());
        assertTrue(unavailableHarness.matchRepository.findAll().isEmpty());

        Harness rollbackHarness = new Harness(new MatchRuleset(2, 8, true));
        rollbackHarness.queueParticipants();
        rollbackHarness.matchPlayerPort.failPrepareFor(rollbackHarness.target(), "prepare failed for queued target");

        IllegalStateException rollback = assertThrows(
                IllegalStateException.class,
                () -> rollbackHarness.matchLifecycleService.startQueuedMatch(
                        rollbackHarness.requester(), rollbackHarness.target(), rollbackHarness.kitId()));

        assertEquals("prepare failed for queued target", rollback.getMessage());
        assertTrue(rollbackHarness.matchRepository.findAll().isEmpty());
        assertEquals(PlayerContext.LOBBY, rollbackHarness.playerSessions.find(rollbackHarness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, rollbackHarness.playerSessions.find(rollbackHarness.target()).orElseThrow().context());
        assertEquals(1, rollbackHarness.arenaResetPort.resetCalls.size());
    }

    private static void assertQueuedKitFailureDoesNotReserveArena(Harness harness, KitId rejectedKitId) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.matchLifecycleService.startQueuedMatch(
                        harness.requester(), harness.target(), rejectedKitId));

        assertEquals("unknown kit: " + rejectedKitId.value(), failure.getMessage());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(harness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(harness.target()).orElseThrow().context());
        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertEquals(0, harness.arenaResetPort.resetCalls.size(), "kit validation should fail before arena reservation");

        Match laterMatch = harness.matchLifecycleService.startQueuedMatch(
                harness.requester(), harness.target(), harness.kitId());

        assertEquals(new ArenaId("arena-one"), laterMatch.arenaId());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(harness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(harness.target()).orElseThrow().context());
    }

    private static void assertTerminalOutcome(Harness harness, MatchEndReason reason) {
        assertTrue(harness.matchRepository.findAll().isEmpty(), "Successful teardown should delete the match");
        assertEquals(List.of(harness.requester(), harness.target(), harness.spectator()), harness.matchPlayerPort.clearedPlayers);
        assertEquals(1, harness.arenaResetPort.resetCalls.size(), "Terminal path should release the arena once");
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.requester()).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.target()).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(harness.spectator()).orElseThrow().context());

        MatchEvent.MatchCompleted completed = harness.events.stream()
                .filter(MatchEvent.MatchCompleted.class::isInstance)
                .map(MatchEvent.MatchCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(reason, completed.outcome().reason());

        MatchEvent.MatchTornDown tornDown = harness.events.stream()
                .filter(MatchEvent.MatchTornDown.class::isInstance)
                .map(MatchEvent.MatchTornDown.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(reason, tornDown.reason());
    }

    private static final class Harness {
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final FakeArenaResetPort arenaResetPort = new FakeArenaResetPort();
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), arenaResetPort);
        private final KitRegistryService kitRegistryService =
                new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final FakePlayerStatePort playerStatePort = new FakePlayerStatePort();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(playerSessions, new InMemoryPendingRestorationRepository(), playerStatePort);
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final List<MatchEvent> events = new ArrayList<>();
        private final MatchLifecycleService matchLifecycleService;
        private final MatchRuleset ruleset;

        private Harness(MatchRuleset ruleset) {
            this(ruleset, events -> {
            });
        }

        private Harness(MatchRuleset ruleset, java.util.function.Consumer<MatchEvent> eventSink) {
            this.ruleset = ruleset;
            this.matchLifecycleService = new MatchLifecycleService(
                    matchRepository,
                    playerSessionService,
                    arenaRegistryService,
                    kitRegistryService,
                    matchPlayerPort,
                    ruleset,
                    event -> {
                        events.add(event);
                        eventSink.accept(event);
                    });
            arenaRegistryService.register(arenaDefinition());
            kitRegistryService.register(kitDefinition());
            matchPlayerPort.onlinePlayers.addAll(Set.of(requester(), target(), spectator()));
            playerStatePort.onlinePlayers.addAll(Set.of(requester(), target(), spectator()));
            join(requester());
            join(target());
        }

        private PlayerId requester() {
            return player("requester");
        }

        private PlayerId target() {
            return player("target");
        }

        private PlayerId spectator() {
            return player("spectator");
        }

        private ArenaId arenaId() {
            return new ArenaId("arena-one");
        }

        private KitId kitId() {
            return new KitId("kit-one");
        }

        private void join(PlayerId playerId) {
            playerSessionService.join(playerId);
        }

        private void queueParticipants() {
            playerSessionService.transitionTo(requester(), PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
            playerSessionService.transitionTo(target(), PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
        }

        private Match startAcceptedDuel() {
            DuelRequest accepted = new DuelRequest(
                    new DuelRequestId(UUID.nameUUIDFromBytes("request".getBytes())),
                    requester(),
                    target(),
                    arenaId(),
                    kitId(),
                    DuelRequestState.ACCEPTED,
                    Instant.parse("2026-05-01T12:00:00Z"),
                    Instant.parse("2026-05-01T12:00:30Z"));
            return matchLifecycleService.startAcceptedDuel(accepted);
        }

        private ArenaDefinition arenaDefinition() {
            return new ArenaDefinition(
                    arenaId(),
                    "Arena One",
                    new ArenaCuboid("minecraft:world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true);
        }

        private KitDefinition kitDefinition() {
            return kitDefinition(kitId(), "Kit One", true);
        }

        private KitDefinition kitDefinition(KitId kitId, String displayName, boolean enabled) {
            return new KitDefinition(
                    kitId,
                    displayName,
                    new KitInventory(List.of("sword"), List.of("helmet", "chest", "legs", "boots"), List.of("rod"), 0),
                    List.of(),
                    new KitRules(false, false, false, false),
                    enabled);
        }

        private void registerArena(String arenaId) {
            arenaRegistryService.register(new ArenaDefinition(
                    new ArenaId(arenaId),
                    arenaId,
                    new ArenaCuboid("minecraft:world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true));
        }

        private void reserveArena(String arenaId, String ownerKey) {
            arenaRegistryService.reserve(new ArenaId(arenaId), ownerKey);
        }

        private void reserveAllArenas() {
            for (ArenaDefinition arena : arenaRegistryService.arenas()) {
                arenaRegistryService.reserve(arena.id(), "busy:" + arena.id().value());
            }
        }

        private void registerKit(KitId kitId, boolean enabled) {
            kitRegistryService.register(kitDefinition(kitId, kitId.value(), enabled));
        }
    }

    private static final class FakeArenaResetPort implements ArenaResetPort {
        private final List<ArenaDefinition> resetCalls = new ArrayList<>();

        @Override
        public void reset(ArenaDefinition arenaDefinition) {
            resetCalls.add(arenaDefinition);
        }
    }

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();
        private final Map<PlayerId, RuntimeException> restoreFailures = new HashMap<>();
        private final Map<PlayerId, RuntimeException> restoreFailuresRemainingOnce = new HashMap<>();
        private final List<PlayerId> restoredPlayers = new ArrayList<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot(playerId);
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoredPlayers.add(playerId);
            RuntimeException failure = restoreFailures.get(playerId);
            if (failure != null) {
                throw failure;
            }
            RuntimeException oneTimeFailure = restoreFailuresRemainingOnce.remove(playerId);
            if (oneTimeFailure != null) {
                throw oneTimeFailure;
            }
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        private void failRestoreFor(PlayerId playerId, String message) {
            restoreFailures.put(playerId, new IllegalStateException(message));
        }

        private void failRestoreOnceFor(PlayerId playerId, String message) {
            restoreFailuresRemainingOnce.put(playerId, new IllegalStateException(message));
        }
    }

    private static final class FakeMatchPlayerPort implements MatchPlayerPort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();
        private final List<PlayerId> clearedPlayers = new ArrayList<>();
        private final List<PlayerId> clearAttempts = new ArrayList<>();
        private final Set<PlayerId> matchStateClearedPlayers = new HashSet<>();
        private final Set<PlayerId> redundantClearPlayers = new HashSet<>();
        private final Map<PlayerId, RuntimeException> clearFailures = new HashMap<>();
        private final Map<PlayerId, RuntimeException> clearFailuresRemainingOnce = new HashMap<>();
        private final Map<PlayerId, RuntimeException> prepareFailures = new HashMap<>();
        private final Map<PlayerId, RuntimeException> spectatorPrepareFailures = new HashMap<>();

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                io.github.xreatlabz.revprac.domain.matches.MatchSide side,
                ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
            matchStateClearedPlayers.add(playerId);
            RuntimeException failure = prepareFailures.get(playerId);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
            matchStateClearedPlayers.add(playerId);
            RuntimeException failure = spectatorPrepareFailures.get(playerId);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
            clearAttempts.add(playerId);
            RuntimeException failure = clearFailures.get(playerId);
            if (failure != null) {
                throw failure;
            }
            RuntimeException oneTimeFailure = clearFailuresRemainingOnce.remove(playerId);
            if (oneTimeFailure != null) {
                throw oneTimeFailure;
            }
            if (!matchStateClearedPlayers.remove(playerId)) {
                redundantClearPlayers.add(playerId);
            }
            clearedPlayers.add(playerId);
        }

        private void failClearFor(PlayerId playerId) {
            clearFailures.put(playerId, new IllegalStateException("clear failed for requester"));
        }

        private void failClearFor(PlayerId playerId, String message) {
            clearFailures.put(playerId, new IllegalStateException(message));
        }

        private void failClearOnceFor(PlayerId playerId, String message) {
            clearFailuresRemainingOnce.put(playerId, new IllegalStateException(message));
        }

        private void failPrepareFor(PlayerId playerId, String message) {
            prepareFailures.put(playerId, new IllegalStateException(message));
        }

        private void failPrepareSpectatorFor(PlayerId playerId, String message) {
            spectatorPrepareFailures.put(playerId, new IllegalStateException(message));
        }
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerSafetySnapshot snapshot(PlayerId playerId) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                new InventorySnapshot(List.of(playerId.value().toString()), List.of(), List.of(), List.of(), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }
}
