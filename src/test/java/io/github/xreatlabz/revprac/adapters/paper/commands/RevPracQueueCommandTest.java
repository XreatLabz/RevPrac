package io.github.xreatlabz.revprac.adapters.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueRatingRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RevPracQueueCommandTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void commandRequiresPlayerSenderAndQueuePermission() {
        Harness harness = new Harness();

        harness.command.onCommand(harness.server.getConsoleSender(), command(), "queue", new String[] {"status"});
        assertEquals("Only players can use /queue.", harness.server.getConsoleSender().nextMessage());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"status"});
        assertEquals("You do not have permission to use this command.", harness.requester.nextMessage());
        assertTrue(harness.queueService.ticket(harness.requesterId()).isEmpty());
    }

    @Test
    void joinCommandsDelegateToQueueServiceWithModeAndCurrentTick() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        harness.target.setOp(true);
        harness.currentTick.set(42L);

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"join", "unranked", "nodebuff"});
        harness.currentTick.set(84L);
        harness.command.onCommand(harness.target, command(), "queue", new String[] {"join", "ranked", "nodebuff"});

        assertEquals("Joined unranked queue for kit nodebuff.", harness.requester.nextMessage());
        assertEquals("Joined ranked queue for kit nodebuff.", harness.target.nextMessage());
        assertEquals(QueueMode.UNRANKED, harness.queueService.ticket(harness.requesterId()).orElseThrow().key().mode());
        assertEquals(42L, harness.queueService.ticket(harness.requesterId()).orElseThrow().joinedAtTick());
        assertEquals(QueueMode.RANKED, harness.queueService.ticket(harness.targetId()).orElseThrow().key().mode());
        assertEquals(84L, harness.queueService.ticket(harness.targetId()).orElseThrow().joinedAtTick());
    }

    @Test
    void leaveAndStatusReportQueueStateWhileUsageErrorsDoNotMutateTickets() {
        Harness harness = new Harness();
        harness.requester.setOp(true);

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"status"});
        assertEquals("You are not queued.", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"join", "casual", "nodebuff"});
        assertEquals(RevPracQueueCommand.USAGE, harness.requester.nextMessage());
        assertTrue(harness.queueService.ticket(harness.requesterId()).isEmpty());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"join", "ranked", "nodebuff"});
        assertEquals("Joined ranked queue for kit nodebuff.", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"status"});
        assertEquals("Queued for ranked nodebuff.", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"leave"});
        assertEquals("Left queue.", harness.requester.nextMessage());
        assertTrue(harness.queueService.ticket(harness.requesterId()).isEmpty());
    }

    @Test
    void applicationErrorsAreCaughtAndSentBackToThePlayer() {
        Harness harness = new Harness();
        harness.requester.setOp(true);

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"join", "ranked", "boxing"});
        assertEquals("ranked queue is disabled for kit: boxing", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "queue", new String[] {"leave"});
        assertEquals("player is not queued", harness.requester.nextMessage());
    }

    private static Command command() {
        return new Command("queue") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    private static final class Harness {
        private final ServerMock server = MockBukkit.mock();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final RecordingPlayerStatePort playerStatePort = new RecordingPlayerStatePort();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(playerSessions, new InMemoryPendingRestorationRepository(), playerStatePort);
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final QueueService queueService;
        private final AtomicLong currentTick = new AtomicLong(0L);
        private final PlayerMock requester = server.addPlayer("requester");
        private final PlayerMock target = server.addPlayer("target");
        private final RevPracQueueCommand command;

        private Harness() {
            kitRegistryService.register(new KitDefinition(
                    new KitId("nodebuff"),
                    "Nodebuff",
                    new KitInventory(List.of(), List.of(), List.of(), 0),
                    List.of(),
                    new KitRules(false, false, false, true),
                    true));
            kitRegistryService.register(new KitDefinition(
                    new KitId("boxing"),
                    "Boxing",
                    new KitInventory(List.of(), List.of(), List.of(), 0),
                    List.of(),
                    new KitRules(false, false, false, false),
                    true));
            playerStatePort.onlinePlayers.add(requesterId());
            playerStatePort.onlinePlayers.add(targetId());
            playerSessionService.join(requesterId());
            playerSessionService.join(targetId());
            queueService = new QueueService(
                    queueTicketRepository,
                    new InMemoryQueueRatingRepository(),
                    new PlayerAvailabilityService(
                            new InMemoryMatchRepository(),
                            new InMemoryDuelRequestRepository(),
                            queueTicketRepository),
                    playerSessionService,
                    kitRegistryService,
                    playerStatePort,
                    Clock.systemUTC(),
                    QueueConfig.defaults());
            command = new RevPracQueueCommand(queueService, currentTick::get);
        }

        private PlayerId requesterId() {
            return new PlayerId(requester.getUniqueId());
        }

        private PlayerId targetId() {
            return new PlayerId(target.getUniqueId());
        }
    }

    private static final class RecordingPlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:lobby", 0.0d, 64.0d, 0.0d, 0.0f, 0.0f),
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
}
