package io.github.xreatlabz.revprac.adapters.storage;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateNoArgs;
import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.invoke;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InMemoryPlayerSessionRepositoryTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String PLAYER_CONTEXT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerContext";
    private static final String PLAYER_SAFETY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot";
    private static final String LOCATION_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.LocationSnapshot";
    private static final String INVENTORY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.InventorySnapshot";
    private static final String PLAYER_STATUS_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot";
    private static final String PLAYER_SESSION_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSession";
    private static final String PLAYER_SESSION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository";
    private static final String IN_MEMORY_PLAYER_SESSION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository";

    @Test
    void repositoryStoresFindsReplacesAndDeletesSessionsByPlayerId() {
        RepositoryHarness harness = newHarness();
        Object playerId = harness.playerId("stored-player");
        Object lobbySession = harness.session(playerId, "LOBBY", null);
        Object managedSnapshot = harness.snapshot("managed");
        Object managedSession = harness.session(playerId, "MATCH", managedSnapshot);

        invoke(harness.save, harness.repository, lobbySession);
        Optional<?> storedLobby = (Optional<?>) invoke(harness.find, harness.repository, playerId);
        assertTrue(storedLobby.isPresent(), "Repository should find the saved lobby session");
        assertEquals("LOBBY", enumName(recordComponentValue(storedLobby.get(), "context")));

        invoke(harness.save, harness.repository, managedSession);
        Optional<?> storedManaged = (Optional<?>) invoke(harness.find, harness.repository, playerId);
        assertTrue(storedManaged.isPresent(), "Repository should replace the session for the same player");
        assertEquals("MATCH", enumName(recordComponentValue(storedManaged.get(), "context")));
        assertEquals(managedSnapshot, recordComponentValue(storedManaged.get(), "returnSnapshot"));

        invoke(harness.delete, harness.repository, playerId);
        assertTrue(((Optional<?>) invoke(harness.find, harness.repository, playerId)).isEmpty(), "Delete should remove the active session");
    }

    @Test
    void repositoryFindAllReturnsTheCurrentStoredSessions() {
        RepositoryHarness harness = newHarness();
        Object firstPlayer = harness.playerId("first-player");
        Object secondPlayer = harness.playerId("second-player");

        invoke(harness.save, harness.repository, harness.session(firstPlayer, "LOBBY", null));
        invoke(harness.save, harness.repository, harness.session(secondPlayer, "QUEUE", harness.snapshot("queue")));

        List<?> allSessions = List.copyOf((java.util.Collection<?>) invoke(harness.findAll, harness.repository));

        assertEquals(2, allSessions.size(), "findAll should expose every stored session");
        List<String> contexts = allSessions.stream()
                .map(session -> enumName(recordComponentValue(session, "context")))
                .sorted()
                .toList();
        assertEquals(List.of("LOBBY", "QUEUE"), contexts);
    }

    private static RepositoryHarness newHarness() {
        Class<?> playerIdType = loadClass(PLAYER_ID_TYPE);
        Class<?> playerContextType = loadClass(PLAYER_CONTEXT_TYPE);
        Class<?> playerSafetySnapshotType = loadClass(PLAYER_SAFETY_SNAPSHOT_TYPE);
        Class<?> playerSessionType = loadClass(PLAYER_SESSION_TYPE);
        Class<?> repositoryType = loadClass(PLAYER_SESSION_REPOSITORY_TYPE);
        Object repository = instantiateNoArgs(loadClass(IN_MEMORY_PLAYER_SESSION_REPOSITORY_TYPE));
        try {
            return new RepositoryHarness(
                    repository,
                    playerIdType,
                    playerContextType,
                    playerSafetySnapshotType,
                    playerSessionType,
                    repositoryType.getMethod("save", playerSessionType),
                    repositoryType.getMethod("find", playerIdType),
                    repositoryType.getMethod("delete", playerIdType),
                    repositoryType.getMethod("findAll"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build repository harness", exception);
        }
    }

    private static String enumName(Object enumValue) {
        return ((Enum<?>) enumValue).name();
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

    private record RepositoryHarness(
            Object repository,
            Class<?> playerIdType,
            Class<?> playerContextType,
            Class<?> playerSafetySnapshotType,
            Class<?> playerSessionType,
            Method save,
            Method find,
            Method delete,
            Method findAll) {

        Object playerId(String seed) {
            return instantiateRecord(playerIdType, Map.of("value", UUID.nameUUIDFromBytes(seed.getBytes())));
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

        Object session(Object playerId, String contextName, Object snapshot) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("playerId", playerId);
            values.put("context", enumConstant(playerContextType, contextName));
            values.put("returnSnapshot", snapshot);
            return instantiateRecord(playerSessionType, values);
        }
    }
}
