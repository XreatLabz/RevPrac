package io.github.xreatlabz.revprac.domain.arenas;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class ArenaDefinitionContractTest {

    private static final String DOMAIN_ARENAS_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/arenas";
    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String ARENA_CUBOID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid";
    private static final String ARENA_SPAWN_POINT_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint";
    private static final String ARENA_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition";
    private static final String ARENA_RESERVATION_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId";
    private static final String ARENA_RESERVATION_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservation";

    @Test
    void arenaContractsUseImmutableRecordsAndNormalizeIds() throws ReflectiveOperationException {
        Class<?> arenaIdType = loadClass(ARENA_ID_TYPE);
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);
        Class<?> arenaReservationIdType = loadClass(ARENA_RESERVATION_ID_TYPE);
        Class<?> arenaReservationType = loadClass(ARENA_RESERVATION_TYPE);

        assertTrue(arenaIdType.isRecord(), "ArenaId should be a record");
        assertTrue(arenaCuboidType.isRecord(), "ArenaCuboid should be a record");
        assertTrue(arenaSpawnPointType.isRecord(), "ArenaSpawnPoint should be a record");
        assertTrue(arenaDefinitionType.isRecord(), "ArenaDefinition should be a record");
        assertTrue(arenaReservationIdType.isRecord(), "ArenaReservationId should be a record");
        assertTrue(arenaReservationType.isRecord(), "ArenaReservation should be a record");

        Object arenaId = instantiateRecord(arenaIdType, Map.of("value", "  NoDebuff_Main  "));
        assertEquals("nodebuff_main", recordComponentValue(arenaId, "value"));

        Constructor<?> arenaIdConstructor = arenaIdType.getDeclaredConstructor(String.class);
        arenaIdConstructor.setAccessible(true);
        assertIllegalArgument(() -> arenaIdConstructor.newInstance(" "), "ArenaId should reject blank ids");
        assertIllegalArgument(() -> arenaIdConstructor.newInstance("bad id"), "ArenaId should reject spaces");
        assertIllegalArgument(() -> arenaIdConstructor.newInstance("bad.id"), "ArenaId should reject punctuation outside the allowed regex");
        assertIllegalArgument(
                () -> arenaIdConstructor.newInstance("-starts-with-dash"),
                "ArenaId should require the first character to be alphanumeric");
    }

    @Test
    void arenaScalarsRejectImpossibleStateAndRequireContainedSpawns() throws ReflectiveOperationException {
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);

        Constructor<?> cuboidConstructor =
                arenaCuboidType.getDeclaredConstructor(String.class, int.class, int.class, int.class, int.class, int.class, int.class);
        cuboidConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> cuboidConstructor.newInstance(" ", 0, 64, 0, 10, 70, 10),
                "ArenaCuboid should reject blank world keys");
        assertIllegalArgument(
                () -> cuboidConstructor.newInstance("world", 10, 64, 0, 9, 70, 10),
                "ArenaCuboid should reject inverted X bounds");
        assertIllegalArgument(
                () -> cuboidConstructor.newInstance("world", 0, 70, 0, 9, 64, 10),
                "ArenaCuboid should reject inverted Y bounds");
        assertIllegalArgument(
                () -> cuboidConstructor.newInstance("world", 0, 64, 10, 9, 70, 9),
                "ArenaCuboid should reject inverted Z bounds");

        Constructor<?> spawnConstructor =
                arenaSpawnPointType.getDeclaredConstructor(String.class, double.class, double.class, double.class, float.class, float.class);
        spawnConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> spawnConstructor.newInstance("world", Double.NaN, 65.0d, 5.0d, 0.0f, 0.0f),
                "ArenaSpawnPoint should reject non-finite X coordinates");
        assertIllegalArgument(
                () -> spawnConstructor.newInstance("world", 5.0d, Double.POSITIVE_INFINITY, 5.0d, 0.0f, 0.0f),
                "ArenaSpawnPoint should reject non-finite Y coordinates");
        assertIllegalArgument(
                () -> spawnConstructor.newInstance("world", 5.0d, 65.0d, 5.0d, Float.NaN, 0.0f),
                "ArenaSpawnPoint should reject non-finite yaw");

        Object cuboid = instantiateRecord(arenaCuboidType, cuboidValues("world", 0, 64, 0, 10, 70, 10));
        Object spawnOne = instantiateRecord(arenaSpawnPointType, spawnValues("world", 1.9d, 65.0d, 1.1d, 0.0f, 0.0f));
        Object spawnTwo = instantiateRecord(arenaSpawnPointType, spawnValues("world", 9.9d, 65.0d, 9.1d, 180.0f, 0.0f));
        Object wrongWorld = instantiateRecord(arenaSpawnPointType, spawnValues("nether", 5.0d, 65.0d, 5.0d, 0.0f, 0.0f));
        Object outsideArena = instantiateRecord(arenaSpawnPointType, spawnValues("world", 11.0d, 65.0d, 5.0d, 0.0f, 0.0f));

        Constructor<?> definitionConstructor =
                arenaDefinitionType.getDeclaredConstructor(
                        loadClass(ARENA_ID_TYPE), String.class, arenaCuboidType, arenaSpawnPointType, arenaSpawnPointType, boolean.class);
        definitionConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> definitionConstructor.newInstance(
                        instantiateRecord(loadClass(ARENA_ID_TYPE), Map.of("value", "sumo")),
                        " ",
                        cuboid,
                        spawnOne,
                        spawnTwo,
                        true),
                "ArenaDefinition should reject blank display names");
        assertIllegalArgument(
                () -> definitionConstructor.newInstance(
                        instantiateRecord(loadClass(ARENA_ID_TYPE), Map.of("value", "sumo")),
                        "Sumo",
                        cuboid,
                        wrongWorld,
                        spawnTwo,
                        true),
                "ArenaDefinition should reject mismatched spawn worlds");
        assertIllegalArgument(
                () -> definitionConstructor.newInstance(
                        instantiateRecord(loadClass(ARENA_ID_TYPE), Map.of("value", "sumo")),
                        "Sumo",
                        cuboid,
                        outsideArena,
                        spawnTwo,
                        true),
                "ArenaDefinition should reject spawns outside bounds");
    }

    @Test
    void arenaDefinitionStoresNormalizedStateAndReservationMetadata() {
        Class<?> arenaIdType = loadClass(ARENA_ID_TYPE);
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);
        Class<?> arenaReservationIdType = loadClass(ARENA_RESERVATION_ID_TYPE);
        Class<?> arenaReservationType = loadClass(ARENA_RESERVATION_TYPE);

        Object arenaId = instantiateRecord(arenaIdType, Map.of("value", "bridge"));
        Object cuboid = instantiateRecord(arenaCuboidType, cuboidValues("world", 0, 64, 0, 10, 70, 10));
        Object spawnOne = instantiateRecord(arenaSpawnPointType, spawnValues("world", 1.5d, 65.0d, 1.5d, 0.0f, 0.0f));
        Object spawnTwo = instantiateRecord(arenaSpawnPointType, spawnValues("world", 8.5d, 65.0d, 8.5d, 180.0f, 0.0f));
        Object definition = instantiateRecord(
                arenaDefinitionType,
                arenaDefinitionValues(arenaId, "Bridge", cuboid, spawnOne, spawnTwo, true));

        assertEquals("Bridge", recordComponentValue(definition, "displayName"));
        assertEquals(arenaId, recordComponentValue(definition, "id"));
        assertEquals(cuboid, recordComponentValue(definition, "bounds"));

        UUID reservationUuid = UUID.randomUUID();
        Object reservationId = instantiateRecord(arenaReservationIdType, Map.of("value", reservationUuid));
        Object reservation = instantiateRecord(
                arenaReservationType,
                Map.of("reservationId", reservationId, "arenaId", arenaId, "ownerKey", "match:queue-1"));

        assertEquals(reservationId, recordComponentValue(reservation, "reservationId"));
        assertEquals(arenaId, recordComponentValue(reservation, "arenaId"));
        assertEquals("match:queue-1", recordComponentValue(reservation, "ownerKey"));
    }

    @Test
    void domainArenaSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        Path domainDirectory = Path.of(DOMAIN_ARENAS_DIR);

        assertTrue(Files.isDirectory(domainDirectory), "Expected arena domain directory to exist: " + domainDirectory);

        try (Stream<Path> sources = Files.walk(domainDirectory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected arena domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static Map<String, Object> cuboidValues(
            String worldKey, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", worldKey);
        values.put("minX", minX);
        values.put("minY", minY);
        values.put("minZ", minZ);
        values.put("maxX", maxX);
        values.put("maxY", maxY);
        values.put("maxZ", maxZ);
        return values;
    }

    private static Map<String, Object> spawnValues(
            String worldKey, double x, double y, double z, float yaw, float pitch) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", worldKey);
        values.put("x", x);
        values.put("y", y);
        values.put("z", z);
        values.put("yaw", yaw);
        values.put("pitch", pitch);
        return values;
    }

    private static Map<String, Object> arenaDefinitionValues(
            Object id, String displayName, Object bounds, Object spawnOne, Object spawnTwo, boolean enabled) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("displayName", displayName);
        values.put("bounds", bounds);
        values.put("spawnOne", spawnOne);
        values.put("spawnTwo", spawnTwo);
        values.put("enabled", enabled);
        return values;
    }

    private static void assertIllegalArgument(ThrowingOperation operation, String message) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, operation::run, message);
        assertTrue(exception.getCause() instanceof IllegalArgumentException, message);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ReflectiveOperationException;
    }
}
