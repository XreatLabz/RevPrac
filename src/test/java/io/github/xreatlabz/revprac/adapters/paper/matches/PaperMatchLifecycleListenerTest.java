package io.github.xreatlabz.revprac.adapters.paper.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerSessionListener;
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
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class PaperMatchLifecycleListenerTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deathEventCompletesActiveDuelAndClearsDropsAndExp() {
        Harness harness = new Harness();
        harness.startAndActivateMatch();
        PaperMatchLifecycleListener listener =
                new PaperMatchLifecycleListener(
                        harness.matchLifecycleService, harness.matchRepository, harness.matchPlayerAdapter);
        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                harness.requester(),
                damageSource(),
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND_SWORD, 1))),
                12,
                "requester fell");

        listener.onPlayerDeath(deathEvent);

        assertTrue(harness.matchRepository.findAll().isEmpty(), "death should complete and tear down the active match");
        assertEquals(PlayerContext.LOBBY, harness.sessionRepository.find(harness.targetId()).orElseThrow().context());
        assertTrue(deathEvent.getDrops().isEmpty(), "duel deaths must not leak kit drops");
        assertEquals(0, deathEvent.getDroppedExp(), "duel deaths must not drop experience");
        assertFalse(deathEvent.shouldDropExperience(), "duel deaths must opt out of experience drops");
    }

    @Test
    void nonMatchDeathsRemainUntouched() {
        Harness harness = new Harness();
        PlayerMock bystander = harness.server.addPlayer("bystander");
        PaperMatchLifecycleListener listener =
                new PaperMatchLifecycleListener(
                        harness.matchLifecycleService, harness.matchRepository, harness.matchPlayerAdapter);
        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                bystander,
                damageSource(),
                new ArrayList<>(List.of(new ItemStack(Material.BREAD, 2))),
                7,
                "bystander fell");

        listener.onPlayerDeath(deathEvent);

        assertEquals(1, deathEvent.getDrops().size(), "non-match drops should be left alone");
        assertEquals(7, deathEvent.getDroppedExp(), "non-match death exp should be left alone");
        assertTrue(deathEvent.shouldDropExperience(), "non-match deaths should keep default exp behavior");
    }

    @Test
    void quitHandlerRunsBeforeTheSessionQuitListenerAndUsesLowestPriority() throws Exception {
        Harness harness = new Harness();
        harness.startAndActivateMatch();
        PaperMatchLifecycleListener matchListener =
                new PaperMatchLifecycleListener(
                        harness.matchLifecycleService, harness.matchRepository, harness.matchPlayerAdapter);
        PaperPlayerSessionListener sessionListener =
                new PaperPlayerSessionListener(harness.plugin, harness.playerSessionService);
        PlayerQuitEvent event = new PlayerQuitEvent(harness.requester(), Component.text("quit"));

        matchListener.onPlayerQuit(event);
        sessionListener.onPlayerQuit(event);

        assertTrue(harness.matchRepository.findAll().isEmpty(), "match quit handling should complete before session cleanup");
        assertEquals(PlayerContext.LOBBY, harness.sessionRepository.find(harness.targetId()).orElseThrow().context());
        assertTrue(harness.sessionRepository.find(harness.requesterId()).isEmpty(), "the later session listener should remove the quitter");

        Method quitHandler = PaperMatchLifecycleListener.class.getDeclaredMethod("onPlayerQuit", PlayerQuitEvent.class);
        EventHandler annotation = quitHandler.getAnnotation(EventHandler.class);
        assertNotNull(annotation);
        assertEquals(EventPriority.LOWEST, annotation.priority());
    }

    @Test
    void frozenMovementAndSpectatorDamageAndInteractionAreCancelled() {
        Harness harness = new Harness();
        harness.startCountdownMatch();
        PaperMatchLifecycleListener listener =
                new PaperMatchLifecycleListener(
                        harness.matchLifecycleService, harness.matchRepository, harness.matchPlayerAdapter);

        Location from = harness.requester().getLocation().clone();
        Location to = from.clone().add(1.0d, 0.0d, 0.0d);
        PlayerMoveEvent moveEvent = new PlayerMoveEvent(harness.requester(), from, to);
        listener.onPlayerMove(moveEvent);

        assertTrue(moveEvent.isCancelled(), "countdown combatants should stay frozen");

        harness.matchLifecycleService.tick();
        harness.matchLifecycleService.tick();
        harness.matchLifecycleService.tick();
        harness.playerSessionService.join(harness.spectatorId());
        harness.matchLifecycleService.spectate(harness.spectatorId(), harness.requesterId());

        EntityDamageEvent damageEvent =
                new EntityDamageEvent(harness.spectator(), EntityDamageEvent.DamageCause.CUSTOM, 1.0d);
        listener.onEntityDamage(damageEvent);
        assertTrue(damageEvent.isCancelled(), "spectator damage attempts should be cancelled");

        EntityDamageByEntityEvent outgoingDamageEvent = new EntityDamageByEntityEvent(
                harness.spectator(),
                harness.requester(),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                2.0d);
        listener.onEntityDamageByEntity(outgoingDamageEvent);
        assertTrue(outgoingDamageEvent.isCancelled(), "spectators must not be able to damage combatants");

        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                harness.spectator(),
                Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.COMPASS, 1),
                null,
                null,
                EquipmentSlot.HAND);
        listener.onPlayerInteract(interactEvent);
        assertTrue(interactEvent.isCancelled(), "spectator interaction should be cancelled");
    }

    @Test
    void unrelatedPlayersAreIgnoredByTheProtectionHandlers() {
        Harness harness = new Harness();
        PlayerMock bystander = harness.server.addPlayer("bystander");
        PaperMatchLifecycleListener listener =
                new PaperMatchLifecycleListener(
                        harness.matchLifecycleService, harness.matchRepository, harness.matchPlayerAdapter);

        PlayerMoveEvent moveEvent = new PlayerMoveEvent(
                bystander,
                bystander.getLocation().clone(),
                bystander.getLocation().clone().add(1.0d, 0.0d, 0.0d));
        listener.onPlayerMove(moveEvent);

        EntityDamageEvent damageEvent = new EntityDamageEvent(bystander, EntityDamageEvent.DamageCause.CUSTOM, 1.0d);
        listener.onEntityDamage(damageEvent);

        assertFalse(moveEvent.isCancelled());
        assertFalse(damageEvent.isCancelled());
    }

    private static DamageSource damageSource() {
        return DamageSource.builder(DamageType.GENERIC).build();
    }

    private static final class Harness {
        private final ServerMock server = MockBukkit.mock();
        private final WorldMock world = addKeyedWorld(server, "match-world");
        private final org.bukkit.plugin.Plugin plugin = MockBukkit.createMockPlugin();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
        private final InMemoryPendingRestorationRepository pendingRepository = new InMemoryPendingRestorationRepository();
        private final RecordingPlayerStatePort playerStatePort = new RecordingPlayerStatePort();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(sessionRepository, pendingRepository, playerStatePort);
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new RecordingArenaResetPort());
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final PaperMatchPlayerAdapter matchPlayerAdapter =
                new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerAdapter,
                new MatchRuleset(3, 200, true),
                event -> {
                });
        private final PlayerMock requester = server.addPlayer("requester");
        private final PlayerMock target = server.addPlayer("target");
        private final PlayerMock spectator = server.addPlayer("spectator");

        private Harness() {
            requester.teleport(new Location(world, 5.0d, 70.0d, 5.0d));
            target.teleport(new Location(world, 15.0d, 70.0d, 15.0d));
            spectator.teleport(new Location(world, 10.0d, 72.0d, 10.0d));
            playerSessionService.join(requesterId());
            playerSessionService.join(targetId());
            arenaRegistryService.register(arenaDefinition());
            kitRegistryService.register(kitDefinition());
        }

        private PlayerMock requester() {
            return requester;
        }

        private PlayerMock target() {
            return target;
        }

        private PlayerMock spectator() {
            return spectator;
        }

        private PlayerId requesterId() {
            return new PlayerId(requester.getUniqueId());
        }

        private PlayerId targetId() {
            return new PlayerId(target.getUniqueId());
        }

        private PlayerId spectatorId() {
            return new PlayerId(spectator.getUniqueId());
        }

        private void startCountdownMatch() {
            DuelRequest acceptedRequest = new DuelRequest(
                    new DuelRequestId(UUID.nameUUIDFromBytes("duel".getBytes())),
                    requesterId(),
                    targetId(),
                    new ArenaId("arena-one"),
                    new KitId("nodebuff"),
                    DuelRequestState.ACCEPTED,
                    Instant.parse("2026-05-01T12:00:00Z"),
                    Instant.parse("2026-05-01T12:00:30Z"));
            matchLifecycleService.startAcceptedDuel(acceptedRequest);
        }

        private void startAndActivateMatch() {
            startCountdownMatch();
            matchLifecycleService.tick();
            matchLifecycleService.tick();
            matchLifecycleService.tick();
        }

        private ArenaDefinition arenaDefinition() {
            return new ArenaDefinition(
                    new ArenaId("arena-one"),
                    "Arena One",
                    new ArenaCuboid("minecraft:match-world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:match-world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:match-world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true);
        }

        private KitDefinition kitDefinition() {
            PlayerMock source = server.addPlayer("kit-source");
            source.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD, 1));
            source.getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 6));
            return new PaperKitLoadoutAdapter().capture(
                    source,
                    new KitId("nodebuff"),
                    "Nodebuff",
                    new KitRules(false, false, true, false),
                    true);
        }
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

    private static final class RecordingArenaResetPort implements ArenaResetPort {

        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static final class RecordingPlayerStatePort implements io.github.xreatlabz.revprac.ports.players.PlayerStatePort {

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new io.github.xreatlabz.revprac.domain.players.LocationSnapshot(
                            "minecraft:match-world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                    new io.github.xreatlabz.revprac.domain.players.InventorySnapshot(
                            List.of(), List.of(), List.of(), List.of(), null, 0),
                    new io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot(
                            "SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
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
