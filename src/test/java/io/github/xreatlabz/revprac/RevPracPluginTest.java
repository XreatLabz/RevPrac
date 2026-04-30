package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
