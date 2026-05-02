package io.github.xreatlabz.revprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchPlayerAdapter;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchTicker;
import io.github.xreatlabz.revprac.adapters.paper.queues.PaperQueueTicker;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueRatingRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.BootstrapConfig;
import io.github.xreatlabz.revprac.application.config.DiagnosticsConfig;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.application.queues.QueueMatchmakingService;
import io.github.xreatlabz.revprac.application.queues.QueueService;
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
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RevPracPluginPhase5Test {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnableWiresQueueCommandListenerTickerAndSharedQueueRepositories() {
        ServerMock server = MockBukkit.mock();

        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        PluginCommand queueCommand = plugin.getCommand("queue");

        assertNotNull(runtime.queueService());
        assertNotNull(runtime.queueMatchmakingService());
        assertNotNull(runtime.paperQueueTicker());
        assertTrue(Arrays.stream(PlayerQuitEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(listener -> listener.getPlugin() == plugin
                        && listener.getListener().getClass().getName().equals(
                                "io.github.xreatlabz.revprac.adapters.paper.queues.PaperQueueLifecycleListener")));
        assertNotNull(queueCommand);
        assertTrue(queueCommand.execute(server.getConsoleSender(), "queue", new String[0]));
        Permission queuePermission = server.getPluginManager().getPermission("revprac.queue");
        assertNotNull(queuePermission);
        assertEquals(PermissionDefault.TRUE, queuePermission.getDefault());
        assertQueueTickerScheduled(runtime.paperQueueTicker());

        Object queueRepository = field(runtime.queueService(), "queueTicketRepository");
        Object matchmakingRepository = field(runtime.queueMatchmakingService(), "queueTicketRepository");
        Object availabilityService = field(runtime.duelRequestService(), "availabilityService");
        Object availabilityRepository = field(availabilityService, "queueTicketRepository");
        Object ratingService = field(runtime.queueService(), "ratingService");
        Object ratingStore = field(ratingService, "ratingStore");
        Object playerRatingRepository = field(ratingStore, "playerRatingRepository");
        Object matchmakingPolicy = field(runtime.queueMatchmakingService(), "matchmakingWindowPolicy");
        Object sessionPlayerStatePort = field(field(runtime, "playerSessionService"), "playerStatePort");
        Object queuePlayerStatePort = field(runtime.queueService(), "playerStatePort");

        assertSame(queueRepository, matchmakingRepository);
        assertSame(queueRepository, availabilityRepository);
        assertSame(sessionPlayerStatePort, queuePlayerStatePort);
        assertInstanceOf(InMemoryQueueTicketRepository.class, queueRepository);
        assertEquals(
                "io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcPlayerRatingRepository",
                playerRatingRepository.getClass().getName());
        assertEquals(new MatchmakingWindowPolicy(runtime.config().queues().rankedWindows()), matchmakingPolicy);
        assertEquals(
                runtime.config().queues().matchmakingPeriodTicks(),
                ((Number) field(runtime.paperQueueTicker(), "periodTicks")).longValue());
    }

    @Test
    void pluginDisableClosesQueueIntakeDrainsQueuedPlayersAndCancelsQueueTicker() {
        MockBukkit.mock();
        RevPracPlugin plugin = MockBukkit.load(RevPracPlugin.class);
        BootstrapRuntime runtime = runtime(plugin);
        PlayerSessionService playerSessionService = (PlayerSessionService) field(runtime, "playerSessionService");
        PlayerMock queuedPlayer = MockBukkit.getMock().addPlayer("queued-player");
        PlayerId queuedPlayerId = new PlayerId(queuedPlayer.getUniqueId());
        runtime.kitRegistryService().register(new KitDefinition(
                new KitId("phase5-plugin-kit"),
                "Phase 5 Plugin Kit",
                new KitInventory(List.of(), List.of(), List.of(), 0),
                List.of(),
                new KitRules(false, false, false, false),
                true));

        playerSessionService.join(queuedPlayerId);
        runtime.queueService().join(
                queuedPlayerId,
                io.github.xreatlabz.revprac.domain.queues.QueueMode.UNRANKED,
                new KitId("phase5-plugin-kit"),
                0L);

        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        assertTrue(runtime.queueService().ticket(queuedPlayerId).isEmpty());
        assertTrue(sessionRepository(playerSessionService).find(queuedPlayerId).isEmpty());
        assertQueueMatchmakingIntakeClosed(runtime.queueMatchmakingService());
        assertQueueTickerCancelled(runtime.paperQueueTicker());
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> runtime.queueService().join(
                        queuedPlayerId,
                        io.github.xreatlabz.revprac.domain.queues.QueueMode.UNRANKED,
                        new KitId("phase5-plugin-kit"),
                        1L));
        assertEquals("queue intake is closed", failure.getMessage());
    }

    @Test
    void runtimeShutdownCancelsQueueWorkBeforeQueueDrainThenRunsMatchAndPlayerShutdown() {
        RuntimeShutdownHarness harness = new RuntimeShutdownHarness();

        harness.runtime.shutdown();

        assertEquals(List.of("RevPrac runtime shut down."), harness.lifecycleReporter.infoMessages);
        assertTrue(harness.queueService.ticket(harness.queuePlayer).isEmpty());
        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertTrue(harness.playerSessions.find(harness.queuePlayer).isEmpty());
        assertTrue(harness.playerSessions.find(harness.runtimeManagedPlayer).isEmpty());
        assertQueueMatchmakingIntakeClosed(harness.queueMatchmakingService);
        assertEquals(
                List.of(
                        "queue-ticker-cancel",
                        "player-restore:" + harness.queuePlayer.value(),
                        "match-ticker-cancel"),
                harness.shutdownOrder.subList(0, 3));
        assertTrue(
                harness.shutdownOrder.indexOf("match-clear:" + harness.requester.value())
                        < harness.shutdownOrder.indexOf("player-restore:" + harness.runtimeManagedPlayer.value()));
        IllegalStateException queueFailure = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(
                        harness.queuePlayer,
                        io.github.xreatlabz.revprac.domain.queues.QueueMode.UNRANKED,
                        new KitId("nodebuff"),
                        1L));
        assertEquals("queue intake is closed", queueFailure.getMessage());
        IllegalStateException duelFailure = assertThrows(
                IllegalStateException.class,
                () -> harness.duelRequestService.request(
                        harness.queuePlayer,
                        harness.runtimeManagedPlayer,
                        new ArenaId("arena-one"),
                        new KitId("nodebuff")));
        assertEquals("duel request intake is closed", duelFailure.getMessage());
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

    private static PlayerSessionRepository sessionRepository(PlayerSessionService playerSessionService) {
        return (PlayerSessionRepository) field(playerSessionService, "playerSessionRepository");
    }

    private static void assertQueueTickerScheduled(PaperQueueTicker ticker) {
        assertNotNull(field(ticker, "task"));
    }

    private static void assertQueueTickerCancelled(PaperQueueTicker ticker) {
        assertEquals(Boolean.TRUE, field(ticker, "cancelled"));
        assertEquals(null, field(ticker, "task"));
    }

    private static void assertQueueMatchmakingIntakeClosed(QueueMatchmakingService queueMatchmakingService) {
        Object intakeClosed = field(queueMatchmakingService, "intakeClosed");
        assertInstanceOf(java.util.concurrent.atomic.AtomicBoolean.class, intakeClosed);
        assertTrue(((java.util.concurrent.atomic.AtomicBoolean) intakeClosed).get());
    }

    private static final class RuntimeShutdownHarness {
        private final List<String> shutdownOrder = new ArrayList<>();
        private final RecordingLifecycleReporter lifecycleReporter = new RecordingLifecycleReporter();
        private final RecordingPlayerStatePort playerStatePort = new RecordingPlayerStatePort(shutdownOrder);
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(playerSessions, new InMemoryPendingRestorationRepository(), playerStatePort);
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new NoOpArenaResetPort());
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryDuelRequestRepository duelRequestRepository = new InMemoryDuelRequestRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final RecordingMatchPlayerPort matchPlayerPort = new RecordingMatchPlayerPort(shutdownOrder);
        private final PlayerAvailabilityService availabilityService =
                new PlayerAvailabilityService(matchRepository, duelRequestRepository, queueTicketRepository);
        private final QueueService queueService = new QueueService(
                queueTicketRepository,
                new InMemoryQueueRatingRepository(),
                availabilityService,
                playerSessionService,
                kitRegistryService,
                playerStatePort,
                Clock.systemUTC(),
                QueueConfig.defaults());
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                new MatchRuleset(3, 200, true),
                event -> {
                });
        private final DuelRequestService duelRequestService = new DuelRequestService(
                duelRequestRepository,
                matchRepository,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchLifecycleService,
                availabilityService,
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), java.time.ZoneOffset.UTC),
                Duration.ofSeconds(30),
                event -> {
                });
        private final ServerMock server = MockBukkit.mock();
        private final JavaPlugin plugin = MockBukkit.createMockPlugin();
        private final QueueMatchmakingService queueMatchmakingService = new QueueMatchmakingService(
                queueTicketRepository,
                matchLifecycleService,
                MatchmakingWindowPolicy.defaults(),
                QueueConfig.defaults());
        private final PaperQueueTicker paperQueueTicker = new PaperQueueTicker(plugin, queueMatchmakingService, 20);
        private final PaperMatchTicker paperMatchTicker = new PaperMatchTicker(
                plugin,
                matchLifecycleService,
                matchRepository,
                new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter()));
        private final BootstrapRuntime runtime = new BootstrapRuntime(
                new RevPracConfig(1, new BootstrapConfig(true), new DiagnosticsConfig(true)),
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                null,
                null,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker,
                queueService,
                queueMatchmakingService,
                paperQueueTicker);
        private final PlayerId queuePlayer = new PlayerId(UUID.nameUUIDFromBytes("phase5-queue-player".getBytes()));
        private final PlayerId requester = new PlayerId(UUID.nameUUIDFromBytes("phase5-requester".getBytes()));
        private final PlayerId target = new PlayerId(UUID.nameUUIDFromBytes("phase5-target".getBytes()));
        private final PlayerId runtimeManagedPlayer =
                new PlayerId(UUID.nameUUIDFromBytes("phase5-runtime-player".getBytes()));

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
                    new KitRules(false, false, false, true),
                    true));
            playerStatePort.captureSnapshots.put(queuePlayer, sampleSnapshot("queue"));
            playerStatePort.captureSnapshots.put(requester, sampleSnapshot("requester"));
            playerStatePort.captureSnapshots.put(target, sampleSnapshot("target"));
            playerStatePort.captureSnapshots.put(runtimeManagedPlayer, sampleSnapshot("runtime"));
            playerStatePort.onlinePlayers.addAll(Set.of(queuePlayer, requester, target, runtimeManagedPlayer));
            matchPlayerPort.onlinePlayers.addAll(Set.of(queuePlayer, requester, target, runtimeManagedPlayer));
            playerSessionService.join(queuePlayer);
            playerSessionService.join(requester);
            playerSessionService.join(target);
            playerSessionService.join(runtimeManagedPlayer);
            queueService.join(queuePlayer, io.github.xreatlabz.revprac.domain.queues.QueueMode.UNRANKED, new KitId("nodebuff"), 0L);
            playerSessionService.transitionTo(runtimeManagedPlayer, PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
            matchLifecycleService.startAcceptedDuel(new DuelRequest(
                    new DuelRequestId(UUID.nameUUIDFromBytes("phase5-runtime-duel".getBytes())),
                    requester,
                    target,
                    new ArenaId("arena-one"),
                    new KitId("nodebuff"),
                    DuelRequestState.ACCEPTED,
                    Instant.parse("2026-05-01T12:00:00Z"),
                    Instant.parse("2026-05-01T12:00:30Z")));
            setField(
                    paperQueueTicker,
                    "task",
                    proxyTask("queue-ticker-cancel", shutdownOrder));
            setField(
                    paperMatchTicker,
                    "task",
                    proxyTask("match-ticker-cancel", shutdownOrder));
        }
    }

    private static BukkitTask proxyTask(String marker, List<String> shutdownOrder) {
        return (BukkitTask) Proxy.newProxyInstance(
                BukkitTask.class.getClassLoader(),
                new Class<?>[] {BukkitTask.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("cancel")) {
                        shutdownOrder.add(marker);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object defaultValue(Class<?> returnType) {
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
        return null;
    }

    private static PlayerSafetySnapshot sampleSnapshot(String suffix) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:" + suffix, 10.0d, 64.0d, -5.0d, 90.0f, 12.0f),
                new InventorySnapshot(List.of("storage-" + suffix), List.of(), List.of(), List.of(), null, 0),
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
        private final Map<PlayerId, PlayerSafetySnapshot> captureSnapshots = new HashMap<>();
        private final Set<PlayerId> onlinePlayers = new HashSet<>();

        private RecordingPlayerStatePort(List<String> shutdownOrder) {
            this.shutdownOrder = shutdownOrder;
        }

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return captureSnapshots.get(playerId);
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

    private static final class RecordingMatchPlayerPort implements MatchPlayerPort {
        private final List<String> shutdownOrder;
        private final Set<PlayerId> onlinePlayers = new HashSet<>();

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
        }
    }

    private static final class NoOpArenaResetPort implements ArenaResetPort {
        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }
}
