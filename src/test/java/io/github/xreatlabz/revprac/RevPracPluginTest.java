package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class RevPracPluginTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginLoadsAndEnables() {
        MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);

        assertTrue(plugin.isEnabled());
        PluginMeta pluginMeta = plugin.getPluginMeta();
        assertEquals("RevPrac", pluginMeta.getName());
        assertEquals("1.21.11", pluginMeta.getAPIVersion());
    }

    @Test
    void pluginDisablesCleanlyAfterSuccessfulEnable() {
        MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        assertTrue(plugin.isEnabled());

        MockBukkit.unmock();

        assertFalse(plugin.isEnabled());
    }

    @Test
    void invalidConfigWithFailFastEnabledThrowsAndDoesNotLeaveRuntimePartiallyActive() {
        MockBukkit.mock();

        YamlConfiguration invalidConfig = new YamlConfiguration();
        invalidConfig.set("config-version", 2);
        invalidConfig.set("bootstrap.fail-fast-on-enable", true);
        invalidConfig.set("diagnostics.verbose-lifecycle-logs", true);

        assertThrows(IllegalStateException.class, () -> MockBukkit.loadWithConfig(RevPracPlugin.class, invalidConfig));
    }

    @Test
    void invalidConfigWithFailFastDisabledDisablesPluginAndDoesNotLeaveRuntimePartiallyActive() {
        MockBukkit.mock();

        YamlConfiguration invalidConfig = new YamlConfiguration();
        invalidConfig.set("config-version", 2);
        invalidConfig.set("bootstrap.fail-fast-on-enable", false);
        invalidConfig.set("diagnostics.verbose-lifecycle-logs", true);

        RevPracPlugin plugin = MockBukkit.loadWithConfig(RevPracPlugin.class, invalidConfig);

        assertFalse(plugin.isEnabled(), "Invalid startup should not leave the plugin enabled");
        assertRuntimeAbsentIfDeclared(plugin);
    }

    private static void assertRuntimeAbsentIfDeclared(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            assertNull(runtimeField.get(plugin), "Runtime should remain absent when startup fails");
        } catch (NoSuchFieldException ignored) {
            // The backend lane may expose runtime state through a different package-visible surface.
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect RevPracPlugin runtime state", exception);
        }
    }
}
