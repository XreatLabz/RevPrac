package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.config.BootstrapConfig;
import io.github.xreatlabz.revprac.application.config.DiagnosticsConfig;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RevPracPluginSessionSafetyTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnableRegistersThePaperPlayerSessionListener() {
        MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);

        boolean registered = Arrays.stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(listener -> listener.getPlugin() == plugin
                        && listener.getListener().getClass().getName().equals(
                                "io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerSessionListener"));

        assertTrue(registered, "Plugin enable should register the Paper player session listener");
    }

    @Test
    void pluginEnableTracksPlayersAlreadyOnlineOnTheNextServerTick() {
        ServerMock server = MockBukkit.mock();
        World world = server.addSimpleWorld("already-online-world");
        PlayerMock player = server.addPlayer("already-online");
        player.teleport(new Location(world, 0.0d, 70.0d, 0.0d));
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerSessionService service = sessionService(plugin);
        PlayerId playerId = new PlayerId(player.getUniqueId());

        server.getScheduler().performOneTick();

        service.transitionTo(playerId, PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
    }

    @Test
    void bootstrapRuntimeShutdownCanRetryAfterARestoreFailure() {
        FlakyRestoreStatePort statePort = new FlakyRestoreStatePort();
        PlayerSessionService service = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                statePort);
        RecordingLifecycleReporter lifecycleReporter = new RecordingLifecycleReporter();
        BootstrapRuntime runtime = new BootstrapRuntime(
                new RevPracConfig(1, new BootstrapConfig(true), new DiagnosticsConfig(true)),
                lifecycleReporter,
                service);
        PlayerId flakyPlayer = new PlayerId(UUID.nameUUIDFromBytes("runtime-flaky".getBytes()));
        PlayerId healthyPlayer = new PlayerId(UUID.nameUUIDFromBytes("runtime-healthy".getBytes()));
        PlayerSafetySnapshot flakyBaseline = sampleSnapshot("runtime-flaky");
        PlayerSafetySnapshot healthyBaseline = sampleSnapshot("runtime-healthy");

        statePort.captureSnapshots.put(flakyPlayer, flakyBaseline);
        statePort.captureSnapshots.put(healthyPlayer, healthyBaseline);
        statePort.onlinePlayers.addAll(Set.of(flakyPlayer, healthyPlayer));
        statePort.failNextRestore(flakyPlayer);

        service.join(flakyPlayer);
        service.transitionTo(flakyPlayer, PlayerContext.MATCH, TransitionReason.MATCH_START);
        service.join(healthyPlayer);
        service.transitionTo(healthyPlayer, PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);

        IllegalStateException firstFailure =
                assertThrows(IllegalStateException.class, runtime::shutdown, "First shutdown should surface the failed restore");

        assertTrue(firstFailure.getMessage().contains("runtime restore failed"));
        assertEquals(1, statePort.restoreAttempts(flakyPlayer));
        assertEquals(1, statePort.restoreAttempts(healthyPlayer));
        assertTrue(lifecycleReporter.infoMessages.isEmpty(), "Shutdown should not report completion before service shutdown succeeds");

        runtime.shutdown();

        assertEquals(2, statePort.restoreAttempts(flakyPlayer), "Retry should re-attempt the failed restore");
        assertEquals(1, statePort.restoreAttempts(healthyPlayer), "Successful restores should not be replayed");
        assertEquals(List.of("RevPrac runtime shut down."), lifecycleReporter.infoMessages);
    }

    @Test
    void pluginDisableContainsPlayerSessionShutdownFailuresAndClearsRuntime() throws Exception {
        ServerMock server = MockBukkit.mock();
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        FlakyRestoreStatePort statePort = new FlakyRestoreStatePort();
        PlayerSessionService service = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                statePort);
        BootstrapRuntime failingRuntime = new BootstrapRuntime(
                new RevPracConfig(1, new BootstrapConfig(true), new DiagnosticsConfig(false)),
                new RecordingLifecycleReporter(),
                service);
        PlayerId flakyPlayer = new PlayerId(UUID.nameUUIDFromBytes("plugin-disable-flaky".getBytes()));
        PlayerSafetySnapshot baseline = sampleSnapshot("plugin-disable-flaky");
        statePort.captureSnapshots.put(flakyPlayer, baseline);
        statePort.onlinePlayers.add(flakyPlayer);
        statePort.failNextRestore(flakyPlayer);
        service.join(flakyPlayer);
        service.transitionTo(flakyPlayer, PlayerContext.MATCH, TransitionReason.MATCH_START);
        Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        runtimeField.set(plugin, failingRuntime);

        assertDoesNotThrow(() -> server.getPluginManager().disablePlugin(plugin));

        assertFalse(plugin.isEnabled());
        assertEquals(1, statePort.restoreAttempts(flakyPlayer));
        assertNullRuntime(plugin);
    }

    @Test
    void pluginDisableRestoresTrackedManagedOnlinePlayersAndClosesSessionIntake() {
        ServerMock server = MockBukkit.mock();
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerSessionService service = sessionService(plugin);

        World baselineWorld = server.addSimpleWorld("plugin-baseline-world");
        World mutatedWorld = server.addSimpleWorld("plugin-mutated-world");
        PlayerMock player = server.addPlayer("managed-player");
        PlayerId playerId = new PlayerId(player.getUniqueId());

        ItemStack[] expectedStorage = new ItemStack[player.getInventory().getStorageContents().length];
        expectedStorage[0] = new ItemStack(Material.DIAMOND_SWORD, 1);
        expectedStorage[8] = new ItemStack(Material.GOLDEN_APPLE, 2);
        player.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[player.getInventory().getArmorContents().length];
        expectedArmor[0] = new ItemStack(Material.IRON_BOOTS, 1);
        expectedArmor[3] = new ItemStack(Material.IRON_HELMET, 1);
        player.getInventory().setArmorContents(expectedArmor);

        ItemStack[] expectedExtra = new ItemStack[player.getInventory().getExtraContents().length];
        expectedExtra[expectedExtra.length - 1] = new ItemStack(Material.SHIELD, 1);
        player.getInventory().setExtraContents(expectedExtra);
        player.getInventory().setItemInOffHand(expectedExtra[expectedExtra.length - 1]);

        ItemStack[] expectedEnderChest = new ItemStack[player.getEnderChest().getSize()];
        expectedEnderChest[4] = new ItemStack(Material.ENDER_PEARL, 16);
        player.getEnderChest().setContents(expectedEnderChest);

        Location expectedLocation = new Location(baselineWorld, 30.0d, 88.0d, -15.0d, 180.0f, 9.0f);
        player.teleport(expectedLocation);
        player.getInventory().setHeldItemSlot(8);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(15.0d);
        player.setFoodLevel(7);
        player.setSaturation(2.5f);
        player.setExp(0.4f);
        player.setLevel(19);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1, false, true, true));

        service.join(playerId);
        service.transitionTo(playerId, PlayerContext.MATCH, TransitionReason.MATCH_START);

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[player.getInventory().getArmorContents().length]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.teleport(new Location(mutatedWorld, 1.0d, 65.0d, 1.0d, 0.0f, 0.0f));
        player.getInventory().setHeldItemSlot(0);
        player.setGameMode(GameMode.CREATIVE);
        player.setHealth(20.0d);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, false, false, false));

        server.getPluginManager().disablePlugin(plugin);

        assertFalse(plugin.isEnabled());
        assertArrayEquals(expectedStorage, player.getInventory().getStorageContents());
        assertArrayEquals(expectedArmor, player.getInventory().getArmorContents());
        assertArrayEquals(expectedExtra, player.getInventory().getExtraContents());
        assertArrayEquals(expectedEnderChest, player.getEnderChest().getContents());
        assertEquals(expectedLocation.getWorld(), player.getLocation().getWorld());
        assertEquals(expectedLocation.getX(), player.getLocation().getX());
        assertEquals(expectedLocation.getY(), player.getLocation().getY());
        assertEquals(expectedLocation.getZ(), player.getLocation().getZ());
        assertEquals(expectedLocation.getYaw(), player.getLocation().getYaw());
        assertEquals(expectedLocation.getPitch(), player.getLocation().getPitch());
        assertEquals(8, player.getInventory().getHeldItemSlot());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertEquals(15.0d, player.getHealth());
        assertEquals(7, player.getFoodLevel());
        assertEquals(2.5f, player.getSaturation());
        assertEquals(0.4f, player.getExp());
        assertEquals(19, player.getLevel());
        assertTrue(player.getAllowFlight());
        assertTrue(player.isFlying());
        assertEquals(1, player.getActivePotionEffects().size());
        assertEquals(PotionEffectType.SPEED, player.getActivePotionEffects().iterator().next().getType());
        assertThrows(IllegalStateException.class, () -> service.join(new PlayerId(java.util.UUID.randomUUID())));
    }

    private static PlayerSessionService sessionService(RevPracPlugin plugin) {
        BootstrapRuntime runtime = runtime(plugin);
        try {
            Field serviceField = BootstrapRuntime.class.getDeclaredField("playerSessionService");
            serviceField.setAccessible(true);
            Object service = serviceField.get(runtime);
            assertNotNull(service, "Bootstrap runtime should keep a PlayerSessionService");
            return (PlayerSessionService) service;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access PlayerSessionService from BootstrapRuntime", exception);
        }
    }

    private static BootstrapRuntime runtime(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            Object runtime = runtimeField.get(plugin);
            assertNotNull(runtime, "Plugin runtime should be present after successful enable");
            return (BootstrapRuntime) runtime;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access BootstrapRuntime from RevPracPlugin", exception);
        }
    }

    private static void assertNullRuntime(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            Object runtime = runtimeField.get(plugin);
            org.junit.jupiter.api.Assertions.assertNull(runtime, "Plugin runtime should be cleared after disable");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect BootstrapRuntime from RevPracPlugin", exception);
        }
    }

    private static PlayerSafetySnapshot sampleSnapshot(String suffix) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:" + suffix, 10.0d, 64.0d, -5.0d, 90.0f, 12.0f),
                new InventorySnapshot(List.of("storage-" + suffix), List.of(), List.of(), List.of(), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }

    private static final class RecordingLifecycleReporter implements LifecycleReporter {

        private final List<String> infoMessages = new java.util.ArrayList<>();

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void startupFailed(io.github.xreatlabz.revprac.application.result.Problem problem) {
            throw new AssertionError("startupFailed should not be called during shutdown tests");
        }
    }

    private static final class FlakyRestoreStatePort implements PlayerStatePort {

        private final java.util.Map<PlayerId, PlayerSafetySnapshot> captureSnapshots = new java.util.HashMap<>();
        private final java.util.Map<PlayerId, Integer> restoreAttempts = new java.util.HashMap<>();
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();
        private final Set<PlayerId> failNextRestorePlayers = new java.util.HashSet<>();

        void failNextRestore(PlayerId playerId) {
            failNextRestorePlayers.add(playerId);
        }

        int restoreAttempts(PlayerId playerId) {
            return restoreAttempts.getOrDefault(playerId, 0);
        }

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return captureSnapshots.get(playerId);
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoreAttempts.merge(playerId, 1, Integer::sum);
            if (failNextRestorePlayers.remove(playerId)) {
                throw new IllegalStateException("runtime restore failed for " + playerId.value());
            }
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }
}
