package io.github.xreatlabz.revprac.adapters.storage;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateNoArgs;
import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class InMemoryArenaRegistryRepositoryTest {

    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String ARENA_CUBOID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid";
    private static final String ARENA_SPAWN_POINT_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint";
    private static final String ARENA_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition";
    private static final String ARENA_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.arenas.ArenaRegistryRepository";
    private static final String IN_MEMORY_ARENA_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository";

    @Test
    void repositoryStoresFindsAndReplacesArenasById() {
        RepositoryHarness harness = newHarness();
        Object original = harness.arenaDefinition("bridge", "Bridge", true);
        Object replacement = harness.arenaDefinition("bridge", "Bridge II", false);

        harness.save(original);
        Optional<?> stored = harness.find("bridge");
        assertTrue(stored.isPresent(), "Repository should find the stored arena");
        assertEquals("Bridge", recordComponentValue(stored.get(), "displayName"));

        harness.save(replacement);
        Optional<?> updated = harness.find("bridge");
        assertTrue(updated.isPresent(), "Repository should replace existing arena for the same id");
        assertEquals("Bridge II", recordComponentValue(updated.get(), "displayName"));
        assertEquals(false, recordComponentValue(updated.get(), "enabled"));
    }

    @Test
    void repositoryFindAllReturnsImmutableSnapshots() {
        RepositoryHarness harness = newHarness();
        harness.save(harness.arenaDefinition("bridge", "Bridge", true));

        Collection<?> firstSnapshot = harness.findAll();
        assertEquals(1, firstSnapshot.size(), "Initial snapshot should contain the first arena");
        assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);

        harness.save(harness.arenaDefinition("courtyard", "Courtyard", true));

        assertEquals(1, firstSnapshot.size(), "Earlier snapshot should not change after later saves");
        assertEquals(List.of("bridge", "courtyard"), harness.findAll().stream()
                .map(arena -> recordComponentValue(recordComponentValue(arena, "id"), "value"))
                .sorted()
                .toList());
    }

    private static RepositoryHarness newHarness() {
        Class<?> arenaIdType = loadClass(ARENA_ID_TYPE);
        Class<?> arenaCuboidType = loadClass(ARENA_CUBOID_TYPE);
        Class<?> arenaSpawnPointType = loadClass(ARENA_SPAWN_POINT_TYPE);
        Class<?> arenaDefinitionType = loadClass(ARENA_DEFINITION_TYPE);
        Class<?> repositoryType = loadClass(ARENA_REGISTRY_REPOSITORY_TYPE);
        Object repository = instantiateNoArgs(loadClass(IN_MEMORY_ARENA_REGISTRY_REPOSITORY_TYPE));

        try {
            return new RepositoryHarness(
                    repository,
                    arenaIdType,
                    arenaCuboidType,
                    arenaSpawnPointType,
                    arenaDefinitionType,
                    repositoryType.getMethod("save", arenaDefinitionType),
                    repositoryType.getMethod("find", arenaIdType),
                    repositoryType.getMethod("findAll"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build arena repository harness", exception);
        }
    }

    private record RepositoryHarness(
            Object repository,
            Class<?> arenaIdType,
            Class<?> arenaCuboidType,
            Class<?> arenaSpawnPointType,
            Class<?> arenaDefinitionType,
            Method saveMethod,
            Method findMethod,
            Method findAllMethod) {

        Object arenaDefinition(String id, String displayName, boolean enabled) {
            String worldKey = "minecraft:" + id;
            Object bounds = instantiateRecord(arenaCuboidType, cuboidValues(worldKey));
            Object spawnOne = instantiateRecord(arenaSpawnPointType, spawnValues(worldKey, 2.0d, 2.0d));
            Object spawnTwo = instantiateRecord(arenaSpawnPointType, spawnValues(worldKey, 18.0d, 18.0d));

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", instantiateRecord(arenaIdType, Map.of("value", id)));
            values.put("displayName", displayName);
            values.put("bounds", bounds);
            values.put("spawnOne", spawnOne);
            values.put("spawnTwo", spawnTwo);
            values.put("enabled", enabled);
            return instantiateRecord(arenaDefinitionType, values);
        }

        void save(Object arenaDefinition) {
            try {
                saveMethod.invoke(repository, arenaDefinition);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not save arena definition", exception);
            }
        }

        Optional<?> find(String id) {
            try {
                Object arenaId = instantiateRecord(arenaIdType, Map.of("value", id));
                return (Optional<?>) findMethod.invoke(repository, arenaId);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not find arena definition", exception);
            }
        }

        Collection<?> findAll() {
            try {
                return (Collection<?>) findAllMethod.invoke(repository);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not list arena definitions", exception);
            }
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
}
