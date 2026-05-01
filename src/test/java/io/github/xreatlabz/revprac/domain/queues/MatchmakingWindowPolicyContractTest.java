package io.github.xreatlabz.revprac.domain.queues;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MatchmakingWindowPolicyContractTest {

    private static final String QUEUE_MODE_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueMode";
    private static final String QUEUE_KEY_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueKey";
    private static final String QUEUE_TICKET_ID_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicketId";
    private static final String QUEUE_TICKET_STATE_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicketState";
    private static final String QUEUE_TICKET_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicket";
    private static final String MATCHMAKING_WINDOW_POLICY_TYPE =
            "io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy";
    private static final String WINDOW_STEP_TYPE =
            "io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy$WindowStep";

    @Test
    void policyReturnsDocumentedWindowsAndValidatesRankedCompatibility() throws ReflectiveOperationException {
        Class<?> queueModeType = loadClass(QUEUE_MODE_TYPE);
        Class<?> queueKeyType = loadClass(QUEUE_KEY_TYPE);
        Class<?> queueTicketIdType = loadClass(QUEUE_TICKET_ID_TYPE);
        Class<?> queueTicketStateType = loadClass(QUEUE_TICKET_STATE_TYPE);
        Class<?> queueTicketType = loadClass(QUEUE_TICKET_TYPE);
        Class<?> policyType = loadClass(MATCHMAKING_WINDOW_POLICY_TYPE);
        Class<?> windowStepType = loadClass(WINDOW_STEP_TYPE);

        Object rankedKey = instantiateRecord(queueKeyType, Map.of(
                "mode", enumConstant(queueModeType, "RANKED"),
                "kitId", new KitId("nodebuff")));
        Object searching = enumConstant(queueTicketStateType, "SEARCHING");
        Object policy = policyType.getMethod("defaults").invoke(null);

        assertEquals(50, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 0L));
        assertEquals(100, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 10L));
        assertEquals(150, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 20L));
        assertEquals(250, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 30L));
        assertEquals(400, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 45L));
        assertEquals(400, policyType.getMethod("windowForWaitSeconds", long.class).invoke(policy, 90L));

        Object anchor = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("d7d441b6-b31d-4d4a-9db6-7dc56b1d88a4"))),
                "playerId", new PlayerId(UUID.fromString("23d3f664-915d-4444-8d3f-4f1bfd4afeee")),
                "key", rankedKey,
                "joinedAtTick", 0L,
                "searchRating", 1200,
                "state", searching));
        Object closeCandidate = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("7200d9bf-8162-4d24-abf2-6d4e6cf2c5e0"))),
                "playerId", new PlayerId(UUID.fromString("463bb6d8-5c92-4ca1-a6e2-f11c3a0b7238")),
                "key", rankedKey,
                "joinedAtTick", 80L,
                "searchRating", 1260,
                "state", searching));
        Object farCandidate = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("6ad1f57c-bf56-4748-8ca8-e78f8f4f9aa4"))),
                "playerId", new PlayerId(UUID.fromString("58f9cbfc-5051-460c-84a2-8f406e7f6a66")),
                "key", rankedKey,
                "joinedAtTick", 80L,
                "searchRating", 1405,
                "state", searching));

        assertTrue((Boolean) policyType
                .getMethod("isCompatible", queueTicketType, queueTicketType, long.class, long.class)
                .invoke(policy, anchor, closeCandidate, 200L, 20L));
        assertFalse((Boolean) policyType
                .getMethod("isCompatible", queueTicketType, queueTicketType, long.class, long.class)
                .invoke(policy, anchor, farCandidate, 200L, 20L));

        Object widerWindowCandidate = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("95117712-c276-41f3-b11a-77199fa4b891"))),
                "playerId", new PlayerId(UUID.fromString("1e69d54d-c843-4b12-9db5-df4eb9c0c884")),
                "key", rankedKey,
                "joinedAtTick", 700L,
                "searchRating", 1580,
                "state", searching));
        assertTrue((Boolean) policyType
                .getMethod("isCompatible", queueTicketType, queueTicketType, long.class, long.class)
                .invoke(policy, anchor, widerWindowCandidate, 900L, 20L));

        List<?> unorderedSteps = List.of(
                instantiateRecord(windowStepType, Map.of("waitSeconds", 10L, "ratingWindow", 100)),
                instantiateRecord(windowStepType, Map.of("waitSeconds", 0L, "ratingWindow", 50)));
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> policyType.getDeclaredConstructor(List.class).newInstance(unorderedSteps));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void unrankedCompatibilityIgnoresRatingForSameQueueKey() throws ReflectiveOperationException {
        Class<?> queueModeType = loadClass(QUEUE_MODE_TYPE);
        Class<?> queueKeyType = loadClass(QUEUE_KEY_TYPE);
        Class<?> queueTicketIdType = loadClass(QUEUE_TICKET_ID_TYPE);
        Class<?> queueTicketStateType = loadClass(QUEUE_TICKET_STATE_TYPE);
        Class<?> queueTicketType = loadClass(QUEUE_TICKET_TYPE);
        Class<?> policyType = loadClass(MATCHMAKING_WINDOW_POLICY_TYPE);

        Object unrankedKey = instantiateRecord(queueKeyType, Map.of(
                "mode", enumConstant(queueModeType, "UNRANKED"),
                "kitId", new KitId("sumo")));
        Object searching = enumConstant(queueTicketStateType, "SEARCHING");
        Object policy = policyType.getMethod("defaults").invoke(null);

        Object anchor = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("cb57873f-87d0-4ec6-b28a-9114874d4a59"))),
                "playerId", new PlayerId(UUID.fromString("d9cb545b-c1a6-4b60-a1f6-425f3f7c1c8b")),
                "key", unrankedKey,
                "joinedAtTick", 0L,
                "searchRating", 0,
                "state", searching));
        Object candidate = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("9dbf228b-75a3-4b4e-95d7-16b467cb2c79"))),
                "playerId", new PlayerId(UUID.fromString("4602f6b5-5ce6-4d44-bcb4-cb18addb7aa4")),
                "key", unrankedKey,
                "joinedAtTick", 200L,
                "searchRating", 0,
                "state", searching));
        Object differentKeyCandidate = instantiateRecord(queueTicketType, Map.of(
                "id", instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("69f51de4-1901-48ba-b674-7f6822bc1f80"))),
                "playerId", new PlayerId(UUID.fromString("5f444b2f-3823-48e0-96e1-8a4c6414c77b")),
                "key", instantiateRecord(queueKeyType, Map.of(
                        "mode", enumConstant(queueModeType, "UNRANKED"),
                        "kitId", new KitId("nodebuff"))),
                "joinedAtTick", 200L,
                "searchRating", 0,
                "state", searching));

        assertTrue((Boolean) policyType
                .getMethod("isCompatible", queueTicketType, queueTicketType, long.class, long.class)
                .invoke(policy, anchor, candidate, 400L, 20L));
        assertFalse((Boolean) policyType
                .getMethod("isCompatible", queueTicketType, queueTicketType, long.class, long.class)
                .invoke(policy, anchor, differentKeyCandidate, 400L, 20L));

        List<?> steps = assertInstanceOf(List.class, policyType.getMethod("steps").invoke(policy));
        assertEquals(5, steps.size());
    }

    private static Object enumConstant(Class<?> enumType, String constantName) {
        return Enum.valueOf(enumType.asSubclass(Enum.class), constantName);
    }
}
