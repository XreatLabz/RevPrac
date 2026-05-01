package io.github.xreatlabz.revprac.domain.matches;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DuelRequestContractTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String DUEL_REQUEST_ID_TYPE = "io.github.xreatlabz.revprac.domain.matches.DuelRequestId";
    private static final String DUEL_REQUEST_STATE_TYPE = "io.github.xreatlabz.revprac.domain.matches.DuelRequestState";
    private static final String DUEL_REQUEST_TYPE = "io.github.xreatlabz.revprac.domain.matches.DuelRequest";

    @Test
    void duelRequestContractsUseImmutableRecordsAndValidateTransitions() throws ReflectiveOperationException {
        Class<?> duelRequestIdType = loadClass(DUEL_REQUEST_ID_TYPE);
        Class<?> duelRequestStateType = loadClass(DUEL_REQUEST_STATE_TYPE);
        Class<?> duelRequestType = loadClass(DUEL_REQUEST_TYPE);

        assertTrue(duelRequestIdType.isRecord(), "DuelRequestId should be a record");
        assertTrue(duelRequestStateType.isEnum(), "DuelRequestState should be an enum");
        assertTrue(duelRequestType.isRecord(), "DuelRequest should be a record");

        Object pendingRequest = pendingRequest();

        assertEquals(enumConstant(duelRequestStateType, "PENDING"), recordComponentValue(pendingRequest, "state"));

        Object acceptedRequest = invokeMethod(pendingRequest, "accept");
        assertEquals(enumConstant(duelRequestStateType, "ACCEPTED"), recordComponentValue(acceptedRequest, "state"));

        assertIllegalState(
                () -> invokeMethod(acceptedRequest, "decline"),
                "Accepted requests must reject non-pending terminal transitions");
    }

    @Test
    void duelRequestRejectsSelfDuelsAndInvalidExpiryOrder() {
        Class<?> duelRequestType = loadClass(DUEL_REQUEST_TYPE);

        Object sharedPlayerId = playerId("0dc63f9b-5f7d-4864-b224-429ebd5367ca");
        Instant createdAt = Instant.parse("2026-05-01T12:00:00Z");

        assertIllegalArgument(
                () -> instantiateRecord(
                        duelRequestType,
                        duelRequestValues(
                                duelRequestId("4dc473d2-443f-4d69-bfef-c6e8db7ad6d3"),
                                sharedPlayerId,
                                sharedPlayerId,
                                arenaId("sumo"),
                                kitId("nodebuff"),
                                enumConstant(loadClass(DUEL_REQUEST_STATE_TYPE), "PENDING"),
                                createdAt,
                                createdAt.plusSeconds(30))),
                "DuelRequest should reject self-duels");

        assertIllegalArgument(
                () -> instantiateRecord(
                        duelRequestType,
                        duelRequestValues(
                                duelRequestId("afc2252e-7207-4de1-a722-e7f0f1908d85"),
                                playerId("f578b4b6-9e70-4e8d-bd9f-3b51a0602b7b"),
                                playerId("2ce49312-0382-4be5-a5a2-9fc30c6590db"),
                                arenaId("bridge"),
                                kitId("bridge"),
                                enumConstant(loadClass(DUEL_REQUEST_STATE_TYPE), "PENDING"),
                                createdAt,
                                createdAt)),
                "DuelRequest should require expiresAt to be after createdAt");
    }

    private static Object pendingRequest() {
        Instant createdAt = Instant.parse("2026-05-01T12:00:00Z");
        return instantiateRecord(
                loadClass(DUEL_REQUEST_TYPE),
                duelRequestValues(
                        duelRequestId("70dbd585-0721-4d12-8ef8-b8ee8dc0cd42"),
                        playerId("c40530c0-f447-4d80-9baa-e2353d34bd82"),
                        playerId("2db4e2f0-f059-4462-8dc6-e2f540cd2da7"),
                        arenaId("nodebuff"),
                        kitId("nodebuff"),
                        enumConstant(loadClass(DUEL_REQUEST_STATE_TYPE), "PENDING"),
                        createdAt,
                        createdAt.plusSeconds(45)));
    }

    private static Map<String, Object> duelRequestValues(
            Object id,
            Object requesterId,
            Object targetId,
            Object arenaId,
            Object kitId,
            Object state,
            Instant createdAt,
            Instant expiresAt) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("requesterId", requesterId);
        values.put("targetId", targetId);
        values.put("arenaId", arenaId);
        values.put("kitId", kitId);
        values.put("state", state);
        values.put("createdAt", createdAt);
        values.put("expiresAt", expiresAt);
        return values;
    }

    private static Object duelRequestId(String rawUuid) {
        return instantiateRecord(loadClass(DUEL_REQUEST_ID_TYPE), Map.of("value", UUID.fromString(rawUuid)));
    }

    private static Object playerId(String rawUuid) {
        return instantiateRecord(loadClass(PLAYER_ID_TYPE), Map.of("value", UUID.fromString(rawUuid)));
    }

    private static Object arenaId(String value) {
        return instantiateRecord(loadClass(ARENA_ID_TYPE), Map.of("value", value));
    }

    private static Object kitId(String value) {
        return instantiateRecord(loadClass(KIT_ID_TYPE), Map.of("value", value));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf(enumType.asSubclass(Enum.class), name);
    }

    private static Object invokeMethod(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            throw exception;
        }
    }

    private static void assertIllegalArgument(ThrowingOperation operation, String message) {
        Throwable thrown = assertThrows(Throwable.class, operation::run, message);
        assertTrue(rootCause(thrown) instanceof IllegalArgumentException, message);
    }

    private static void assertIllegalState(ThrowingOperation operation, String message) {
        Throwable thrown = assertThrows(Throwable.class, operation::run, message);
        assertTrue(rootCause(thrown) instanceof IllegalStateException, message);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ReflectiveOperationException;
    }
}
