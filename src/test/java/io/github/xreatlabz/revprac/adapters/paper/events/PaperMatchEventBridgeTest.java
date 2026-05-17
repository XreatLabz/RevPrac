package io.github.xreatlabz.revprac.adapters.paper.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.xreatlabz.revprac.api.events.RevPracMatchEvent;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class PaperMatchEventBridgeTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bridgePublishesVersionedBukkitEventWithoutChangingTheDomainPayload() {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        AtomicReference<RevPracMatchEvent> capturedEvent = new AtomicReference<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onRevPracMatchEvent(RevPracMatchEvent event) {
                capturedEvent.set(event);
            }
        }, plugin);
        MatchEvent domainEvent = new MatchEvent.DuelRequestCreated(
                7L,
                new DuelRequestId(UUID.nameUUIDFromBytes("event-request".getBytes(StandardCharsets.UTF_8))),
                player("event-requester"),
                player("event-target"),
                new ArenaId("arena-event"),
                new KitId("nodebuff"));

        new PaperMatchEventBridge(server.getPluginManager()).accept(domainEvent);

        assertSame(domainEvent, capturedEvent.get().domainEvent());
        assertEquals(RevPracMatchEvent.CONTRACT_VERSION, capturedEvent.get().contractVersion());
        assertEquals("DuelRequestCreated", capturedEvent.get().eventType());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }
}
