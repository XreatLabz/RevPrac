package io.github.xreatlabz.revprac.adapters.paper.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class PaperMatchTickerTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tickerTicksEveryServerTickUntilCancelledAndCancelIsIdempotent() {
        ServerMock server = MockBukkit.mock();
        addKeyedWorld(server, "match-world");
        var plugin = MockBukkit.createMockPlugin();
        InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
        PlayerSessionService playerSessionService = new PlayerSessionService(
                sessionRepository, new InMemoryPendingRestorationRepository(), new SnapshotPlayerStatePort());
        ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), arenaDefinition -> {
                });
        KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        PaperMatchPlayerAdapter matchPlayerAdapter =
                new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());
        MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerAdapter,
                new MatchRuleset(2, 200, true),
                event -> {
                });
        PlayerMock requester = server.addPlayer("requester");
        PlayerMock target = server.addPlayer("target");
        playerSessionService.join(new PlayerId(requester.getUniqueId()));
        playerSessionService.join(new PlayerId(target.getUniqueId()));
        arenaRegistryService.register(arenaDefinition());
        kitRegistryService.register(kitDefinition(server));
        matchLifecycleService.startAcceptedDuel(new DuelRequest(
                new DuelRequestId(UUID.nameUUIDFromBytes("ticker-request".getBytes())),
                new PlayerId(requester.getUniqueId()),
                new PlayerId(target.getUniqueId()),
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                DuelRequestState.ACCEPTED,
                Instant.parse("2026-05-01T12:00:00Z"),
                Instant.parse("2026-05-01T12:00:30Z")));

        PaperMatchTicker ticker =
                new PaperMatchTicker(plugin, matchLifecycleService, matchRepository, matchPlayerAdapter);

        assertTrue(matchPlayerAdapter.isCountdownFrozen(new PlayerId(requester.getUniqueId())));

        ticker.start();
        server.getScheduler().performOneTick();
        assertEquals(1, matchRepository.findByPlayer(new PlayerId(requester.getUniqueId()))
                .orElseThrow()
                .countdownTicksRemaining());
        assertTrue(matchPlayerAdapter.isCountdownFrozen(new PlayerId(requester.getUniqueId())));

        server.getScheduler().performOneTick();
        assertEquals("ACTIVE", matchRepository.findByPlayer(new PlayerId(requester.getUniqueId()))
                .orElseThrow()
                .state()
                .name());
        assertFalse(matchPlayerAdapter.isCountdownFrozen(new PlayerId(requester.getUniqueId())));

        ticker.cancel();
        ticker.cancel();
        server.getScheduler().performOneTick();

        assertEquals(0, matchRepository.findByPlayer(new PlayerId(requester.getUniqueId()))
                .orElseThrow()
                .activeTicksElapsed());
    }

    @Test
    void tickerExposesOnlyTheSafeFourArgumentConstructor() {
        assertEquals(1, PaperMatchTicker.class.getConstructors().length);
        assertEquals(4, PaperMatchTicker.class.getConstructors()[0].getParameterCount());
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

    private static KitDefinition kitDefinition(ServerMock server) {
        PlayerMock source = server.addPlayer("kit-template");
        source.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD, 1));
        return new PaperKitLoadoutAdapter().capture(
                source,
                new KitId("nodebuff"),
                "Nodebuff",
                new KitRules(false, false, true, false),
                true);
    }

    private static WorldMock addKeyedWorld(ServerMock server, String worldName) {
        WorldMock world = new KeyedWorldMock(worldName);
        server.addWorld(world);
        return world;
    }

    private static final class KeyedWorldMock extends WorldMock {

        private final NamespacedKey key;

        private KeyedWorldMock(String worldName) {
            this.key = NamespacedKey.minecraft(worldName);
            setName(worldName);
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }

    private static final class SnapshotPlayerStatePort implements PlayerStatePort {

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:match-world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                    new InventorySnapshot(List.of(), List.of(), List.of(), List.of(), null, 0),
                    new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return true;
        }
    }
}
