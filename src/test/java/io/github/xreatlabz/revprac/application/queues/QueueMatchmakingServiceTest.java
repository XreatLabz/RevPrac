package io.github.xreatlabz.revprac.application.queues;

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
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class QueueMatchmakingServiceTest {

    @Test
    void unrankedMatchmakingPairsPlayersInFifoOrderWithinTheSameKit() {
        Harness harness = new Harness();
        QueueTicket first = harness.queueTicket("first", "player-one", QueueMode.UNRANKED, 10L, 0);
        QueueTicket second = harness.queueTicket("second", "player-two", QueueMode.UNRANKED, 11L, 0);
        QueueTicket third = harness.queueTicket("third", "player-three", QueueMode.UNRANKED, 12L, 0);
        harness.enqueue(first, second, third);

        harness.matchmakingService.tick(12L);

        Match match = harness.matchRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals(Set.of(first.playerId(), second.playerId()), Set.of(
                match.participants().playerOne(),
                match.participants().playerTwo()));
        assertTrue(harness.queueTicketRepository.findByPlayer(first.playerId()).isEmpty());
        assertTrue(harness.queueTicketRepository.findByPlayer(second.playerId()).isEmpty());
        assertEquals(third, harness.queueTicketRepository.findByPlayer(third.playerId()).orElseThrow());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(first.playerId()).orElseThrow().context());
        assertEquals(PlayerContext.MATCH, harness.playerSessions.find(second.playerId()).orElseThrow().context());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(third.playerId()).orElseThrow().context());
    }

    @Test
    void rankedMatchmakingUsesWindowCompatibilityThenRatingDeltaThenJoinTickThenTicketId() {
        Harness harness = new Harness();
        QueueTicket anchor = harness.queueTicket("anchor", "player-one", QueueMode.RANKED, 0L, 1000);
        QueueTicket largerDelta = harness.queueTicket("larger-delta", "player-two", QueueMode.RANKED, 1L, 1035);
        QueueTicket betterJoinLater = harness.queueTicket("better-join-later", "player-three", QueueMode.RANKED, 3L, 1020);
        QueueTicket tieEarlierJoinValueLowId =
                harness.queueTicket("tie-high-id", "player-four", QueueMode.RANKED, 2L, 1020);
        QueueTicket tieEarlierJoinValueHighId =
                harness.queueTicket("aaa-tie-low-id", "player-five", QueueMode.RANKED, 2L, 1020);
        QueueTicket outsideWindow = harness.queueTicket("outside-window", "player-six", QueueMode.RANKED, 1L, 1100);
        assertTrue(tieEarlierJoinValueLowId.id().value().compareTo(tieEarlierJoinValueHighId.id().value()) < 0);
        harness.enqueue(
                anchor,
                largerDelta,
                betterJoinLater,
                tieEarlierJoinValueLowId,
                tieEarlierJoinValueHighId,
                outsideWindow);

        harness.matchmakingService.tick(0L);

        assertTrue(
                harness.matchRepository.findAll().stream()
                        .map(match -> Set.of(match.participants().playerOne(), match.participants().playerTwo()))
                        .anyMatch(participants -> participants.equals(Set.of(anchor.playerId(), tieEarlierJoinValueLowId.playerId()))),
                "ranked sweep should pair the anchor with the smallest-delta earliest eligible candidate");
        assertTrue(harness.queueTicketRepository.findByPlayer(anchor.playerId()).isEmpty());
        assertTrue(harness.queueTicketRepository.findByPlayer(tieEarlierJoinValueLowId.playerId()).isEmpty());
        assertEquals(outsideWindow, harness.queueTicketRepository.findByPlayer(outsideWindow.playerId()).orElseThrow());
    }

    @Test
    void rankedMatchmakingBreaksTicketIdTiesByUuidValueOrder() {
        Harness harness = new Harness();
        QueueTicket anchor = harness.queueTicket(
                UUID.fromString("10000000-0000-0000-0000-000000000000"),
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                QueueMode.RANKED,
                0L,
                1000);
        QueueTicket valueLowerStringHigher = harness.queueTicket(
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                QueueMode.RANKED,
                1L,
                1010);
        QueueTicket stringLowerValueHigher = harness.queueTicket(
                UUID.fromString("00000000-0000-0001-0000-000000000000"),
                UUID.fromString("00000000-0000-0000-0000-000000000103"),
                QueueMode.RANKED,
                1L,
                1010);
        assertTrue(valueLowerStringHigher.id().value().compareTo(stringLowerValueHigher.id().value()) < 0);
        assertTrue(valueLowerStringHigher.id().value().toString()
                        .compareTo(stringLowerValueHigher.id().value().toString())
                > 0);
        harness.enqueue(anchor, valueLowerStringHigher, stringLowerValueHigher);

        harness.matchmakingService.tick(1L);

        assertTrue(
                harness.matchRepository.findAll().stream()
                        .map(match -> Set.of(match.participants().playerOne(), match.participants().playerTwo()))
                        .anyMatch(participants -> participants.equals(Set.of(anchor.playerId(), valueLowerStringHigher.playerId()))),
                "ranked ticket-id tie-break should use UUID value ordering");
        assertTrue(harness.queueTicketRepository.findByPlayer(anchor.playerId()).isEmpty());
        assertTrue(harness.queueTicketRepository.findByPlayer(valueLowerStringHigher.playerId()).isEmpty());
        assertEquals(
                stringLowerValueHigher,
                harness.queueTicketRepository.findByPlayer(stringLowerValueHigher.playerId()).orElseThrow());
    }

    @Test
    void closeIntakeStopsFurtherMatchmakingSweeps() {
        Harness harness = new Harness();
        QueueTicket first = harness.queueTicket("first", "player-one", QueueMode.UNRANKED, 1L, 0);
        QueueTicket second = harness.queueTicket("second", "player-two", QueueMode.UNRANKED, 2L, 0);
        harness.enqueue(first, second);
        harness.matchmakingService.closeIntake();

        harness.matchmakingService.tick(2L);

        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertEquals(first, harness.queueTicketRepository.findByPlayer(first.playerId()).orElseThrow());
        assertEquals(second, harness.queueTicketRepository.findByPlayer(second.playerId()).orElseThrow());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(first.playerId()).orElseThrow().context());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(second.playerId()).orElseThrow().context());
    }

    @Test
    void arenaUnavailableRestoresBothTicketsToSearchingWhilePlayersStayQueued() {
        Harness harness = new Harness();
        QueueTicket first = harness.queueTicket("first", "player-one", QueueMode.UNRANKED, 1L, 0);
        QueueTicket second = harness.queueTicket("second", "player-two", QueueMode.UNRANKED, 2L, 0);
        harness.enqueue(first, second);
        harness.reserveAllArenas();

        harness.matchmakingService.tick(2L);

        QueueTicket restoredFirst = harness.queueTicketRepository.findByPlayer(first.playerId()).orElseThrow();
        QueueTicket restoredSecond = harness.queueTicketRepository.findByPlayer(second.playerId()).orElseThrow();
        assertEquals("SEARCHING", restoredFirst.state().name());
        assertEquals("SEARCHING", restoredSecond.state().name());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(first.playerId()).orElseThrow().context());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(second.playerId()).orElseThrow().context());
        assertTrue(harness.matchRepository.findAll().isEmpty());
    }

    @Test
    void laterMatchStartFailuresDeleteTicketsAndPreserveMatchRollbackBehavior() {
        Harness harness = new Harness();
        QueueTicket first = harness.queueTicket("first", "player-one", QueueMode.RANKED, 1L, 1000);
        QueueTicket second = harness.queueTicket("second", "player-two", QueueMode.RANKED, 2L, 1005);
        harness.enqueue(first, second);
        harness.matchPlayerPort.failPrepareFor(second.playerId(), "prepare failed for second");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.matchmakingService.tick(2L));

        assertEquals("prepare failed for second", failure.getMessage());
        assertTrue(harness.queueTicketRepository.findByPlayer(first.playerId()).isEmpty());
        assertTrue(harness.queueTicketRepository.findByPlayer(second.playerId()).isEmpty());
        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(first.playerId()).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(second.playerId()).orElseThrow().context());
        assertEquals(1, harness.arenaResetPort.resetCalls.size());
    }

    private static final class Harness {
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
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
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                new MatchRuleset(2, 10, true),
                events::add);
        private final QueueMatchmakingService matchmakingService =
                new QueueMatchmakingService(
                        queueTicketRepository,
                        matchLifecycleService,
                        MatchmakingWindowPolicy.defaults(),
                        QueueConfig.defaults());
        private final List<ArenaReservationId> manualReservations = new ArrayList<>();

        private Harness() {
            arenaRegistryService.register(arenaDefinition("arena-b"));
            arenaRegistryService.register(arenaDefinition("arena-a"));
            arenaRegistryService.register(arenaDefinition("arena-c"));
            kitRegistryService.register(kitDefinition());
        }

        private void enqueue(QueueTicket... tickets) {
            for (QueueTicket ticket : tickets) {
                matchPlayerPort.onlinePlayers.add(ticket.playerId());
                playerStatePort.onlinePlayers.add(ticket.playerId());
                playerSessionService.join(ticket.playerId());
                playerSessionService.transitionTo(ticket.playerId(), PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
                assertTrue(queueTicketRepository.create(ticket));
            }
        }

        private QueueTicket queueTicket(String ticketSeed, String playerSeed, QueueMode mode, long joinedAtTick, int searchRating) {
            return queueTicket(
                    UUID.nameUUIDFromBytes(ticketSeed.getBytes()),
                    UUID.nameUUIDFromBytes(playerSeed.getBytes()),
                    mode,
                    joinedAtTick,
                    searchRating);
        }

        private QueueTicket queueTicket(UUID ticketId, UUID playerId, QueueMode mode, long joinedAtTick, int searchRating) {
            return new QueueTicket(
                    new QueueTicketId(ticketId),
                    new PlayerId(playerId),
                    new QueueKey(mode, new KitId("nodebuff")),
                    joinedAtTick,
                    searchRating,
                    io.github.xreatlabz.revprac.domain.queues.QueueTicketState.SEARCHING);
        }

        private void reserveAllArenas() {
            for (ArenaDefinition arena : arenaRegistryService.arenas()) {
                manualReservations.add(arenaRegistryService.reserve(arena.id(), "test-reservation").reservationId());
            }
        }

        private ArenaDefinition arenaDefinition(String arenaId) {
            return new ArenaDefinition(
                    new ArenaId(arenaId),
                    arenaId,
                    new ArenaCuboid("minecraft:world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true);
        }

        private KitDefinition kitDefinition() {
            return new KitDefinition(
                    new KitId("nodebuff"),
                    "NoDebuff",
                    new KitInventory(List.of("sword"), List.of("helmet", "chest", "legs", "boots"), List.of("rod"), 0),
                    List.of(),
                    new KitRules(false, false, false, true),
                    true);
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

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:world", 0.0d, 70.0d, 0.0d, 0.0f, 0.0f),
                    new InventorySnapshot(List.of(playerId.value().toString()), List.of(), List.of(), List.of(), null, 0),
                    new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }

    private static final class FakeMatchPlayerPort implements MatchPlayerPort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();
        private final Map<PlayerId, RuntimeException> prepareFailures = new HashMap<>();
        private final Set<PlayerId> preparedPlayers = new HashSet<>();

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
            preparedPlayers.add(playerId);
            RuntimeException failure = prepareFailures.get(playerId);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
            throw new UnsupportedOperationException("not needed for queue matchmaking tests");
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
            preparedPlayers.remove(playerId);
        }

        private void failPrepareFor(PlayerId playerId, String message) {
            prepareFailures.put(playerId, new IllegalStateException(message));
        }
    }
}
