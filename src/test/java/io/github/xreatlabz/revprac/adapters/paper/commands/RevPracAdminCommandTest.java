package io.github.xreatlabz.revprac.adapters.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.RevPracPlugin;
import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandResult;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class RevPracAdminCommandTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void revpracCommandIsDeclaredWithAdminPermission() {
        ServerMock server = MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PluginCommand command = server.getPluginCommand("revprac");

        assertNotNull(command, "plugin.yml should declare /revprac");
        assertEquals("revprac.admin", command.getPermission());
        assertNotNull(command.getExecutor(), "Bootstrap should register the /revprac executor");
    }

    @Test
    void commandExecutionRequiresRevpracAdminPermission() {
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("permission-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("no-permission");

        CommandResult result = server.execute("revprac", player, "arena", "create", "bridge", "8");

        result.assertResponse("You do not have permission to use this command.");
        assertEquals(List.of(), arenaService(plugin).arenas());
        assertTrue(Files.notExists(plugin.getDataFolder().toPath().resolve("arenas.yml")));
    }

    @Test
    void arenaCreateRequiresPlayerCapturesLocationAndPersistsArena() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "arena-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("arena-admin");
        player.setOp(true);
        Location location = new Location(world, 10.75d, 65.25d, -4.4d, 135.0f, -12.5f);
        player.teleport(location);

        CommandResult consoleResult = server.executeConsole("revprac", "arena", "create", "bridge", "8");
        consoleResult.assertResponse("Only players can use /revprac arena create.");

        CommandResult result = server.execute("revprac", player, "arena", "create", "bridge", "8");

        result.assertResponse("Saved arena bridge.");
        List<ArenaDefinition> arenas = arenaService(plugin).arenas();
        assertEquals(1, arenas.size());
        ArenaDefinition arena = arenas.getFirst();
        assertEquals("bridge", arena.id().value());
        assertTrue(arena.enabled());
        assertEquals(player.getWorld().getKey().asString(), arena.bounds().worldKey());
        assertEquals(2, arena.bounds().minX());
        assertEquals(57, arena.bounds().minY());
        assertEquals(-13, arena.bounds().minZ());
        assertEquals(18, arena.bounds().maxX());
        assertEquals(73, arena.bounds().maxY());
        assertEquals(3, arena.bounds().maxZ());
        assertEquals(player.getWorld().getKey().asString(), arena.spawnOne().worldKey());
        assertEquals(player.getWorld().getKey().asString(), arena.spawnTwo().worldKey());
        assertEquals(location.getX(), arena.spawnOne().x());
        assertEquals(location.getY(), arena.spawnOne().y());
        assertEquals(location.getZ(), arena.spawnOne().z());
        assertEquals(location.getYaw(), arena.spawnOne().yaw());
        assertEquals(location.getPitch(), arena.spawnOne().pitch());
        assertEquals(arena.spawnOne(), arena.spawnTwo());

        Path arenasFile = plugin.getDataFolder().toPath().resolve("arenas.yml");
        assertTrue(Files.exists(arenasFile), "Arena save should persist arenas.yml");
        assertEquals(arenas, new PaperArenaRegistryFiles(plugin.getDataFolder().toPath()).load());
    }

    @Test
    void kitSaveCapturesLoadoutAndPersistsKit() throws Exception {
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("kit-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("kit-admin");
        player.setOp(true);
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 1));
        player.getInventory().setItem(4, new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 5));
        player.getInventory().setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET, 1));
        player.getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.TOTEM_OF_UNDYING, 1));
        player.getInventory().setHeldItemSlot(4);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, true, true));

        CommandResult result = server.execute("revprac", player, "kit", "save", "nodebuff");

        result.assertResponse("Saved kit nodebuff.");
        List<KitDefinition> kits = kitService(plugin).kits();
        assertEquals(1, kits.size());
        KitDefinition kit = kits.getFirst();
        assertEquals("nodebuff", kit.id().value());
        assertTrue(kit.enabled());
        assertEquals(4, kit.inventory().selectedSlot());
        assertNotNull(kit.inventory().storage().get(0));
        assertNotNull(kit.inventory().storage().get(4));
        assertNotNull(kit.inventory().armor().get(3));
        assertNotNull(kit.inventory().extra().get(0));
        assertEquals(List.of("minecraft:speed"), kit.potionEffects().stream().map(effect -> effect.effectKey()).toList());

        Path kitsFile = plugin.getDataFolder().toPath().resolve("kits.yml");
        assertTrue(Files.exists(kitsFile), "Kit save should persist kits.yml");
        assertEquals(kits, new PaperKitRegistryFiles(plugin.getDataFolder().toPath()).load());
    }

    @Test
    void invalidArgumentCountsReturnUsageWithoutMutatingRegistries() {
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("usage-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("usage-admin");
        player.setOp(true);

        CommandResult arenaResult = server.execute("revprac", player, "arena", "create", "bridge");
        CommandResult kitResult = server.execute("revprac", player, "kit", "save", "nodebuff", "extra");

        arenaResult.assertResponse("Usage: /revprac arena create <id> <radius>");
        kitResult.assertResponse("Usage: /revprac kit save <id>");
        assertEquals(List.of(), arenaService(plugin).arenas());
        assertEquals(List.of(), kitService(plugin).kits());
    }

    @Test
    void domainErrorsStayOperatorFacingAndDoNotMutateRegistries() {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "error-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("error-admin");
        player.setOp(true);
        player.teleport(new Location(world, 0.0d, 64.0d, 0.0d));

        CommandResult result = server.execute("revprac", player, "arena", "create", "Bridge!", "8");

        result.assertResponse("arena id must match [a-z0-9][a-z0-9_-]{0,62}");
        assertEquals(List.of(), arenaService(plugin).arenas());
        assertEquals(List.of(), kitService(plugin).kits());
    }

    @Test
    void arenaSaveFailureLeavesRuntimeRegistryUnchangedAndAllowsRetry() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "arena-failure-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("arena-failure-admin");
        player.setOp(true);
        player.teleport(new Location(world, 0.0d, 64.0d, 0.0d));

        Path blockingPath = plugin.getDataFolder().toPath().resolve("arena-save-blocker");
        Files.writeString(blockingPath, "blocking file");
        replaceRuntimeField(plugin, "arenaRegistryFiles", new PaperArenaRegistryFiles(blockingPath));

        CommandResult failedResult = server.execute("revprac", player, "arena", "create", "bridge", "8");

        failedResult.assertResponse("Failed to save arenas.yml.");
        assertEquals(List.of(), arenaService(plugin).arenas());

        replaceRuntimeField(plugin, "arenaRegistryFiles", new PaperArenaRegistryFiles(plugin.getDataFolder().toPath()));

        CommandResult retryResult = server.execute("revprac", player, "arena", "create", "bridge", "8");

        retryResult.assertResponse("Saved arena bridge.");
        assertEquals(List.of("bridge"), arenaService(plugin).arenas().stream()
                .map(arena -> arena.id().value())
                .toList());
    }

    @Test
    void kitSaveFailureLeavesRuntimeRegistryUnchangedAndAllowsRetry() throws Exception {
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("kit-failure-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("kit-failure-admin");
        player.setOp(true);
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 1));

        Path blockingPath = plugin.getDataFolder().toPath().resolve("kit-save-blocker");
        Files.writeString(blockingPath, "blocking file");
        replaceRuntimeField(plugin, "kitRegistryFiles", new PaperKitRegistryFiles(blockingPath));

        CommandResult failedResult = server.execute("revprac", player, "kit", "save", "nodebuff");

        failedResult.assertResponse("Failed to save kits.yml.");
        assertEquals(List.of(), kitService(plugin).kits());

        replaceRuntimeField(plugin, "kitRegistryFiles", new PaperKitRegistryFiles(plugin.getDataFolder().toPath()));

        CommandResult retryResult = server.execute("revprac", player, "kit", "save", "nodebuff");

        retryResult.assertResponse("Saved kit nodebuff.");
        assertEquals(List.of("nodebuff"), kitService(plugin).kits().stream()
                .map(kit -> kit.id().value())
                .toList());
    }

    @Test
    void overlappingArenaCreatesKeepYamlAndRuntimeInSync() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "arena-race-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("arena-race-admin");
        player.setOp(true);
        player.teleport(new Location(world, 0.0d, 64.0d, 0.0d));
        PluginCommand command = server.getPluginCommand("revprac");
        BlockingPersistenceHooks hooks = new BlockingPersistenceHooks();
        RevPracAdminCommand adminCommand = new RevPracAdminCommand(runtime(plugin), new io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter(), hooks);

        List<Throwable> failures = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(2);
        Thread first = new Thread(
                () -> invokeCommand(adminCommand, player, command, failures, done, "arena", "create", "bridge", "8"),
                "arena-create-bridge");
        Thread second = new Thread(
                () -> invokeCommand(adminCommand, player, command, failures, done, "arena", "create", "node", "8"),
                "arena-create-node");

        first.start();
        assertTrue(hooks.firstStageReached.await(1, TimeUnit.SECONDS), "First arena create should reach the staged save window");
        second.start();
        hooks.allowFirstSave.countDown();

        assertTrue(done.await(2, TimeUnit.SECONDS), "Both arena create commands should finish");
        assertEquals(List.of(), failures);
        assertEquals(
                List.of("bridge", "node"),
                arenaService(plugin).arenas().stream().map(arena -> arena.id().value()).toList());
        assertEquals(
                List.of("bridge", "node"),
                new PaperArenaRegistryFiles(plugin.getDataFolder().toPath()).load().stream()
                        .map(arena -> arena.id().value())
                        .toList());
    }

    @Test
    void overlappingKitSavesKeepYamlAndRuntimeInSync() throws Exception {
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("kit-race-world");
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        PlayerMock player = server.addPlayer("kit-race-admin");
        player.setOp(true);
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 1));
        player.getInventory().setItem(1, new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 4));
        PluginCommand command = server.getPluginCommand("revprac");
        BlockingPersistenceHooks hooks = new BlockingPersistenceHooks();
        RevPracAdminCommand adminCommand = new RevPracAdminCommand(runtime(plugin), new io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter(), hooks);

        List<Throwable> failures = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(2);
        Thread first = new Thread(
                () -> invokeCommand(adminCommand, player, command, failures, done, "kit", "save", "nodebuff"),
                "kit-save-nodebuff");
        Thread second = new Thread(
                () -> invokeCommand(adminCommand, player, command, failures, done, "kit", "save", "sumo"),
                "kit-save-sumo");

        first.start();
        assertTrue(hooks.firstStageReached.await(1, TimeUnit.SECONDS), "First kit save should reach the staged save window");
        second.start();
        hooks.allowFirstSave.countDown();

        assertTrue(done.await(2, TimeUnit.SECONDS), "Both kit save commands should finish");
        assertEquals(List.of(), failures);
        assertEquals(
                List.of("nodebuff", "sumo"),
                kitService(plugin).kits().stream().map(kit -> kit.id().value()).toList());
        assertEquals(
                List.of("nodebuff", "sumo"),
                new PaperKitRegistryFiles(plugin.getDataFolder().toPath()).load().stream()
                        .map(kit -> kit.id().value())
                        .toList());
    }

    private static ArenaRegistryService arenaService(RevPracPlugin plugin) {
        return (ArenaRegistryService) runtimeField(plugin, "arenaRegistryService");
    }

    private static KitRegistryService kitService(RevPracPlugin plugin) {
        return (KitRegistryService) runtimeField(plugin, "kitRegistryService");
    }

    private static BootstrapRuntime runtime(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            return (BootstrapRuntime) runtimeField.get(plugin);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access runtime", exception);
        }
    }

    private static Object runtimeField(RevPracPlugin plugin, String fieldName) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            BootstrapRuntime runtime = (BootstrapRuntime) runtimeField.get(plugin);
            Field serviceField = BootstrapRuntime.class.getDeclaredField(fieldName);
            serviceField.setAccessible(true);
            return serviceField.get(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access runtime field " + fieldName, exception);
        }
    }

    private static void replaceRuntimeField(RevPracPlugin plugin, String fieldName, Object value) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            BootstrapRuntime runtime = (BootstrapRuntime) runtimeField.get(plugin);
            Field field = BootstrapRuntime.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(runtime, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not replace runtime field " + fieldName, exception);
        }
    }

    private static void invokeCommand(
            RevPracAdminCommand command,
            PlayerMock player,
            PluginCommand pluginCommand,
            List<Throwable> failures,
            CountDownLatch done,
            String... args) {
        try {
            command.onCommand(player, pluginCommand, "revprac", args);
        } catch (Throwable throwable) {
            synchronized (failures) {
                failures.add(throwable);
            }
        } finally {
            done.countDown();
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

    private static final class BlockingPersistenceHooks implements RevPracAdminCommand.PersistenceHooks {

        private final AtomicInteger stageCalls = new AtomicInteger();
        private final CountDownLatch firstStageReached = new CountDownLatch(1);
        private final CountDownLatch allowFirstSave = new CountDownLatch(1);

        @Override
        public void afterArenaStage(ArenaDefinition arenaDefinition, List<ArenaDefinition> stagedArenas) {
            blockFirstStageOnly();
        }

        @Override
        public void afterKitStage(KitDefinition kitDefinition, List<KitDefinition> stagedKits) {
            blockFirstStageOnly();
        }

        private void blockFirstStageOnly() {
            if (stageCalls.getAndIncrement() != 0) {
                return;
            }
            firstStageReached.countDown();
            try {
                assertTrue(allowFirstSave.await(1, TimeUnit.SECONDS), "Test should release the first save");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to release the first save", exception);
            }
        }
    }
}
