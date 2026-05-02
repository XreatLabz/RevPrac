package io.github.xreatlabz.revprac.adapters.paper.queues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.commands.RevPracQueueCommand;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueRatingRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.application.queues.QueueMatchmakingService;
import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.command.Command;

final class PaperQueueTickerTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tickerRunsSynchronouslyEveryConfiguredPeriodAndCancelIsIdempotent() {
        Harness harness = new Harness(10);
        QueueTicket first = harness.queueTicket("first", "player-one", 0L, 1000);
        QueueTicket second = harness.queueTicket("second", "player-two", 0L, 1075);
        harness.enqueue(first, second);

        harness.ticker.start();
        harness.performTicks(9);

        assertEquals(9L, harness.ticker.currentTick());
        assertFalse(ticketScheduledMatchExists(harness.matchRepository.findAll()));

        harness.performTicks(1);

        assertEquals(10L, harness.ticker.currentTick());
        assertNotNull(harness.matchRepository.findByPlayer(first.playerId()).orElse(null));

        harness.performTicks(10);
        assertEquals(20L, harness.ticker.currentTick());

        harness.ticker.cancel();
        harness.ticker.cancel();
        harness.performTicks(10);

        assertEquals(30L, harness.ticker.currentTick());
    }

    @Test
    void rankedJoinBetweenSweepsUsesTheActualCurrentTickWhenEvaluatingMatchmaking() {
        Harness harness = new Harness(10);
        harness.requester.setOp(true);
        harness.prepareLobbyPlayer(harness.requesterId());
        QueueTicket candidate = harness.queueTicket("candidate", harness.candidateId(), 10L, 1075);
        harness.enqueue(candidate);

        harness.ticker.start();
        harness.performTicks(9);

        harness.queueCommand.onCommand(
                harness.requester,
                command(),
                "queue",
                new String[] {"join", "ranked", "nodebuff"});

        QueueTicket joinedTicket = harness.queueService.ticket(harness.requesterId()).orElseThrow();
        assertEquals(9L, joinedTicket.joinedAtTick());

        harness.performTicks(1);

        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertEquals(joinedTicket, harness.queueService.ticket(harness.requesterId()).orElseThrow());
        assertEquals(candidate, harness.queueService.ticket(harness.candidateId()).orElseThrow());
    }

    @Test
    void tickerExposesOnlyTheSafeThreeArgumentConstructor() {
        assertEquals(1, PaperQueueTicker.class.getConstructors().length);
        assertEquals(3, PaperQueueTicker.class.getConstructors()[0].getParameterCount());
    }

    private static Command command() {
        return new Command("queue") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    private static boolean ticketScheduledMatchExists(Iterable<Match> matches) {
        return matches.iterator().hasNext();
    }

    private static final class Harness {
        private final ServerMock server = MockBukkit.mock();
        private final org.bukkit.plugin.java.JavaPlugin plugin = MockBukkit.createMockPlugin();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final InMemoryQueueRatingRepository queueRatingRepository = new InMemoryQueueRatingRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new NoOpArenaResetPort());
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final FakePlayerStatePort playerStatePort = new FakePlayerStatePort();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(playerSessions, new InMemoryPendingRestorationRepository(), playerStatePort);
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final QueueConfig queueConfig;
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                new MatchRuleset(2, 10, true),
                event -> {
                });
        private final QueueMatchmakingService matchmakingService;
        private final QueueService queueService;
        private final PaperQueueTicker ticker;
        private final RevPracQueueCommand queueCommand;
        private final PlayerMock requester = server.addPlayer("requester");
        private final PlayerMock candidate = server.addPlayer("candidate");

        private Harness(int periodTicks) {
            queueConfig = new QueueConfig(
                    periodTicks,
                    1000,
                    periodTicks,
                    List.of(
                            new MatchmakingWindowPolicy.WindowStep(0L, 50),
                            new MatchmakingWindowPolicy.WindowStep(1L, 100)));
            matchmakingService = new QueueMatchmakingService(
                    queueTicketRepository,
                    matchLifecycleService,
                    new MatchmakingWindowPolicy(queueConfig.rankedWindows()),
                    queueConfig);
            ticker = new PaperQueueTicker(plugin, matchmakingService, periodTicks);
            queueService = new QueueService(
                    queueTicketRepository,
                    queueRatingRepository,
                    new PlayerAvailabilityService(
                            matchRepository,
                            new io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository(),
                            queueTicketRepository),
                    playerSessionService,
                    kitRegistryService,
                    playerStatePort,
                    java.time.Clock.systemUTC(),
                    queueConfig);
            queueCommand = new RevPracQueueCommand(queueService, ticker::currentTick);
            arenaRegistryService.register(arenaDefinition());
            kitRegistryService.register(kitDefinition());
        }

        private void enqueue(QueueTicket... tickets) {
            for (QueueTicket ticket : tickets) {
                playerStatePort.onlinePlayers.add(ticket.playerId());
                matchPlayerPort.onlinePlayers.add(ticket.playerId());
                playerSessionService.join(ticket.playerId());
                playerSessionService.transitionTo(ticket.playerId(), PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
                queueTicketRepository.create(ticket);
            }
        }

        private void prepareLobbyPlayer(PlayerId playerId) {
            playerStatePort.onlinePlayers.add(playerId);
            matchPlayerPort.onlinePlayers.add(playerId);
            playerSessionService.join(playerId);
        }

        private QueueTicket queueTicket(String ticketSeed, String playerSeed, long joinedAtTick, int rating) {
            return new QueueTicket(
                    new QueueTicketId(UUID.nameUUIDFromBytes(ticketSeed.getBytes())),
                    new PlayerId(UUID.nameUUIDFromBytes(playerSeed.getBytes())),
                    new io.github.xreatlabz.revprac.domain.queues.QueueKey(QueueMode.RANKED, new KitId("nodebuff")),
                    joinedAtTick,
                    rating,
                    io.github.xreatlabz.revprac.domain.queues.QueueTicketState.SEARCHING);
        }

        private QueueTicket queueTicket(String ticketSeed, PlayerId playerId, long joinedAtTick, int rating) {
            return new QueueTicket(
                    new QueueTicketId(UUID.nameUUIDFromBytes(ticketSeed.getBytes())),
                    playerId,
                    new io.github.xreatlabz.revprac.domain.queues.QueueKey(QueueMode.RANKED, new KitId("nodebuff")),
                    joinedAtTick,
                    rating,
                    io.github.xreatlabz.revprac.domain.queues.QueueTicketState.SEARCHING);
        }

        private PlayerId requesterId() {
            return new PlayerId(requester.getUniqueId());
        }

        private PlayerId candidateId() {
            return new PlayerId(candidate.getUniqueId());
        }

        private void performTicks(int ticks) {
            for (int index = 0; index < ticks; index++) {
                server.getScheduler().performOneTick();
            }
        }
    }

    private static ArenaDefinition arenaDefinition() {
        return new ArenaDefinition(
                new ArenaId("arena-one"),
                "Arena One",
                new ArenaCuboid("minecraft:match-world", 0, 60, 0, 20, 90, 20),
                new ArenaSpawnPoint("minecraft:match-world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                new ArenaSpawnPoint("minecraft:match-world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                true);
    }

    private static KitDefinition kitDefinition() {
        return new KitDefinition(
                new KitId("nodebuff"),
                "Nodebuff",
                new KitInventory(List.of(), List.of(), List.of(), 0),
                List.of(),
                new KitRules(false, false, false, true),
                true);
    }

    private static final class NoOpArenaResetPort implements ArenaResetPort {
        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:match-world", 10.0d, 64.0d, -5.0d, 90.0f, 12.0f),
                    new InventorySnapshot(List.of(), List.of(), List.of(), List.of(), null, 0),
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

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                MatchSide side,
                ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
        }
    }
}
