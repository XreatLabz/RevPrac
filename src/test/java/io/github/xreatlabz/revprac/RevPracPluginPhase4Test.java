package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchPlayerAdapter;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchTicker;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.BootstrapConfig;
import io.github.xreatlabz.revprac.application.config.DiagnosticsConfig;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

final class RevPracPluginPhase4Test {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnableWiresDuelCommandMatchListenerAndRuntimeMatchServices() {
        ServerMock server = MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        PluginCommand duelCommand = plugin.getCommand("duel");

        assertNotNull(runtime);
        assertNotNull(runtime.duelRequestService(), "Bootstrap runtime should expose DuelRequestService");
        assertNotNull(runtime.matchLifecycleService(), "Bootstrap runtime should expose MatchLifecycleService");
        assertNotNull(runtime.paperMatchTicker(), "Bootstrap runtime should expose PaperMatchTicker");
        assertTrue(Arrays.stream(PlayerDeathEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(listener -> listener.getPlugin() == plugin
                        && listener.getListener().getClass().getName().equals(
                                "io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchLifecycleListener")));
        assertNotNull(duelCommand, "Plugin should declare /duel in plugin.yml");
        assertTrue(duelCommand.execute(server.getConsoleSender(), "duel", new String[0]));
        Permission duelPermission = server.getPluginManager().getPermission("revprac.duel");
        assertNotNull(duelPermission, "Plugin should register revprac.duel permission");
        assertEquals(PermissionDefault.TRUE, duelPermission.getDefault());
        assertTickerScheduled(runtime.paperMatchTicker());
    }

    @Test
    void pluginDisableClosesDuelIntakeAndCancelsTicker() {
        ServerMock server = MockBukkit.mock();
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        DuelRequestService duelRequestService = runtime.duelRequestService();
        PaperMatchTicker paperMatchTicker = runtime.paperMatchTicker();

        server.getPluginManager().disablePlugin(plugin);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> duelRequestService.request(
                        new PlayerId(UUID.randomUUID()),
                        new PlayerId(UUID.randomUUID()),
                        new ArenaId("arena-one"),
                        new KitId("nodebuff")));

        assertTrue(exception.getMessage().contains("duel request intake is closed"));
        assertFalse(plugin.isEnabled());
        assertTickerCancelled(paperMatchTicker);
    }

    @Test
    void runtimeShutdownAttemptsPlayerShutdownAfterMatchFailureAndAggregatesSuppressedFailures() {
        RuntimeShutdownHarness harness = new RuntimeShutdownHarness();
        harness.startManagedMatch();
        harness.runtimePlayerStatePort.failRestoreTimes(harness.runtimeManagedPlayer, 1, "runtime player restore failed");
        harness.matchPlayerPort.failClearTimes(harness.requester, 2, "match clear failed");

        IllegalStateException failure = assertThrows(IllegalStateException.class, harness.runtime::shutdown);

        assertEquals("match clear failed", failure.getMessage());
        assertEquals(
                List.of("match clear failed", "runtime player restore failed"),
                suppressedMessages(failure));
        assertTrue(
                harness.shutdownOrder.indexOf("match-clear:" + harness.requester.value())
                        < harness.shutdownOrder.indexOf("runtime-player-restore:" + harness.runtimeManagedPlayer.value()),
                "Runtime shutdown must attempt match teardown before player-session shutdown");
        assertEquals(2L,
                harness.shutdownOrder.stream()
                        .filter(step -> step.equals("match-clear:" + harness.requester.value()))
                        .count(),
                "Match shutdown should run and retry before surfacing the failure");
        assertEquals(
                List.of(),
                harness.lifecycleReporter.infoMessages,
                "Runtime shutdown must not report completion when any shutdown step fails");
    }

