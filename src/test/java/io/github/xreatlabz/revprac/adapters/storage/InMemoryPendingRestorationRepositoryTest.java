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

final class InMemoryPendingRestorationRepositoryTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String TRANSITION_REASON_TYPE = "io.github.xreatlabz.revprac.domain.players.TransitionReason";
    private static final String PLAYER_SAFETY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot";
    private static final String LOCATION_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.LocationSnapshot";
    private static final String INVENTORY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.InventorySnapshot";
    private static final String PLAYER_STATUS_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot";
    private static final String PENDING_RESTORATION_TYPE = "io.github.xreatlabz.revprac.domain.players.PendingRestoration";
    private static final String PENDING_RESTORATION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository";
    private static final String IN_MEMORY_PENDING_RESTORATION_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository";

    @Test
    void repositoryStoresFindsReplacesAndDeletesRestorationsByPlayerId() {
        RepositoryHarness harness = newHarness();
        Object playerId = harness.playerId("stored-pending");
        Object firstPending = harness.pendingRestoration(playerId, harness.snapshot("first"), "QUIT");
        Object secondPending = harness.pendingRestoration(playerId, harness.snapshot("second"), "PLUGIN_DISABLE");

        invoke(harness.save, harness.repository, firstPending);
        Optional<?> storedFirst = (Optional<?>) invoke(harness.find, harness.repository, playerId);
        assertTrue(storedFirst.isPresent(), "Repository should find the saved pending restoration");
        assertEquals("QUIT", enumName(recordComponentValue(storedFirst.get(), "reason")));

        invoke(harness.save, harness.repository, secondPending);
        Optional<?> storedSecond = (Optional<?>) invoke(harness.find, harness.repository, playerId);
        assertTrue(storedSecond.isPresent(), "Repository should replace the restoration for the same player");
        assertEquals("PLUGIN_DISABLE", enumName(recordComponentValue(storedSecond.get(), "reason")));
        assertEquals(recordComponentValue(secondPending, "snapshot"), recordComponentValue(storedSecond.get(), "snapshot"));

        invoke(harness.delete, harness.repository, playerId);
        assertTrue(((Optional<?>) invoke(harness.find, harness.repository, playerId)).isEmpty(), "Delete should remove the pending restoration");
    }

    @Test
    void repositoryFindAllReturnsTheCurrentStoredPendingRestorations() {
        RepositoryHarness harness = newHarness();
        Object firstPlayer = harness.playerId("first-pending");
        Object secondPlayer = harness.playerId("second-pending");

        invoke(harness.save, harness.repository, harness.pendingRestoration(firstPlayer, harness.snapshot("first"), "QUIT"));
        invoke(
                harness.save,
                harness.repository,
                harness.pendingRestoration(secondPlayer, harness.snapshot("second"), "PLUGIN_DISABLE"));

        List<?> allRestorations = List.copyOf((java.util.Collection<?>) invoke(harness.findAll, harness.repository));

        assertEquals(2, allRestorations.size(), "findAll should expose every stored pending restoration");
        List<String> reasons = allRestorations.stream()
                .map(restoration -> enumName(recordComponentValue(restoration, "reason")))
                .sorted()
                .toList();
        assertEquals(List.of("PLUGIN_DISABLE", "QUIT"), reasons);
    }

    private static RepositoryHarness newHarness() {
        Class<?> playerIdType = loadClass(PLAYER_ID_TYPE);
        Class<?> transitionReasonType = loadClass(TRANSITION_REASON_TYPE);
        Class<?> playerSafetySnapshotType = loadClass(PLAYER_SAFETY_SNAPSHOT_TYPE);
        Class<?> pendingRestorationType = loadClass(PENDING_RESTORATION_TYPE);
        Class<?> repositoryType = loadClass(PENDING_RESTORATION_REPOSITORY_TYPE);
        Object repository = instantiateNoArgs(loadClass(IN_MEMORY_PENDING_RESTORATION_REPOSITORY_TYPE));
        try {
            return new RepositoryHarness(
                    repository,
                    playerIdType,
                    transitionReasonType,
                    playerSafetySnapshotType,
                    pendingRestorationType,
                    repositoryType.getMethod("save", pendingRestorationType),
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
            Class<?> transitionReasonType,
            Class<?> playerSafetySnapshotType,
            Class<?> pendingRestorationType,
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

        Object pendingRestoration(Object playerId, Object snapshot, String reasonName) {
            return instantiateRecord(pendingRestorationType, Map.of(
                    "playerId", playerId,
                    "snapshot", snapshot,
                    "reason", enumConstant(transitionReasonType, reasonName)));
        }
    }
}
