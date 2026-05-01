package io.github.xreatlabz.revprac.adapters.paper.queues;

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
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class PaperQueueLifecycleListenerTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void quitEventDelegatesToQueueServiceWithoutTouchingSessionRestore() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("queue-player");
        PlayerId playerId = new PlayerId(player.getUniqueId());
        InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
        RecordingPlayerStatePort playerStatePort = new RecordingPlayerStatePort();
        playerStatePort.onlinePlayers.add(playerId);
        PlayerSessionService playerSessionService =
                new PlayerSessionService(sessionRepository, new InMemoryPendingRestorationRepository(), playerStatePort);
        KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        kitRegistryService.register(new KitDefinition(
                new KitId("nodebuff"),
                "Nodebuff",
                new KitInventory(List.of(), List.of(), List.of(), 0),
                List.of(),
                new KitRules(false, false, true, false),
                true));
        QueueService queueService = new QueueService(
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
        PaperQueueLifecycleListener listener = new PaperQueueLifecycleListener(queueService);

        playerSessionService.join(playerId);
        queueService.join(playerId, io.github.xreatlabz.revprac.domain.queues.QueueMode.UNRANKED, new KitId("nodebuff"), 5L);
        listener.onPlayerQuit(new PlayerQuitEvent(player, Component.text("left")));

        assertTrue(queueService.ticket(playerId).isEmpty());
        assertEquals(PlayerContext.QUEUE, sessionRepository.find(playerId).orElseThrow().context());
        assertTrue(playerStatePort.restoreCalls.isEmpty());
    }

    private static final class RecordingPlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();
        private final List<PlayerSafetySnapshot> restoreCalls = new java.util.ArrayList<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:lobby", 0.0d, 64.0d, 0.0d, 0.0f, 0.0f),
                    new InventorySnapshot(List.of(), List.of(), List.of(), List.of(), null, 0),
                    new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoreCalls.add(snapshot);
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }
}
