package io.github.xreatlabz.revprac.domain.players;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerSessionTransitionPolicyTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String PLAYER_CONTEXT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerContext";
    private static final String LOCATION_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.LocationSnapshot";
    private static final String INVENTORY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.InventorySnapshot";
    private static final String PLAYER_STATUS_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot";
    private static final String PLAYER_SAFETY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot";
    private static final String PLAYER_SESSION_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSession";
    private static final String PLAYER_SESSION_TRANSITION_POLICY_TYPE =
            "io.github.xreatlabz.revprac.domain.players.PlayerSessionTransitionPolicy";

    @Test
    void transitionPolicyAllowsDocumentedTransitionsAndRejectsUndeclaredOnes() throws ReflectiveOperationException {
        Class<?> playerContextType = loadClass(PLAYER_CONTEXT_TYPE);
        Class<?> transitionPolicyType = loadClass(PLAYER_SESSION_TRANSITION_POLICY_TYPE);
        assertTrue(transitionPolicyType.isEnum(), "PlayerSessionTransitionPolicy should stay enum-only in the domain");
        Method isAllowed = transitionPolicyType.getMethod("isAllowed", playerContextType, playerContextType);

        Object lobby = enumConstant(playerContextType, "LOBBY");
        Object queue = enumConstant(playerContextType, "QUEUE");
        Object match = enumConstant(playerContextType, "MATCH");
        Object spectator = enumConstant(playerContextType, "SPECTATOR");
        Object editor = enumConstant(playerContextType, "EDITOR");

        assertTrue((boolean) isAllowed.invoke(null, lobby, queue), "LOBBY -> QUEUE should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, lobby, match), "LOBBY -> MATCH should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, lobby, spectator), "LOBBY -> SPECTATOR should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, lobby, editor), "LOBBY -> EDITOR should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, queue, match), "QUEUE -> MATCH should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, match, spectator), "Managed -> managed should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, spectator, editor), "Managed -> managed should be allowed");
        assertTrue((boolean) isAllowed.invoke(null, editor, lobby), "Managed -> LOBBY should be allowed");

        assertFalse((boolean) isAllowed.invoke(null, queue, queue), "Same-context transition should be rejected");
        assertFalse((boolean) isAllowed.invoke(null, lobby, lobby), "Same-context transition should be rejected");
        assertFalse((boolean) isAllowed.invoke(null, spectator, spectator), "Same-context transition should be rejected");
    }

    @Test
    void managedSessionsRequireReturnSnapshotAndLobbySessionsDoNot() throws ReflectiveOperationException {
        Class<?> playerIdType = loadClass(PLAYER_ID_TYPE);
        Class<?> playerContextType = loadClass(PLAYER_CONTEXT_TYPE);
        Class<?> locationSnapshotType = loadClass(LOCATION_SNAPSHOT_TYPE);
        Class<?> inventorySnapshotType = loadClass(INVENTORY_SNAPSHOT_TYPE);
        Class<?> playerStatusSnapshotType = loadClass(PLAYER_STATUS_SNAPSHOT_TYPE);
        Class<?> playerSafetySnapshotType = loadClass(PLAYER_SAFETY_SNAPSHOT_TYPE);
        Class<?> playerSessionType = loadClass(PLAYER_SESSION_TYPE);

        Object playerId = instantiateRecord(playerIdType, Map.of("value", UUID.randomUUID()));
        Object lobby = enumConstant(playerContextType, "LOBBY");
        Object queue = enumConstant(playerContextType, "QUEUE");
        Object returnSnapshot = instantiateRecord(playerSafetySnapshotType, Map.of(
                "location", instantiateRecord(locationSnapshotType, locationValues()),
                "inventory", instantiateRecord(inventorySnapshotType, inventoryValues()),
                "status", instantiateRecord(playerStatusSnapshotType, statusValues())));

        Constructor<?> constructor = playerSessionType.getDeclaredConstructor(playerIdType, playerContextType, playerSafetySnapshotType);
        constructor.setAccessible(true);

        Object lobbySession = constructor.newInstance(playerId, lobby, null);
        assertTrue(playerSessionType.isRecord(), "PlayerSession should be a record");
        assertTrue(lobbySession.getClass().isRecord(), "Lobby session should be created without a return snapshot");

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> constructor.newInstance(playerId, queue, null),
                "Managed sessions should reject a missing return snapshot");
        assertTrue(exception.getCause() instanceof IllegalArgumentException, "Managed session failure should be an IllegalArgumentException");

        InvocationTargetException staleLobbySnapshotException = assertThrows(
                InvocationTargetException.class,
                () -> constructor.newInstance(playerId, lobby, returnSnapshot),
                "Lobby sessions should reject a stale return snapshot");
        assertTrue(
                staleLobbySnapshotException.getCause() instanceof IllegalArgumentException,
                "Lobby session failure should be an IllegalArgumentException");

        Object managedSession = constructor.newInstance(playerId, queue, returnSnapshot);
        assertTrue(managedSession.getClass().isRecord(), "Managed session should accept a return snapshot");
    }

    private static Map<String, Object> locationValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldKey", "minecraft:world");
        values.put("x", 10.5d);
        values.put("y", 64.0d);
        values.put("z", -14.25d);
        values.put("yaw", 180.0f);
        values.put("pitch", 12.5f);
        return values;
    }

    private static Map<String, Object> inventoryValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storage", List.of("sword", "bow"));
        values.put("armor", List.of("helmet", "chestplate", "leggings", "boots"));
        values.put("extra", List.of("offhand"));
        values.put("enderChest", List.of("totem"));
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

    private static Object enumConstant(Class<?> enumType, String name) {
        Object[] constants = enumType.getEnumConstants();
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new AssertionError("Expected enum constant " + name + " on " + enumType.getName());
    }
}
