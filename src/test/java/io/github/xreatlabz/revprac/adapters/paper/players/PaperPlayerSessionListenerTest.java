package io.github.xreatlabz.revprac.adapters.paper.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.players.PlayerProfileService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class PaperPlayerSessionListenerTest {

    @Test
    void joinEventDefersPlayerSessionServiceWorkUntilTheNextServerTick() {
        ServerMock server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            PlayerMock player = server.addPlayer("join-listener");
            PlayerId playerId = new PlayerId(player.getUniqueId());
            InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
            InMemoryPendingRestorationRepository pendingRepository = new InMemoryPendingRestorationRepository();
            RecordingStatePort statePort = new RecordingStatePort();
            PlayerSessionService service = new PlayerSessionService(sessionRepository, pendingRepository, statePort);
            PaperPlayerSessionListener listener = new PaperPlayerSessionListener(plugin, service);

            listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));

            assertTrue(sessionRepository.find(playerId).isEmpty(), "Join should not open a session from inside PlayerJoinEvent");
            assertTrue(statePort.restoreCalls.isEmpty(), "Join should not restore from inside PlayerJoinEvent");

            server.getScheduler().performOneTick();

            assertTrue(sessionRepository.find(playerId).isPresent(), "Join should open a lobby session");
            assertEquals(PlayerContext.LOBBY, sessionRepository.find(playerId).orElseThrow().context());
            assertTrue(statePort.restoreCalls.isEmpty(), "Plain joins should not force a restore");
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void deferredJoinSkipsPlayersWhoDisconnectBeforeTheNextServerTick() {
        ServerMock server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            PlayerMock player = server.addPlayer("join-then-disconnect");
            PlayerId playerId = new PlayerId(player.getUniqueId());
            InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
            InMemoryPendingRestorationRepository pendingRepository = new InMemoryPendingRestorationRepository();
            RecordingStatePort statePort = new RecordingStatePort();
            PlayerSessionService service = new PlayerSessionService(sessionRepository, pendingRepository, statePort);
            PaperPlayerSessionListener listener = new PaperPlayerSessionListener(plugin, service);

            listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));
            player.disconnect();
            server.getScheduler().performOneTick();

            assertTrue(sessionRepository.find(playerId).isEmpty(), "Deferred join should not create an offline session");
            assertTrue(statePort.restoreCalls.isEmpty(), "Deferred join should not restore an offline player");
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void joinEventTouchesThePlayerProfileAfterTheDeferredJoinTick() {
        ServerMock server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            PlayerMock player = server.addPlayer("profile-touch");
            PlayerId playerId = new PlayerId(player.getUniqueId());
            InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
            InMemoryPendingRestorationRepository pendingRepository = new InMemoryPendingRestorationRepository();
            RecordingStatePort statePort = new RecordingStatePort();
            PlayerSessionService sessionService = new PlayerSessionService(sessionRepository, pendingRepository, statePort);
            FakePlayerProfileRepository profileRepository = new FakePlayerProfileRepository();
            PlayerProfileService profileService = new PlayerProfileService(profileRepository);
            Instant joinInstant = Instant.parse("2026-05-02T12:00:00Z");
            PaperPlayerSessionListener listener = new PaperPlayerSessionListener(
                    plugin,
                    sessionService,
                    profileService,
                    Clock.fixed(joinInstant, ZoneOffset.UTC));

            listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));

            assertTrue(profileRepository.find(playerId).isEmpty(), "Profile touch should stay deferred with the join task");

            server.getScheduler().performOneTick();

            assertEquals(
                    new PlayerProfile(playerId, Optional.of("profile-touch"), joinInstant, joinInstant),
                    profileRepository.find(playerId).orElseThrow());
            assertTrue(sessionRepository.find(playerId).isPresent(), "Join should still open the lobby session");
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void quitEventDelegatesToThePlayerSessionService() {
        ServerMock server = MockBukkit.mock();
        try {
            PlayerMock player = server.addPlayer("quit-listener");
            PlayerId playerId = new PlayerId(player.getUniqueId());
            InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
            InMemoryPendingRestorationRepository pendingRepository = new InMemoryPendingRestorationRepository();
            RecordingStatePort statePort = new RecordingStatePort();
            statePort.snapshot = sampleSnapshot("quit-world");
            PlayerSessionService service = new PlayerSessionService(sessionRepository, pendingRepository, statePort);
            PaperPlayerSessionListener listener = new PaperPlayerSessionListener(MockBukkit.createMockPlugin(), service);

            listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));
            server.getScheduler().performOneTick();
            service.transitionTo(playerId, PlayerContext.MATCH, TransitionReason.MATCH_START);
            listener.onPlayerQuit(new PlayerQuitEvent(player, Component.text("left")));

            assertTrue(sessionRepository.find(playerId).isEmpty(), "Quit should remove the active session");
            Optional<?> pending = pendingRepository.find(playerId);
            assertTrue(pending.isPresent(), "Managed quit should persist a pending restoration");
            assertFalse(statePort.restoreInvoked, "Quit should defer restore until a future join or shutdown");
        } finally {
            MockBukkit.unmock();
        }
    }

    private static PlayerSafetySnapshot sampleSnapshot(String worldKey) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot(worldKey, 10.0d, 65.0d, -4.0d, 90.0f, 5.0f),
                new InventorySnapshot(List.of("storage"), List.of("armor"), List.of("extra"), List.of("ender"), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }

    private static final class RecordingStatePort implements PlayerStatePort {

        private PlayerSafetySnapshot snapshot;
        private boolean restoreInvoked;
        private final List<PlayerSafetySnapshot> restoreCalls = new java.util.ArrayList<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot;
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoreInvoked = true;
            restoreCalls.add(snapshot);
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return true;
        }
    }

    private static final class FakePlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }
    }
}
