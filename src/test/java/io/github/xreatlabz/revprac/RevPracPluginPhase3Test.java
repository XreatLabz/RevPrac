package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;

final class RevPracPluginPhase3Test {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bootstrapLoadsArenaAndKitRegistriesIntoSharedRuntimeState() throws Exception {
        ServerMock server = MockBukkit.mock();
        PluginManagerMock pluginManager = server.getPluginManager();
        RevPracPlugin plugin = (RevPracPlugin) pluginManager.loadPlugin(RevPracPlugin.class);

        PaperArenaRegistryFiles arenaFiles = new PaperArenaRegistryFiles(plugin.getDataFolder().toPath());
        arenaFiles.save(List.of(new ArenaDefinition(
                new ArenaId("bridge"),
                "Bridge",
                new ArenaCuboid("minecraft:arena", -8, 56, -8, 8, 72, 8),
                new ArenaSpawnPoint("minecraft:arena", -2.5d, 64.0d, 0.5d, 90.0f, 0.0f),
                new ArenaSpawnPoint("minecraft:arena", 2.5d, 64.0d, 0.5d, -90.0f, 0.0f),
                true)));
        PaperKitRegistryFiles kitFiles = new PaperKitRegistryFiles(plugin.getDataFolder().toPath());
        kitFiles.save(List.of(new KitDefinition(
                new KitId("nodebuff"),
                "NoDebuff",
                new KitInventory(new ArrayList<>(java.util.Collections.nCopies(36, null)),
                        new ArrayList<>(java.util.Collections.nCopies(4, null)),
                        new ArrayList<>(java.util.Collections.nCopies(1, null)),
                        0),
                List.of(),
                new KitRules(false, false, true, false),
                true)));

        pluginManager.enablePlugin(plugin);

        BootstrapRuntime runtime = runtime(plugin);
        assertNotNull(runtimeField(runtime, "arenaRegistryService"));
        assertNotNull(runtimeField(runtime, "kitRegistryService"));
        assertNotNull(runtimeField(runtime, "arenaRegistryFiles"));
        assertNotNull(runtimeField(runtime, "kitRegistryFiles"));
        assertEquals(List.of("bridge"), ((ArenaRegistryService) runtimeField(runtime, "arenaRegistryService"))
                .arenas().stream().map(arena -> arena.id().value()).toList());
        assertEquals(List.of("nodebuff"), ((KitRegistryService) runtimeField(runtime, "kitRegistryService"))
                .kits().stream().map(kit -> kit.id().value()).toList());
    }

    @Test
    void invalidArenaRegistryFailsThroughExistingStartupFailurePath() throws Exception {
        ServerMock server = MockBukkit.mock();
        PluginManagerMock pluginManager = server.getPluginManager();
        RevPracPlugin plugin = (RevPracPlugin) pluginManager.loadPlugin(RevPracPlugin.class);
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.writeString(plugin.getDataFolder().toPath().resolve("arenas.yml"), "arenas: nope\n");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> pluginManager.enablePlugin(plugin));

        assertTrue(exception.getMessage().contains("bootstrap.registries.arenas"));
        assertRuntimeAbsentIfDeclared(plugin);
    }

    @Test
    void invalidKitRegistryRespectsFailFastDisabledPath() throws Exception {
        ServerMock server = MockBukkit.mock();
        PluginManagerMock pluginManager = server.getPluginManager();
        RevPracPlugin plugin = (RevPracPlugin) pluginManager.loadPlugin(RevPracPlugin.class);
        injectConfig(plugin, false);
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.writeString(plugin.getDataFolder().toPath().resolve("kits.yml"), "kits: nope\n");

        pluginManager.enablePlugin(plugin);

        assertFalse(plugin.isEnabled(), "Invalid registry with fail-fast disabled should disable the plugin");
        assertRuntimeAbsentIfDeclared(plugin);
    }

    private static void injectConfig(RevPracPlugin plugin, boolean failFastOnEnable) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 1);
        config.set("bootstrap.fail-fast-on-enable", failFastOnEnable);
        config.set("diagnostics.verbose-lifecycle-logs", false);

        Field newConfigField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("newConfig");
        newConfigField.setAccessible(true);
        newConfigField.set(plugin, config);
    }

    private static BootstrapRuntime runtime(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            return (BootstrapRuntime) runtimeField.get(plugin);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access RevPracPlugin runtime", exception);
        }
    }

    private static Object runtimeField(BootstrapRuntime runtime, String fieldName) {
        try {
            Field field = BootstrapRuntime.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not access BootstrapRuntime field " + fieldName, exception);
        }
    }

    private static void assertRuntimeAbsentIfDeclared(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            assertNull(runtimeField.get(plugin), "Runtime should remain absent when startup fails");
        } catch (NoSuchFieldException ignored) {
            // Runtime storage is optional for this assertion.
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect RevPracPlugin runtime state", exception);
        }
    }
}