    @Test
    void runtimeShutdownCanRetryAfterFlakyMatchFailureWhileStillAttemptingPlayerShutdown() {
        RuntimeShutdownHarness harness = new RuntimeShutdownHarness();
        Match match = harness.startManagedMatch();
        harness.matchPlayerPort.failClearTimes(harness.requester, 2, "match clear failed");

        IllegalStateException firstFailure = assertThrows(IllegalStateException.class, harness.runtime::shutdown);

        assertEquals("match clear failed", firstFailure.getMessage());
        assertTrue(
                harness.shutdownOrder.contains("runtime-player-restore:" + harness.runtimeManagedPlayer.value()),
                "Player-session shutdown should still be attempted on the failed shutdown pass");
        assertEquals(
                List.of(),
                harness.lifecycleReporter.infoMessages,
                "Runtime shutdown should not flip to completed state until every shutdown step succeeds");

        harness.runtime.shutdown();

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "Second shutdown should retry and drain the retained match");
        assertEquals(3L,
                harness.shutdownOrder.stream()
                        .filter(step -> step.equals("match-clear:" + harness.requester.value()))
                        .count(),
                "Second shutdown should retry match teardown after the first failed attempt");
        assertEquals(List.of("RevPrac runtime shut down."), harness.lifecycleReporter.infoMessages);
    }

    @Test
    void runtimeShutdownAttemptsTickerMatchAndPlayerShutdownAfterDuelIntakeFailureAndAggregatesFailures() {
        RuntimeShutdownHarness harness = new RuntimeShutdownHarness();
        harness.startManagedMatch();
        harness.breakDuelIntakeState();
        harness.failTickerCancelTimes(1, "ticker cancel failed");
        harness.matchPlayerPort.failClearTimes(harness.requester, 2, "match clear failed");
        harness.runtimePlayerStatePort.failRestoreTimes(harness.runtimeManagedPlayer, 1, "runtime player restore failed");

        RuntimeException failure = assertThrows(RuntimeException.class, harness.runtime::shutdown);

        assertTrue(failure instanceof NullPointerException, "Duel intake failure should surface as the primary shutdown failure");
        assertEquals(
                List.of("ticker cancel failed", "match clear failed", "runtime player restore failed"),
                suppressedMessages(failure));
        assertEquals("ticker-cancel", harness.shutdownOrder.getFirst());
        assertEquals(
                4L,
                harness.shutdownOrder.stream().filter(step -> step.startsWith("match-clear:")).count(),
                "Shutdown should still attempt both match participant teardowns across the retried match shutdown");
        assertTrue(
                harness.shutdownOrder.indexOf("match-clear:" + harness.requester.value())
                        < harness.shutdownOrder.indexOf("runtime-player-restore:" + harness.runtimeManagedPlayer.value()));
        assertTrue(
                harness.shutdownOrder.indexOf("match-clear:" + harness.target.value())
                        < harness.shutdownOrder.indexOf("runtime-player-restore:" + harness.runtimeManagedPlayer.value()));
        assertEquals(
                List.of(),
                harness.lifecycleReporter.infoMessages,
                "Runtime shutdown must not report completion when any shutdown step fails");
    }

    @Test
    void runtimeShutdownCanRetryAfterFlakyDuelIntakeAndTickerFailures() {
        RuntimeShutdownHarness harness = new RuntimeShutdownHarness();
        Match match = harness.startManagedMatch();
        harness.breakDuelIntakeState();
        harness.failTickerCancelTimes(1, "ticker cancel failed");

        RuntimeException firstFailure = assertThrows(RuntimeException.class, harness.runtime::shutdown);

        assertTrue(firstFailure instanceof NullPointerException);
        assertEquals(List.of("ticker cancel failed"), suppressedMessages(firstFailure));
        assertTrue(
                harness.shutdownOrder.contains("ticker-cancel"),
                "Ticker cancellation should still be attempted on the failed shutdown pass");
        assertEquals(
                List.of(),
                harness.lifecycleReporter.infoMessages,
                "Runtime shutdown should not report completion until duel intake and ticker shutdown succeed");

        harness.repairDuelIntakeState();
        harness.runtime.shutdown();

        assertTrue(harness.matchRepository.find(match.id()).isEmpty(), "Second shutdown should retry and drain the retained match");
        assertEquals(List.of("RevPrac runtime shut down."), harness.lifecycleReporter.infoMessages);
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

    private static void assertTickerScheduled(PaperMatchTicker ticker) {
        try {
            Field taskField = PaperMatchTicker.class.getDeclaredField("task");
            taskField.setAccessible(true);
            assertNotNull(taskField.get(ticker), "PaperMatchTicker should schedule a repeating Bukkit task on enable");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect PaperMatchTicker scheduled task", exception);
        }
    }

    private static void assertTickerCancelled(PaperMatchTicker ticker) {
        try {
            Field taskField = PaperMatchTicker.class.getDeclaredField("task");
            taskField.setAccessible(true);
            Field cancelledField = PaperMatchTicker.class.getDeclaredField("cancelled");
            cancelledField.setAccessible(true);
            assertEquals(Boolean.TRUE, cancelledField.get(ticker));
            assertEquals(null, taskField.get(ticker));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect PaperMatchTicker cancellation state", exception);
        }
    }

    private static List<String> suppressedMessages(RuntimeException exception) {
        return Arrays.stream(exception.getSuppressed()).map(Throwable::getMessage).collect(Collectors.toList());
    }

    private static PlayerSafetySnapshot sampleSnapshot(String suffix) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:" + suffix, 10.0d, 64.0d, -5.0d, 90.0f, 12.0f),
                new InventorySnapshot(List.of("storage-" + suffix), List.of(), List.of(), List.of(), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }

    private static final class RuntimeShutdownHarness {

        private final List<String> shutdownOrder = new ArrayList<>();
        private final RecordingLifecycleReporter lifecycleReporter = new RecordingLifecycleReporter();
        private final RecordingPlayerStatePort runtimePlayerStatePort = new RecordingPlayerStatePort(shutdownOrder, "runtime-player");
        private final RecordingPlayerStatePort matchPlayerStatePort = new RecordingPlayerStatePort(shutdownOrder, "match-player");
        private final PlayerSessionService runtimePlayerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                runtimePlayerStatePort);
        private final PlayerSessionService matchPlayerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                matchPlayerStatePort);
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new NoOpArenaResetPort());
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryDuelRequestRepository duelRequestRepository = new InMemoryDuelRequestRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final RecordingMatchPlayerPort matchPlayerPort = new RecordingMatchPlayerPort(shutdownOrder);
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                matchPlayerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                new MatchRuleset(3, 200, true),
                event -> {
                });
        private final PlayerAvailabilityService availabilityService =
                new PlayerAvailabilityService(matchRepository, duelRequestRepository, queueTicketRepository);
        private final DuelRequestService duelRequestService = new DuelRequestService(
                duelRequestRepository,
                matchRepository,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchLifecycleService,
                availabilityService,
                java.time.Clock.systemUTC(),
                java.time.Duration.ofSeconds(30),
                event -> {
                });
        private final ServerMock server = MockBukkit.mock();
        private final Plugin plugin = MockBukkit.load(RevPracPlugin.class);
        private final PaperMatchTicker paperMatchTicker = new PaperMatchTicker(
                plugin,
                matchLifecycleService,
                matchRepository,
                new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter()));
        private final BootstrapRuntime runtime = new BootstrapRuntime(
                new RevPracConfig(1, new BootstrapConfig(true), new DiagnosticsConfig(true)),
                lifecycleReporter,
                runtimePlayerSessionService,
                arenaRegistryService,
                kitRegistryService,
                null,
                null,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker);
        private final PlayerId requester = new PlayerId(UUID.nameUUIDFromBytes("phase4-runtime-requester".getBytes()));
        private final PlayerId target = new PlayerId(UUID.nameUUIDFromBytes("phase4-runtime-target".getBytes()));
        private final PlayerId runtimeManagedPlayer =
                new PlayerId(UUID.nameUUIDFromBytes("phase4-runtime-managed-player".getBytes()));

        private RuntimeShutdownHarness() {
            arenaRegistryService.register(new ArenaDefinition(
                    new ArenaId("arena-one"),
                    "Arena One",
                    new ArenaCuboid("minecraft:match-world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:match-world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:match-world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true));
            kitRegistryService.register(new KitDefinition(
                    new KitId("nodebuff"),
                    "Nodebuff",
                    new KitInventory(List.of(), List.of(), List.of(), 0),
                    List.of(),
                    new KitRules(false, false, true, false),
                    true));

            matchPlayerStatePort.captureSnapshots.put(requester, sampleSnapshot("phase4-runtime-requester"));
            matchPlayerStatePort.captureSnapshots.put(target, sampleSnapshot("phase4-runtime-target"));
            matchPlayerStatePort.onlinePlayers.addAll(Set.of(requester, target));
            matchPlayerPort.onlinePlayers.addAll(Set.of(requester, target));
            matchPlayerSessionService.join(requester);
            matchPlayerSessionService.join(target);

            runtimePlayerStatePort.captureSnapshots.put(
                    runtimeManagedPlayer, sampleSnapshot("phase4-runtime-managed-player"));
            runtimePlayerStatePort.onlinePlayers.add(runtimeManagedPlayer);
            runtimePlayerSessionService.join(runtimeManagedPlayer);
            runtimePlayerSessionService.transitionTo(
                    runtimeManagedPlayer,
                    PlayerContext.QUEUE,
                    TransitionReason.QUEUE_JOIN);
        }

        private Match startManagedMatch() {
            DuelRequest acceptedRequest = new DuelRequest(
                    new DuelRequestId(UUID.nameUUIDFromBytes("phase4-runtime-duel".getBytes())),
                    requester,
                    target,
                    new ArenaId("arena-one"),
                    new KitId("nodebuff"),
                    DuelRequestState.ACCEPTED,
                    Instant.parse("2026-05-01T12:00:00Z"),
                    Instant.parse("2026-05-01T12:00:30Z"));
            return matchLifecycleService.startAcceptedDuel(acceptedRequest);
        }

        private void breakDuelIntakeState() {
            setField(duelRequestService, "intakeClosed", null);
        }

        private void repairDuelIntakeState() {
            setField(duelRequestService, "intakeClosed", new AtomicBoolean(false));
        }

        private void failTickerCancelTimes(int times, String message) {
            int[] remainingFailures = {times};
            setField(
                    paperMatchTicker,
                    "task",
                    (BukkitTask) Proxy.newProxyInstance(
                            BukkitTask.class.getClassLoader(),
                            new Class<?>[] {BukkitTask.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("cancel")) {
                                    shutdownOrder.add("ticker-cancel");
                                    if (remainingFailures[0] > 0) {
                                        remainingFailures[0]--;
                                        throw new IllegalStateException(message);
                                    }
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            }));
        }
    }

    private static final class RecordingLifecycleReporter implements LifecycleReporter {

        private final List<String> infoMessages = new ArrayList<>();

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void startupFailed(io.github.xreatlabz.revprac.application.result.Problem problem) {
            throw new AssertionError("startupFailed should not be called during runtime shutdown tests");
        }
    }

    private static final class RecordingPlayerStatePort implements PlayerStatePort {

        private final List<String> shutdownOrder;
        private final String label;
        private final Map<PlayerId, PlayerSafetySnapshot> captureSnapshots = new HashMap<>();
        private final Map<PlayerId, Integer> restoreFailuresRemaining = new HashMap<>();
        private final Map<PlayerId, String> restoreFailureMessages = new HashMap<>();
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        private RecordingPlayerStatePort(List<String> shutdownOrder, String label) {
            this.shutdownOrder = shutdownOrder;
            this.label = label;
        }

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return captureSnapshots.get(playerId);
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            shutdownOrder.add(label + "-restore:" + playerId.value());
            int remainingFailures = restoreFailuresRemaining.getOrDefault(playerId, 0);
            if (remainingFailures > 0) {
                restoreFailuresRemaining.put(playerId, remainingFailures - 1);
                throw new IllegalStateException(restoreFailureMessages.get(playerId));
            }
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        private void failRestoreTimes(PlayerId playerId, int times, String message) {
            restoreFailuresRemaining.put(playerId, times);
            restoreFailureMessages.put(playerId, message);
        }
    }

    private static final class RecordingMatchPlayerPort implements MatchPlayerPort {

        private final List<String> shutdownOrder;
        private final Map<PlayerId, Integer> clearFailuresRemaining = new HashMap<>();
        private final Map<PlayerId, String> clearFailureMessages = new HashMap<>();
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        private RecordingMatchPlayerPort(List<String> shutdownOrder) {
            this.shutdownOrder = shutdownOrder;
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                MatchSide side,
                ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
            shutdownOrder.add("match-clear:" + playerId.value());
            int remainingFailures = clearFailuresRemaining.getOrDefault(playerId, 0);
            if (remainingFailures > 0) {
                clearFailuresRemaining.put(playerId, remainingFailures - 1);
                throw new IllegalStateException(clearFailureMessages.get(playerId));
            }
        }

        private void failClearTimes(PlayerId playerId, int times, String message) {
            clearFailuresRemaining.put(playerId, times);
            clearFailureMessages.put(playerId, message);
        }
    }

    private static final class NoOpArenaResetPort implements ArenaResetPort {

        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not set field " + fieldName + " on " + target.getClass().getSimpleName(), exception);
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive return type: " + returnType);
    }
}
