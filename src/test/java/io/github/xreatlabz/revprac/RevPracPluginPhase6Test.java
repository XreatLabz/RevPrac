package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
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
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

final class RevPracPluginPhase6Test {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginYmlDeclaresLibrariesAndEnableWiresJdbcStorageIntoQueueRuntime() throws Exception {
        MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        YamlConfiguration pluginYaml = loadPluginYaml();

        assertEquals(
                List.of(
                        "com.zaxxer:HikariCP:7.0.2",
                        "org.flywaydb:flyway-core:12.5.0",
                        "org.xerial:sqlite-jdbc:3.53.0.0"),
                pluginYaml.getStringList("libraries"));
        assertTrue(Files.isRegularFile(plugin.getDataFolder().toPath().resolve("data/revprac.db")));
        assertNotNull(field(runtime, "storageRuntime"));
        assertInstanceOf(InMemoryQueueTicketRepository.class, field(runtime.queueService(), "queueTicketRepository"));

        Object ratingService = field(runtime.queueService(), "ratingService");
        Object ratingStore = field(ratingService, "ratingStore");
        Object playerRatingRepository = field(ratingStore, "playerRatingRepository");

        assertEquals(
                "io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcPlayerRatingRepository",
                playerRatingRepository.getClass().getName());
    }

    @Test
    void storageStartupFailureDisablesPluginWhenFailFastIsDisabled() {
        MockBukkit.mock();
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 1);
        config.set("bootstrap.fail-fast-on-enable", false);
        config.set("diagnostics.verbose-lifecycle-logs", false);
        config.set("storage.sqlite-path", "bad\u0000path.db");

        RevPracPlugin plugin = MockBukkit.loadWithConfig(RevPracPlugin.class, config);

        assertFalse(plugin.isEnabled());
        assertRuntimeAbsentIfDeclared(plugin);
    }

    @Test
    void pluginDisableClosesTheJdbcStorageRuntime() throws Exception {
        MockBukkit.mock();
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        Object storageRuntime = field(runtime, "storageRuntime");
        PlayerProfileRepository playerProfiles = playerProfiles(storageRuntime);

        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> playerProfiles.find(new PlayerId(UUID.randomUUID())));
        assertFalse(failure.getMessage().isBlank());
    }

    @Test
    void runtimeShutdownClosesStorageAfterPlayerShutdown() {
        RecordingLifecycleReporter lifecycleReporter = new RecordingLifecycleReporter();
        List<String> shutdownOrder = new ArrayList<>();
        RecordingPlayerStatePort playerStatePort = new RecordingPlayerStatePort(shutdownOrder);
        PlayerSessionService playerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                playerStatePort);
        PlayerId playerId = new PlayerId(UUID.nameUUIDFromBytes("phase6-storage-close-order".getBytes(StandardCharsets.UTF_8)));
        playerStatePort.onlinePlayers.add(playerId);
        playerStatePort.snapshot = sampleSnapshot();
        playerSessionService.join(playerId);
        playerSessionService.transitionTo(playerId, PlayerContext.MATCH, TransitionReason.MATCH_START);
        RecordingCloseable storageRuntime = new RecordingCloseable(shutdownOrder);
        BootstrapRuntime runtime = new BootstrapRuntime(
                new RevPracConfig(1, new BootstrapConfig(true), new DiagnosticsConfig(true)),
                lifecycleReporter,
                playerSessionService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                storageRuntime);

        runtime.shutdown();

        assertEquals(List.of("player-restore:" + playerId.value(), "storage-close"), shutdownOrder);
        assertEquals(List.of("RevPrac runtime shut down."), lifecycleReporter.infoMessages);
    }

    private static BootstrapRuntime runtime(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            return (BootstrapRuntime) runtimeField.get(plugin);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static PlayerProfileRepository playerProfiles(Object storageRuntime) throws Exception {
        return (PlayerProfileRepository) storageRuntime.getClass().getMethod("playerProfileRepository").invoke(storageRuntime);
    }

    private static YamlConfiguration loadPluginYaml() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                java.util.Objects.requireNonNull(
                        RevPracPlugin.class.getClassLoader().getResourceAsStream("plugin.yml"),
                        "plugin.yml must exist"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static void assertRuntimeAbsentIfDeclared(RevPracPlugin plugin) {
        try {
            Field runtimeField = RevPracPlugin.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            assertTrue(runtimeField.get(plugin) == null);
        } catch (NoSuchFieldException ignored) {
            // Runtime field is optional for this assertion.
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static PlayerSafetySnapshot sampleSnapshot() {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:phase6", 1.0d, 65.0d, 1.0d, 0.0f, 0.0f),
                new InventorySnapshot(List.of("sword"), List.of(), List.of(), List.of(), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }

    private static final class RecordingLifecycleReporter implements LifecycleReporter {
        private final List<String> infoMessages = new ArrayList<>();

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void startupFailed(io.github.xreatlabz.revprac.application.result.Problem problem) {
            throw new AssertionError(problem.message());
        }
    }

    private static final class RecordingPlayerStatePort implements PlayerStatePort {
        private final List<String> shutdownOrder;
        private final List<PlayerId> onlinePlayers = new ArrayList<>();
        private PlayerSafetySnapshot snapshot;

        private RecordingPlayerStatePort(List<String> shutdownOrder) {
            this.shutdownOrder = shutdownOrder;
        }

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot;
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            shutdownOrder.add("player-restore:" + playerId.value());
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }

    private static final class RecordingCloseable implements AutoCloseable {
        private final List<String> shutdownOrder;

        private RecordingCloseable(List<String> shutdownOrder) {
            this.shutdownOrder = shutdownOrder;
        }

        @Override
        public void close() {
            shutdownOrder.add("storage-close");
        }
    }
}
