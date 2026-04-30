package io.github.xreatlabz.revprac.application.players;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateNoArgs;
import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.invoke;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class PlayerSessionServiceTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String PLAYER_CONTEXT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerContext";
    private static final String TRANSITION_REASON_TYPE = "io.github.xreatlabz.revprac.domain.players.TransitionReason";
    private static final String PLAYER_SAFETY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot";
    private static final String LOCATION_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.LocationSnapshot";
    private static final String INVENTORY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.InventorySnapshot";
    private static final String PLAYER_STATUS_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot";
    private static final String PLAYER_SESSION_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSession";
    private static final String PENDING_RESTORATION_TYPE = "io.github.xreatlabz.revprac.domain.players.PendingRestoration";
    private static final String PLAYER_STATE_PORT_TYPE = "io.github.xreatlabz.revprac.ports.players.PlayerStatePort";
    private static final String PLAYER_SESSION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository";
    private static final String PENDING_RESTORATION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository";
    private static final String PLAYER_SESSION_SERVICE_TYPE =
            "io.github.xreatlabz.revprac.application.players.PlayerSessionService";
    private static final String IN_MEMORY_PLAYER_SESSION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository";
    private static final String IN_MEMORY_PENDING_RESTORATION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository";

    @Test
    void joinWithoutPendingRestorationCreatesLobbySession() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("join-without-pending");

        Object session = invoke(harness.join, harness.service, playerId);

        assertEquals("LOBBY", enumName(recordComponentValue(session, "context")));
        assertNull(recordComponentValue(session, "returnSnapshot"), "Lobby session should not keep a baseline snapshot");
        assertTrue(harness.findActiveSession(playerId).isPresent(), "Join should persist an active session");
        assertTrue(harness.statePort.restoreCalls.isEmpty(), "Plain joins should not restore a snapshot");
    }

    @Test
    void firstManagedTransitionCapturesExactlyOneBaselineSnapshot() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("first-managed-transition");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        Object session = invoke(harness.transitionTo, harness.service, playerId, harness.context("QUEUE"), harness.reason("QUEUE_JOIN"));

        assertEquals(1, harness.statePort.captureCalls.size(), "First managed transition should capture once");
        assertEquals(playerId, harness.statePort.captureCalls.getFirst(), "Capture should target the transitioning player");
        assertEquals("QUEUE", enumName(recordComponentValue(session, "context")));
        assertSame(baseline, recordComponentValue(session, "returnSnapshot"), "Managed session should store the captured baseline");
    }

    @Test
    void managedToManagedTransitionPreservesBaselineAndDoesNotRecapture() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("managed-to-managed");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        invoke(harness.transitionTo, harness.service, playerId, harness.context("QUEUE"), harness.reason("QUEUE_JOIN"));
        Object session = invoke(harness.transitionTo, harness.service, playerId, harness.context("MATCH"), harness.reason("MATCH_START"));

        assertEquals(1, harness.statePort.captureCalls.size(), "Managed-to-managed transitions must not recapture the baseline");
        assertEquals("MATCH", enumName(recordComponentValue(session, "context")));
        assertSame(baseline, recordComponentValue(session, "returnSnapshot"), "Managed transitions should preserve the original baseline");
    }

    @Test
    void returnToLobbyRestoresBaselineAndClearsManagedState() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("return-to-lobby");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        invoke(harness.transitionTo, harness.service, playerId, harness.context("MATCH"), harness.reason("MATCH_START"));
        Object session = invoke(harness.returnToLobby, harness.service, playerId);

        assertEquals(1, harness.statePort.restoreCalls.size(), "Return to lobby should restore exactly one baseline");
        assertEquals(playerId, harness.statePort.restoreCalls.getFirst().playerId());
        assertSame(baseline, harness.statePort.restoreCalls.getFirst().snapshot());
        assertEquals("LOBBY", enumName(recordComponentValue(session, "context")));
        assertNull(recordComponentValue(session, "returnSnapshot"), "Lobby session should clear managed baseline state");
    }

    @Test
    void quitFromLobbyRemovesActiveSessionWithoutPendingRestoration() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("quit-lobby");

        invoke(harness.join, harness.service, playerId);
        invoke(harness.quit, harness.service, playerId);

        assertTrue(harness.findActiveSession(playerId).isEmpty(), "Quit should remove the active lobby session");
        assertTrue(harness.findPendingRestoration(playerId).isEmpty(), "Lobby quit must not create a pending restore");
        assertTrue(harness.statePort.restoreCalls.isEmpty(), "Lobby quit must not restore immediately");
    }

    @Test
    void quitFromManagedContextCreatesOnePendingRestorationAndRemovesActiveSession() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("quit-managed");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        invoke(harness.transitionTo, harness.service, playerId, harness.context("MATCH"), harness.reason("MATCH_START"));
        invoke(harness.quit, harness.service, playerId);

        Optional<?> pending = harness.findPendingRestoration(playerId);
        assertTrue(pending.isPresent(), "Managed quit should persist a pending restoration");
        assertSame(baseline, recordComponentValue(pending.get(), "snapshot"), "Pending restoration should use the captured baseline");
        assertEquals("QUIT", enumName(recordComponentValue(pending.get(), "reason")));
        assertTrue(harness.findActiveSession(playerId).isEmpty(), "Managed quit should remove the active session");
        assertTrue(harness.statePort.restoreCalls.isEmpty(), "Managed quit should defer restore until the next join");
    }

    @Test
    void joinWithPendingRestorationRestoresOnceDeletesPendingTicketAndOpensLobbySession() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("join-with-pending");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        invoke(harness.transitionTo, harness.service, playerId, harness.context("QUEUE"), harness.reason("QUEUE_JOIN"));
        invoke(harness.quit, harness.service, playerId);

        Object session = invoke(harness.join, harness.service, playerId);

        assertEquals(1, harness.statePort.restoreCalls.size(), "Join should restore exactly once when a pending ticket exists");
        assertEquals(playerId, harness.statePort.restoreCalls.getFirst().playerId());
        assertSame(baseline, harness.statePort.restoreCalls.getFirst().snapshot());
        assertTrue(harness.findPendingRestoration(playerId).isEmpty(), "Join should delete the pending ticket after restore");
        assertEquals("LOBBY", enumName(recordComponentValue(session, "context")));
        assertNull(recordComponentValue(session, "returnSnapshot"), "Post-restore join should open a clean lobby session");
    }

    @Test
    void duplicateJoinDuringManagedSessionReturnsExistingSessionAndQuitStillCreatesPendingRestoration() {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("duplicate-join-managed");
        Object baseline = harness.snapshot("baseline");
        harness.statePort.captureResponses.put(playerId, baseline);

        invoke(harness.join, harness.service, playerId);
        Object managedSession =
                invoke(harness.transitionTo, harness.service, playerId, harness.context("MATCH"), harness.reason("MATCH_START"));

        Object duplicateJoinSession = invoke(harness.join, harness.service, playerId);

        assertSame(managedSession, duplicateJoinSession, "Duplicate join should return the existing managed session unchanged");
        assertEquals("MATCH", enumName(recordComponentValue(duplicateJoinSession, "context")));
        assertSame(baseline, recordComponentValue(duplicateJoinSession, "returnSnapshot"));
        assertEquals(1, harness.statePort.captureCalls.size(), "Duplicate join must not recapture the baseline");
        assertTrue(harness.statePort.restoreCalls.isEmpty(), "Duplicate join must not restore or clear the managed baseline");

        invoke(harness.quit, harness.service, playerId);

        Optional<?> pending = harness.findPendingRestoration(playerId);
        assertTrue(pending.isPresent(), "Managed quit should still create a pending restoration after a duplicate join");
        assertSame(baseline, recordComponentValue(pending.get(), "snapshot"), "Pending restoration must keep the original baseline");
        assertTrue(harness.findActiveSession(playerId).isEmpty(), "Quit should still remove the active session");
    }

    @Test
    void shutdownAllClosesIntakeRestoresOnlineManagedPlayersLeavesOfflinePendingTicketsAloneAndIsIdempotent()
            throws ReflectiveOperationException {
        ServiceHarness harness = newHarness();
        Object onlineManaged = harness.playerId("online-managed");
        Object onlineLobby = harness.playerId("online-lobby");
        Object offlineManaged = harness.playerId("offline-managed");
        Object offlinePending = harness.playerId("offline-pending");
        Object managedBaseline = harness.snapshot("managed-baseline");
        Object offlineManagedBaseline = harness.snapshot("offline-managed-baseline");
        Object pendingBaseline = harness.snapshot("pending-baseline");
        harness.statePort.captureResponses.put(onlineManaged, managedBaseline);
        harness.statePort.captureResponses.put(offlineManaged, offlineManagedBaseline);
        harness.statePort.onlinePlayers.addAll(Set.of(onlineManaged, onlineLobby));

        invoke(harness.join, harness.service, onlineManaged);
        invoke(harness.transitionTo, harness.service, onlineManaged, harness.context("MATCH"), harness.reason("MATCH_START"));
        invoke(harness.join, harness.service, offlineManaged);
        invoke(harness.transitionTo, harness.service, offlineManaged, harness.context("MATCH"), harness.reason("MATCH_START"));
        invoke(harness.join, harness.service, onlineLobby);
        invoke(harness.pendingSave, harness.pendingRepository, harness.pendingRestoration(offlinePending, pendingBaseline, "QUIT"));

        invoke(harness.shutdownAll, harness.service);

        assertEquals(1, harness.statePort.restoreCalls.size(), "Shutdown should restore each online managed player once");
        assertEquals(onlineManaged, harness.statePort.restoreCalls.getFirst().playerId());
        assertSame(managedBaseline, harness.statePort.restoreCalls.getFirst().snapshot());
        assertTrue(harness.findActiveSession(onlineManaged).isEmpty(), "Shutdown should remove the online managed session");
        assertTrue(harness.findActiveSession(onlineLobby).isEmpty(), "Shutdown should remove the online lobby session");
        Optional<?> offlineManagedPending = harness.findPendingRestoration(offlineManaged);
        assertTrue(offlineManagedPending.isPresent(), "Offline managed shutdown should preserve a pending restoration");
        assertSame(offlineManagedBaseline, recordComponentValue(offlineManagedPending.get(), "snapshot"));
        assertEquals("PLUGIN_DISABLE", enumName(recordComponentValue(offlineManagedPending.get(), "reason")));
        assertTrue(harness.findActiveSession(offlineManaged).isEmpty(), "Offline managed shutdown should remove active state after preserving pending restoration");
        assertTrue(harness.findPendingRestoration(offlinePending).isPresent(), "Existing offline pending tickets should remain untouched");

        invoke(harness.shutdownAll, harness.service);
        assertEquals(1, harness.statePort.restoreCalls.size(), "Shutdown should be idempotent once sessions are drained");

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> harness.join.invoke(harness.service, harness.playerId("late-join")),
                "Closed intake should reject new joins after shutdown");
        assertInstanceOf(IllegalStateException.class, exception.getCause(), "Closed intake should fail with IllegalStateException");
    }

    @Test
    void shutdownAllKeepsFailedManagedSessionsForRetryAndContinuesRestoringOthers() {
        ServiceHarness harness = newHarness();
        Object flakyManaged = harness.playerId("flaky-managed");
        Object healthyManaged = harness.playerId("healthy-managed");
        Object flakyBaseline = harness.snapshot("flaky-baseline");
        Object healthyBaseline = harness.snapshot("healthy-baseline");
        harness.statePort.captureResponses.put(flakyManaged, flakyBaseline);
        harness.statePort.captureResponses.put(healthyManaged, healthyBaseline);
        harness.statePort.onlinePlayers.addAll(Set.of(flakyManaged, healthyManaged));
        harness.statePort.failNextRestore(flakyManaged);

        invoke(harness.join, harness.service, flakyManaged);
        invoke(harness.transitionTo, harness.service, flakyManaged, harness.context("MATCH"), harness.reason("MATCH_START"));
        invoke(harness.join, harness.service, healthyManaged);
        invoke(harness.transitionTo, harness.service, healthyManaged, harness.context("QUEUE"), harness.reason("QUEUE_JOIN"));

        InvocationTargetException firstFailure = assertThrows(
                InvocationTargetException.class,
                () -> harness.shutdownAll.invoke(harness.service),
                "shutdownAll should surface the restore failure after draining other sessions");

        assertInstanceOf(
                IllegalStateException.class,
                firstFailure.getCause(),
                "Failed restore should remain actionable to the bootstrap layer");
        assertEquals(1, harness.statePort.restoreAttempts(flakyManaged));
        assertEquals(1, harness.statePort.restoreAttempts(healthyManaged));
        assertTrue(harness.findActiveSession(flakyManaged).isPresent(), "Failed restore should keep the managed session for retry");
        assertTrue(harness.findActiveSession(healthyManaged).isEmpty(), "Successful restore should still drain other managed sessions");

        invoke(harness.shutdownAll, harness.service);

        assertEquals(2, harness.statePort.restoreAttempts(flakyManaged), "Retry should restore the failed managed session");
        assertEquals(1, harness.statePort.restoreAttempts(healthyManaged), "Already-drained sessions must not be restored twice");
        assertTrue(harness.findActiveSession(flakyManaged).isEmpty(), "Successful retry should finish draining the failed session");
    }

    @Test
    void shutdownAllSerializesAgainstInFlightTransitionAndLeavesNoManagedSessionBehind() throws Exception {
        ServiceHarness harness = newHarness();
        Object playerId = harness.playerId("serialized-shutdown");
        Object baseline = harness.snapshot("baseline");
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        CountDownLatch shutdownFinished = new CountDownLatch(1);
        AtomicReference<Throwable> transitionFailure = new AtomicReference<>();
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();

        harness.statePort.captureResponses.put(playerId, baseline);
        harness.statePort.onlinePlayers.add(playerId);

        invoke(harness.join, harness.service, playerId);
        harness.statePort.blockNextCapture(captureEntered, releaseCapture);

        Thread transitionThread = new Thread(
                () -> invokeReflectively(
                        harness.transitionTo,
                        transitionFailure,
                        harness.service,
                        playerId,
                        harness.context("MATCH"),
                        harness.reason("MATCH_START")),
                "player-session-transition");
        transitionThread.start();

        assertTrue(captureEntered.await(1, TimeUnit.SECONDS), "Transition should block inside capture before shutdown begins");

        Thread shutdownThread = new Thread(
                () -> {
                    try {
                        invokeReflectively(harness.shutdownAll, shutdownFailure, harness.service);
                    } finally {
                        shutdownFinished.countDown();
                    }
                },
                "player-session-shutdown");
        shutdownThread.start();

        assertFalse(
                shutdownFinished.await(200, TimeUnit.MILLISECONDS),
                "shutdownAll should wait for the in-flight transition to finish before draining sessions");

        releaseCapture.countDown();
        transitionThread.join(1_000L);
        shutdownThread.join(1_000L);

        assertNull(transitionFailure.get(), "Transition should complete cleanly once capture is released");
        assertNull(shutdownFailure.get(), "shutdownAll should complete cleanly after the transition finishes");
        assertEquals(1, harness.statePort.restoreCalls.size(), "Serialized shutdown should restore the managed baseline once");
        assertEquals(playerId, harness.statePort.restoreCalls.getFirst().playerId());
        assertSame(baseline, harness.statePort.restoreCalls.getFirst().snapshot());
        assertTrue(harness.findActiveSession(playerId).isEmpty(), "No active managed session should remain after transition plus shutdown");
    }

    @Test
    void playerApplicationAndPortSourcesContainNoBukkitOrPaperImports() throws IOException {
        List<Path> sourceRoots = List.of(
                Path.of("src/main/java/io/github/xreatlabz/revprac/application/players"),
                Path.of("src/main/java/io/github/xreatlabz/revprac/ports/players"));

        for (Path root : sourceRoots) {
            assertTrue(Files.isDirectory(root), "Expected source directory to exist: " + root);
            try (Stream<Path> stream = Files.walk(root)) {
                List<Path> javaFiles = stream
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList();
                assertTrue(!javaFiles.isEmpty(), "Expected Java sources under " + root);
                for (Path javaFile : javaFiles) {
                    String source = Files.readString(javaFile);
                    assertTrue(!source.contains("org.bukkit"), "Application and port sources must not import Bukkit types: " + javaFile);
                    assertTrue(!source.contains("io.papermc"), "Application and port sources must not import Paper types: " + javaFile);
                }
            }
        }
    }

    private static ServiceHarness newHarness() {
        Class<?> playerIdType = loadClass(PLAYER_ID_TYPE);
        Class<?> playerContextType = loadClass(PLAYER_CONTEXT_TYPE);
        Class<?> transitionReasonType = loadClass(TRANSITION_REASON_TYPE);
        Class<?> playerSafetySnapshotType = loadClass(PLAYER_SAFETY_SNAPSHOT_TYPE);
        Class<?> pendingRestorationType = loadClass(PENDING_RESTORATION_TYPE);
        Class<?> playerStatePortType = loadClass(PLAYER_STATE_PORT_TYPE);
        Class<?> playerSessionRepositoryType = loadClass(PLAYER_SESSION_REPOSITORY_TYPE);
        Class<?> pendingRestorationRepositoryType = loadClass(PENDING_RESTORATION_REPOSITORY_TYPE);
        Class<?> serviceType = loadClass(PLAYER_SESSION_SERVICE_TYPE);

        Object playerSessionRepository = instantiateNoArgs(loadClass(IN_MEMORY_PLAYER_SESSION_REPOSITORY_TYPE));
        Object pendingRestorationRepository = instantiateNoArgs(loadClass(IN_MEMORY_PENDING_RESTORATION_REPOSITORY_TYPE));
        RecordingPlayerStatePort statePort = new RecordingPlayerStatePort();
        Object playerStatePort = Proxy.newProxyInstance(
                playerStatePortType.getClassLoader(),
                new Class<?>[] {playerStatePortType},
                statePort);

        Constructor<?> constructor;
        try {
            constructor = serviceType.getDeclaredConstructor(
                    playerSessionRepositoryType, pendingRestorationRepositoryType, playerStatePortType);
            constructor.setAccessible(true);
            Object service = constructor.newInstance(playerSessionRepository, pendingRestorationRepository, playerStatePort);

            return new ServiceHarness(
                    service,
                    playerSessionRepository,
                    pendingRestorationRepository,
                    statePort,
                    playerIdType,
                    playerContextType,
                    transitionReasonType,
                    playerSafetySnapshotType,
                    pendingRestorationType,
                    serviceType.getMethod("join", playerIdType),
                    serviceType.getMethod("transitionTo", playerIdType, playerContextType, transitionReasonType),
                    serviceType.getMethod("returnToLobby", playerIdType),
                    serviceType.getMethod("quit", playerIdType),
                    serviceType.getMethod("shutdownAll"),
                    playerSessionRepositoryType.getMethod("find", playerIdType),
                    pendingRestorationRepositoryType.getMethod("find", playerIdType),
                    pendingRestorationRepositoryType.getMethod("save", pendingRestorationType));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build PlayerSessionService harness", exception);
        }
    }

    private static String enumName(Object enumValue) {
        return ((Enum<?>) enumValue).name();
    }

    private static void invokeReflectively(Method method, AtomicReference<Throwable> failure, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            failure.set(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            failure.set(exception);
        }
    }

    private static Object enumConstant(Class<?> enumType, String name) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new AssertionError("Expected enum constant " + name + " on " + enumType.getName());
    }

    private static Map<String, Object> locationValues(String suffix) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", "minecraft:world_" + suffix);
        values.put("x", 10.5d);
        values.put("y", 64.0d);
        values.put("z", -14.25d);
        values.put("yaw", 180.0f);
        values.put("pitch", 12.5f);
        return values;
    }

    private static Map<String, Object> inventoryValues(String suffix) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storage", List.of("sword-" + suffix, "bow-" + suffix));
        values.put("armor", List.of("helmet", "chestplate", "leggings", "boots"));
        values.put("extra", List.of("offhand-" + suffix));
        values.put("enderChest", List.of("totem-" + suffix));
        values.put("cursorItem", null);
        values.put("selectedSlot", 0);
        return values;
    }

    private static Map<String, Object> statusValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gameMode", "SURVIVAL");
        values.put("health", 20.0d);
        values.put("foodLevel", 20);
        values.put("saturation", 5.0f);
        values.put("expProgress", 0.25f);
        values.put("level", 12);
        values.put("allowFlight", false);
        values.put("flying", false);
        values.put("potionEffects", List.of());
        return values;
    }

    private record RestoreCall(Object playerId, Object snapshot) {
    }

    private static final class RecordingPlayerStatePort implements InvocationHandler {
        private final Map<Object, Object> captureResponses = new ConcurrentHashMap<>();
        private final List<Object> captureCalls = Collections.synchronizedList(new ArrayList<>());
        private final List<RestoreCall> restoreCalls = Collections.synchronizedList(new ArrayList<>());
        private final Map<Object, Integer> restoreAttempts = new ConcurrentHashMap<>();
        private final Set<Object> failNextRestorePlayers = ConcurrentHashMap.newKeySet();
        private final Set<Object> onlinePlayers = ConcurrentHashMap.newKeySet();
        private volatile CountDownLatch captureEntered;
        private volatile CountDownLatch releaseCapture;

        void blockNextCapture(CountDownLatch captureEntered, CountDownLatch releaseCapture) {
            this.captureEntered = captureEntered;
            this.releaseCapture = releaseCapture;
        }

        void failNextRestore(Object playerId) {
            failNextRestorePlayers.add(playerId);
        }

        int restoreAttempts(Object playerId) {
            return restoreAttempts.getOrDefault(playerId, 0);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "capture" -> {
                    Object playerId = args[0];
                    captureCalls.add(playerId);
                    CountDownLatch entered = captureEntered;
                    CountDownLatch release = releaseCapture;
                    if (entered != null && release != null) {
                        captureEntered = null;
                        releaseCapture = null;
                        entered.countDown();
                        try {
                            release.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("Capture was interrupted while blocked", exception);
                        }
                    }
                    if (!captureResponses.containsKey(playerId)) {
                        throw new AssertionError("No capture response registered for " + playerId);
                    }
                    yield captureResponses.get(playerId);
                }
                case "restore" -> {
                    Object playerId = args[0];
                    restoreAttempts.merge(playerId, 1, Integer::sum);
                    restoreCalls.add(new RestoreCall(playerId, args[1]));
                    if (failNextRestorePlayers.remove(playerId)) {
                        throw new IllegalStateException("Simulated restore failure for " + playerId);
                    }
                    yield null;
                }
                case "isOnline" -> onlinePlayers.contains(args[0]);
                case "toString" -> "RecordingPlayerStatePort";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected method on PlayerStatePort: " + method);
            };
        }
    }

    private record ServiceHarness(
            Object service,
            Object playerSessionRepository,
            Object pendingRepository,
            RecordingPlayerStatePort statePort,
            Class<?> playerIdType,
            Class<?> playerContextType,
            Class<?> transitionReasonType,
            Class<?> playerSafetySnapshotType,
            Class<?> pendingRestorationType,
            Method join,
            Method transitionTo,
            Method returnToLobby,
            Method quit,
            Method shutdownAll,
            Method activeFind,
            Method pendingFind,
            Method pendingSave) {

        Object playerId(String seed) {
            return instantiateRecord(playerIdType, Map.of("value", UUID.nameUUIDFromBytes(seed.getBytes())));
        }

        Object context(String name) {
            return enumConstant(playerContextType, name);
        }

        Object reason(String name) {
            return enumConstant(transitionReasonType, name);
        }

        Object snapshot(String suffix) {
            Class<?> locationType = loadClass(LOCATION_SNAPSHOT_TYPE);
            Class<?> inventoryType = loadClass(INVENTORY_SNAPSHOT_TYPE);
            Class<?> playerStatusType = loadClass(PLAYER_STATUS_SNAPSHOT_TYPE);
            return instantiateRecord(playerSafetySnapshotType, Map.of(
                    "location", instantiateRecord(locationType, locationValues(suffix)),
                    "inventory", instantiateRecord(inventoryType, inventoryValues(suffix)),
                    "status", instantiateRecord(playerStatusType, statusValues())));
        }

        Object pendingRestoration(Object playerId, Object snapshot, String reasonName) {
            return instantiateRecord(pendingRestorationType, Map.of(
                    "playerId", playerId,
                    "snapshot", snapshot,
                    "reason", reason(reasonName)));
        }

        Optional<?> findActiveSession(Object playerId) {
            return (Optional<?>) invoke(activeFind, playerSessionRepository, playerId);
        }

        Optional<?> findPendingRestoration(Object playerId) {
            return (Optional<?>) invoke(pendingFind, pendingRepository, playerId);
        }
    }
}
