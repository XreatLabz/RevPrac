package io.github.xreatlabz.revprac.application.arenas;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ArenaRegistryServiceTest {

    private static final String DOMAIN_ARENAS_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/arenas";
    private static final String APPLICATION_ARENAS_DIR = "src/main/java/io/github/xreatlabz/revprac/application/arenas";
    private static final String PORTS_ARENAS_DIR = "src/main/java/io/github/xreatlabz/revprac/ports/arenas";
    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String ARENA_CUBOID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid";
    private static final String ARENA_SPAWN_POINT_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint";
    private static final String ARENA_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition";
    private static final String ARENA_RESERVATION_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservation";
    private static final String ARENA_RESERVATION_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId";
    private static final String ARENA_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository";
    private static final String ARENA_RESET_PORT_TYPE = "io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort";
    private static final String ARENA_REGISTRY_SERVICE_TYPE =
            "io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService";

    @Test
    void registerAndListReturnDeterministicArenaIdOrder() {
        ServiceHarness harness = newHarness();
        Object zeta = harness.arenaDefinition("zeta", "Zeta", true);
        Object alpha = harness.arenaDefinition("alpha", "Alpha", true);

        harness.register(zeta);
        harness.register(alpha);

        List<?> arenas = harness.listArenas();
        assertEquals(List.of("alpha", "zeta"), arenas.stream()
                .map(arena -> recordComponentValue(recordComponentValue(arena, "id"), "value"))
                .toList());
    }

    @Test
    void duplicateArenaIdsAreRejected() {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);

        harness.register(arena);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> harness.registerMethod.invoke(harness.service, harness.arenaDefinition("bridge", "Bridge v2", true)));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Arena already exists: bridge", exception.getCause().getMessage());
    }

    @Test
    void duplicateArenaRegistrationCannotOverwriteAcrossServiceInstancesSharingRepository() {
        ServiceHarness firstHarness = newHarness();
        ServiceHarness secondHarness = newHarness(firstHarness.repository);

        firstHarness.register(firstHarness.arenaDefinition("bridge", "Bridge", true));

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> secondHarness.registerMethod.invoke(
                        secondHarness.service, secondHarness.arenaDefinition("bridge", "Bridge Replacement", false)));

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Arena already exists: bridge", exception.getCause().getMessage());
        assertEquals(
                "Bridge",
                recordComponentValue(firstHarness.repository.find(firstHarness.arenaId("bridge")).orElseThrow(), "displayName"));
    }

    @Test
    void disabledArenasCannotBeReserved() {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", false);
        harness.register(arena);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> harness.reserveMethod.invoke(harness.service, harness.arenaId("bridge"), "match:disabled"));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Arena is disabled: bridge", exception.getCause().getMessage());
        assertTrue(harness.resetPort.resetCalls.isEmpty(), "Disabled reserve must not trigger reset");
    }

    @Test
    void reserveAvailableArenaReturnsReservation() {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        Object reservation = harness.reserve("bridge", "match:queue-1");

        assertNotNull(recordComponentValue(reservation, "reservationId"));
        assertEquals("bridge", recordComponentValue(recordComponentValue(reservation, "arenaId"), "value"));
        assertEquals("match:queue-1", recordComponentValue(reservation, "ownerKey"));
    }

    @Test
    void releaseFreesArenaAndCallsResetExactlyOnce() {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        Object reservation = harness.reserve("bridge", "match:queue-1");
        harness.release(recordComponentValue(reservation, "reservationId"));

        assertEquals(1, harness.resetPort.resetCalls.size(), "Release should call reset exactly once");
        assertSame(arena, harness.resetPort.resetCalls.getFirst());

        Object nextReservation = harness.reserve("bridge", "match:queue-2");
        assertEquals("match:queue-2", recordComponentValue(nextReservation, "ownerKey"));
    }

    @Test
    void reserveFailsWhileArenaResetIsStillRunningAndSucceedsAfterResetCompletes() throws Exception {
        ServiceHarness harness = newHarness();
        harness.resetPort.resetStarted = new CountDownLatch(1);
        harness.resetPort.allowResetToFinish = new CountDownLatch(1);
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        Object reservation = harness.reserve("bridge", "match:queue-1");
        CountDownLatch releaseDone = new CountDownLatch(1);
        CountDownLatch releaseStarted = harness.resetPort.resetStarted;
        Thread releaseThread = new Thread(() -> {
            try {
                harness.release(recordComponentValue(reservation, "reservationId"));
            } finally {
                releaseDone.countDown();
            }
        }, "release-thread");
        releaseThread.start();

        assertTrue(releaseStarted.await(1, TimeUnit.SECONDS), "Reset should begin before overlap assertion");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> harness.reserve("bridge", "match:during-reset"));
        assertEquals("Arena is resetting: bridge", exception.getMessage());

        harness.resetPort.allowResetToFinish.countDown();
        assertTrue(releaseDone.await(1, TimeUnit.SECONDS), "Release should finish after reset is unblocked");

        Object nextReservation = harness.reserve("bridge", "match:after-reset");
        assertEquals("match:after-reset", recordComponentValue(nextReservation, "ownerKey"));
    }

    @Test
    void resetFailureDoesNotWedgeArenaReservationState() {
        ServiceHarness harness = newHarness();
        harness.resetPort.throwOnReset = new IllegalStateException("reset failed");
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        Object reservation = harness.reserve("bridge", "match:queue-1");
        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> harness.release(recordComponentValue(reservation, "reservationId")));

        assertInstanceOf(IllegalStateException.class, exception);
        assertEquals("reset failed", exception.getMessage());
        assertEquals(1, harness.resetPort.resetCalls.size(), "Release should still attempt reset once");

        IllegalStateException reserveFailure =
                assertThrows(IllegalStateException.class, () -> harness.reserve("bridge", "match:queue-2"));
        assertEquals("Arena is already reserved: bridge", reserveFailure.getMessage());
    }

    @Test
    void resetFailureClearsResettingMarkerAfterThrowing() throws Exception {
        ServiceHarness harness = newHarness();
        harness.resetPort.resetStarted = new CountDownLatch(1);
        harness.resetPort.allowResetToFinish = new CountDownLatch(1);
        harness.resetPort.throwOnReset = new IllegalStateException("reset failed");
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        Object reservation = harness.reserve("bridge", "match:queue-1");
        CountDownLatch releaseFailed = new CountDownLatch(1);
        Throwable[] releaseFailure = new Throwable[1];
        Thread releaseThread = new Thread(() -> {
            try {
                harness.release(recordComponentValue(reservation, "reservationId"));
            } catch (Throwable throwable) {
                releaseFailure[0] = throwable;
            } finally {
                releaseFailed.countDown();
            }
        }, "release-thread");
        releaseThread.start();

        assertTrue(harness.resetPort.resetStarted.await(1, TimeUnit.SECONDS), "Reset should begin before overlap assertion");

        IllegalStateException overlapException =
                assertThrows(IllegalStateException.class, () -> harness.reserve("bridge", "match:during-reset"));
        assertEquals("Arena is resetting: bridge", overlapException.getMessage());

        harness.resetPort.allowResetToFinish.countDown();
        assertTrue(releaseFailed.await(1, TimeUnit.SECONDS), "Release should finish after reset failure");

        IllegalStateException resetFailure = assertInstanceOf(IllegalStateException.class, releaseFailure[0]);
        assertEquals("reset failed", resetFailure.getMessage());

        IllegalStateException reserveFailure =
                assertThrows(IllegalStateException.class, () -> harness.reserve("bridge", "match:after-failed-reset"));
        assertEquals("Arena is already reserved: bridge", reserveFailure.getMessage());
    }

    @Test
    void unknownReservationIdIsRejectedWithoutCallingReset() {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);
        Object unknownReservationId = harness.reservationId(UUID.randomUUID());

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> harness.releaseMethod.invoke(harness.service, unknownReservationId));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(
                "Unknown reservation: " + recordComponentValue(unknownReservationId, "value"),
                exception.getCause().getMessage());
        assertTrue(harness.resetPort.resetCalls.isEmpty(), "Unknown release must not call reset");
    }

    @Test
    void concurrentReserveRaceProducesOneSuccessAndOneDeterministicFailure() throws Exception {
        ServiceHarness harness = newHarness();
        Object arena = harness.arenaDefinition("bridge", "Bridge", true);
        harness.register(arena);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Object> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        Runnable contender = () -> {
            ready.countDown();
            try {
                assertTrue(start.await(1, TimeUnit.SECONDS), "Test failed to synchronize reserve race start");
                Object reservation = harness.reserve("bridge", "match:" + Thread.currentThread().getName());
                synchronized (successes) {
                    successes.add(reservation);
                }
            } catch (Throwable throwable) {
                synchronized (failures) {
                    failures.add(throwable);
                }
            } finally {
                done.countDown();
            }
        };

        Thread first = new Thread(contender, "one");
        Thread second = new Thread(contender, "two");
        first.start();
        second.start();

        assertTrue(ready.await(1, TimeUnit.SECONDS), "Both contenders should be ready");
        start.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS), "Both contenders should finish");

        assertEquals(1, successes.size(), "Exactly one reservation should succeed");
        assertEquals(1, failures.size(), "Exactly one reservation should fail");
        Throwable failure = failures.getFirst();
        if (failure instanceof InvocationTargetException invocationTargetException) {
            failure = invocationTargetException.getCause();
        }
        IllegalStateException exception = assertInstanceOf(IllegalStateException.class, failure);
        assertEquals("Arena is already reserved: bridge", exception.getMessage());
    }

    @Test
    void applicationAndPortArenaSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertNoBukkitOrPaperImports(Path.of(APPLICATION_ARENAS_DIR));
        assertNoBukkitOrPaperImports(Path.of(PORTS_ARENAS_DIR));
    }

    private static void assertNoBukkitOrPaperImports(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), "Expected directory to exist: " + directory);

        try (Stream<Path> sources = Files.walk(directory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected Java sources in " + directory);
            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + source);
            }
        }
    }

    private static ServiceHarness newHarness() {
        Class<?> arenaIdType = loadClass(ARENA_ID_TYPE);
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);
        Class<?> arenaReservationType = loadClass(ARENA_RESERVATION_TYPE);
        Class<?> arenaReservationIdType = loadClass(ARENA_RESERVATION_ID_TYPE);
        Class<?> repositoryType = loadClass(ARENA_REGISTRY_REPOSITORY_TYPE);
        Class<?> resetPortType = loadClass(ARENA_RESET_PORT_TYPE);
        Class<?> serviceType = loadClass(ARENA_REGISTRY_SERVICE_TYPE);

        RepositoryDouble repository = new RepositoryDouble(arenaIdType);
        return newHarness(repository, arenaIdType, arenaCuboidType, arenaSpawnPointType, arenaDefinitionType, arenaReservationType,
                arenaReservationIdType, repositoryType, resetPortType, serviceType);
    }

    private static ServiceHarness newHarness(RepositoryDouble repository) {
        Class<?> arenaIdType = loadClass(ARENA_ID_TYPE);
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);
        Class<?> arenaReservationType = loadClass(ARENA_RESERVATION_TYPE);
        Class<?> arenaReservationIdType = loadClass(ARENA_RESERVATION_ID_TYPE);
        Class<?> repositoryType = loadClass(ARENA_REGISTRY_REPOSITORY_TYPE);
        Class<?> resetPortType = loadClass(ARENA_RESET_PORT_TYPE);
        Class<?> serviceType = loadClass(ARENA_REGISTRY_SERVICE_TYPE);
        return newHarness(repository, arenaIdType, arenaCuboidType, arenaSpawnPointType, arenaDefinitionType, arenaReservationType,
                arenaReservationIdType, repositoryType, resetPortType, serviceType);
    }

    private static ServiceHarness newHarness(
            RepositoryDouble repository,
            Class<?> arenaIdType,
            Class<?> arenaCuboidType,
            Class<?> arenaSpawnPointType,
            Class<?> arenaDefinitionType,
            Class<?> arenaReservationType,
            Class<?> arenaReservationIdType,
            Class<?> repositoryType,
            Class<?> resetPortType,
            Class<?> serviceType) {
        ResetPortDouble resetPort = new ResetPortDouble();
        Object repositoryProxy = Proxy.newProxyInstance(
                repositoryType.getClassLoader(), new Class<?>[] {repositoryType}, repository);
        Object resetProxy = Proxy.newProxyInstance(
                resetPortType.getClassLoader(), new Class<?>[] {resetPortType}, resetPort);

        try {
            Object service = serviceType.getDeclaredConstructor(repositoryType, resetPortType)
                    .newInstance(repositoryProxy, resetProxy);
            return new ServiceHarness(
                    service,
                    arenaIdType,
                    arenaCuboidType,
                    arenaSpawnPointType,
                    arenaDefinitionType,
                    arenaReservationType,
                    arenaReservationIdType,
                    serviceType.getMethod("register", arenaDefinitionType),
                    serviceType.getMethod("arenas"),
                    serviceType.getMethod("reserve", arenaIdType, String.class),
                    serviceType.getMethod("release", arenaReservationIdType),
                    repository,
                    resetPort);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build arena service harness", exception);
        }
    }

    private static Map<String, Object> cuboidValues(String worldKey) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", worldKey);
        values.put("minX", 0);
        values.put("minY", 64);
        values.put("minZ", 0);
        values.put("maxX", 20);
        values.put("maxY", 90);
        values.put("maxZ", 20);
        return values;
    }

    private static Map<String, Object> spawnValues(String worldKey, double x, double z) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", worldKey);
        values.put("x", x);
        values.put("y", 70.0d);
        values.put("z", z);
        values.put("yaw", 0.0f);
        values.put("pitch", 0.0f);
        return values;
    }

    private record ServiceHarness(
            Object service,
            Class<?> arenaIdType,
            Class<?> arenaCuboidType,
            Class<?> arenaSpawnPointType,
            Class<?> arenaDefinitionType,
            Class<?> arenaReservationType,
            Class<?> arenaReservationIdType,
            Method registerMethod,
            Method listMethod,
            Method reserveMethod,
            Method releaseMethod,
            RepositoryDouble repository,
            ResetPortDouble resetPort) {

        Object arenaId(String value) {
            return instantiateRecord(arenaIdType, Map.of("value", value));
        }

        Object reservationId(UUID value) {
            return instantiateRecord(arenaReservationIdType, Map.of("value", value));
        }

        Object arenaDefinition(String id, String displayName, boolean enabled) {
            String worldKey = "minecraft:" + id;
            Object bounds = instantiateRecord(arenaCuboidType, cuboidValues(worldKey));
            Object spawnOne = instantiateRecord(arenaSpawnPointType, spawnValues(worldKey, 2.5d, 2.5d));
            Object spawnTwo = instantiateRecord(arenaSpawnPointType, spawnValues(worldKey, 17.5d, 17.5d));

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", arenaId(id));
            values.put("displayName", displayName);
            values.put("bounds", bounds);
            values.put("spawnOne", spawnOne);
            values.put("spawnTwo", spawnTwo);
            values.put("enabled", enabled);
            return instantiateRecord(arenaDefinitionType, values);
        }

        void register(Object arenaDefinition) {
            try {
                registerMethod.invoke(service, arenaDefinition);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Could not register arena", exception);
            }
        }

        List<?> listArenas() {
            try {
                return List.copyOf((Collection<?>) listMethod.invoke(service));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Could not list arenas", exception);
            }
        }

        Object reserve(String arenaId, String ownerKey) {
            try {
                return reserveMethod.invoke(service, arenaId(arenaId), ownerKey);
            } catch (IllegalAccessException exception) {
                throw new AssertionError("Could not reserve arena", exception);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new AssertionError("Could not reserve arena", exception);
            }
        }

        void release(Object reservationId) {
            try {
                releaseMethod.invoke(service, reservationId);
            } catch (IllegalAccessException exception) {
                throw new AssertionError("Could not release arena reservation", exception);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new AssertionError("Could not release arena reservation", exception);
            }
        }
    }

    private static final class RepositoryDouble implements InvocationHandler {

        private final Class<?> arenaIdType;
        private final Map<Object, Object> definitions = new ConcurrentHashMap<>();

        private RepositoryDouble(Class<?> arenaIdType) {
            this.arenaIdType = arenaIdType;
        }

        private Optional<Object> find(Object arenaId) {
            return Optional.ofNullable(definitions.get(arenaId));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "find" -> find(arguments[0]);
                case "create" -> {
                    Object arenaDefinition = arguments[0];
                    yield definitions.putIfAbsent(recordComponentValue(arenaDefinition, "id"), arenaDefinition) == null;
                }
                case "findAll" -> List.copyOf(definitions.values());
                default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
            };
        }
    }

    private static final class ResetPortDouble implements InvocationHandler {

        private final List<Object> resetCalls = new ArrayList<>();
        private RuntimeException throwOnReset;
        private CountDownLatch resetStarted;
        private CountDownLatch allowResetToFinish;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (!method.getName().equals("reset")) {
                throw new UnsupportedOperationException("Unexpected reset port method: " + method.getName());
            }
            resetCalls.add(arguments[0]);
            if (resetStarted != null) {
                resetStarted.countDown();
            }
            if (allowResetToFinish != null) {
                try {
                    if (!allowResetToFinish.await(1, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to finish reset");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting to finish reset", exception);
                }
            }
            if (throwOnReset != null) {
                throw throwOnReset;
            }
            return null;
        }
    }
}
