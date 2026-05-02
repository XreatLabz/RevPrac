package io.github.xreatlabz.revprac.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.RevPracPlugin;
import io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcStorageRuntime;
import io.github.xreatlabz.revprac.application.config.LoadValidatedConfigService;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginManagerMock;

final class RevPracBootstrapTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void closesStorageRuntimeWhenPostStorageBootstrapFails() {
        ServerMock server = MockBukkit.mock();
        PluginManagerMock pluginManager = server.getPluginManager();
        RevPracPlugin plugin = (RevPracPlugin) pluginManager.loadPlugin(RevPracPlugin.class);
        AtomicReference<JdbcStorageRuntime> openedStorage = new AtomicReference<>();
        RevPracBootstrap bootstrap = new RevPracBootstrap(new LoadValidatedConfigService(), storageRuntime -> {
            openedStorage.set(storageRuntime);
            throw new IllegalStateException("post-storage bootstrap failure");
        });

        assertThrows(IllegalStateException.class, () -> bootstrap.enable(plugin));

        JdbcStorageRuntime storageRuntime = openedStorage.get();
        assertNotNull(storageRuntime);
        PlayerProfileRepository playerProfiles = storageRuntime.playerProfileRepository();
        assertThrows(IllegalStateException.class, () -> playerProfiles.find(new PlayerId(UUID.randomUUID())));
    }
}
