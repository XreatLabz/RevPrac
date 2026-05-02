package io.github.xreatlabz.revprac.domain.queues;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class QueueTicketContractTest {

    private static final String DOMAIN_QUEUES_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/queues";
    private static final String QUEUE_TICKET_ID_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicketId";
    private static final String QUEUE_MODE_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueMode";
    private static final String QUEUE_TICKET_STATE_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicketState";
    private static final String QUEUE_KEY_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueKey";
    private static final String QUEUE_TICKET_TYPE = "io.github.xreatlabz.revprac.domain.queues.QueueTicket";
    private static final String QUEUED_MATCH_ASSIGNMENT_TYPE =
            "io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment";

    @Test
    void queueEnumsAndIdentifiersExposeDocumentedContracts() {
        Class<?> queueTicketIdType = loadClass(QUEUE_TICKET_ID_TYPE);
        Class<?> queueModeType = loadClass(QUEUE_MODE_TYPE);
        Class<?> queueTicketStateType = loadClass(QUEUE_TICKET_STATE_TYPE);

        assertTrue(queueTicketIdType.isRecord(), "QueueTicketId should be a record");
        assertTrue(queueModeType.isEnum(), "QueueMode should be an enum");
        assertTrue(queueTicketStateType.isEnum(), "QueueTicketState should be an enum");
        assertEquals(
                Arrays.asList("UNRANKED", "RANKED"),
                Arrays.stream(queueModeType.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
        assertEquals(
                Arrays.asList("SEARCHING", "PAIRING", "MATCHED", "CANCELLED", "EXPIRED"),
                Arrays.stream(queueTicketStateType.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());

        Object ticketId = instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString("6efd455f-a4ae-4c0a-a615-c8b0628c3e8d")));
        assertEquals(
                UUID.fromString("6efd455f-a4ae-4c0a-a615-c8b0628c3e8d"),
                recordComponentValue(ticketId, "value"));

        assertNullPointer(() -> queueTicketIdType.getDeclaredConstructor(UUID.class).newInstance(new Object[] {null}));
    }

    @Test
    void queueKeyTicketAndAssignmentRejectImpossibleStateAndNormalizeUnrankedRatings() throws ReflectiveOperationException {
        Class<?> queueModeType = loadClass(QUEUE_MODE_TYPE);
        Class<?> queueTicketStateType = loadClass(QUEUE_TICKET_STATE_TYPE);
        Class<?> queueKeyType = loadClass(QUEUE_KEY_TYPE);
        Class<?> queueTicketType = loadClass(QUEUE_TICKET_TYPE);
        Class<?> queueTicketIdType = loadClass(QUEUE_TICKET_ID_TYPE);
        Class<?> queuedMatchAssignmentType = loadClass(QUEUED_MATCH_ASSIGNMENT_TYPE);

        Object ranked = enumConstant(queueModeType, "RANKED");
        Object unranked = enumConstant(queueModeType, "UNRANKED");
        Object searching = enumConstant(queueTicketStateType, "SEARCHING");
        KitId noDebuff = new KitId("nodebuff");

        var queueKeyConstructor = queueKeyType.getDeclaredConstructor(queueModeType, KitId.class);
        queueKeyConstructor.setAccessible(true);
        assertNullPointer(() -> queueKeyConstructor.newInstance(null, noDebuff));
        assertNullPointer(() -> queueKeyConstructor.newInstance(ranked, null));

        Object rankedKey = instantiateRecord(queueKeyType, Map.of("mode", ranked, "kitId", noDebuff));
        Object unrankedKey = instantiateRecord(queueKeyType, Map.of("mode", unranked, "kitId", noDebuff));

        var queueTicketConstructor = queueTicketType.getDeclaredConstructor(
                queueTicketIdType, PlayerId.class, queueKeyType, long.class, int.class, queueTicketStateType);
        queueTicketConstructor.setAccessible(true);
        assertNullPointer(() -> queueTicketConstructor.newInstance(
                null,
                playerId("79d82d62-2ece-4fc0-b594-d6d4b4409390"),
                rankedKey,
                0L,
                1200,
                searching));
        assertIllegalArgument(() -> queueTicketConstructor.newInstance(
                ticketId(queueTicketIdType, "28953f54-935a-4a82-b2b5-5eec1d531777"),
                playerId("79d82d62-2ece-4fc0-b594-d6d4b4409390"),
                rankedKey,
                -1L,
                1200,
                searching));
        assertIllegalArgument(() -> queueTicketConstructor.newInstance(
                ticketId(queueTicketIdType, "28953f54-935a-4a82-b2b5-5eec1d531777"),
                playerId("79d82d62-2ece-4fc0-b594-d6d4b4409390"),
                rankedKey,
                0L,
                0,
                searching));

        Object unrankedTicket = instantiateRecord(queueTicketType, Map.of(
                "id", ticketId(queueTicketIdType, "28953f54-935a-4a82-b2b5-5eec1d531777"),
                "playerId", playerId("79d82d62-2ece-4fc0-b594-d6d4b4409390"),
                "key", unrankedKey,
                "joinedAtTick", 5L,
                "searchRating", 1600,
                "state", searching));
        assertEquals(0, recordComponentValue(unrankedTicket, "searchRating"));

        Object rankedTicket = instantiateRecord(queueTicketType, Map.of(
                "id", ticketId(queueTicketIdType, "5429c5dc-fd46-4358-b9b1-40b9304eb981"),
                "playerId", playerId("7c889ba8-7978-40d1-80cb-c2e6c8cff54b"),
                "key", rankedKey,
                "joinedAtTick", 10L,
                "searchRating", 1250,
                "state", searching));
        assertEquals(1250, recordComponentValue(rankedTicket, "searchRating"));

        Object pairingTicket = invokeNoArg(queueTicketType, rankedTicket, "markPairing");
        assertEquals("PAIRING", ((Enum<?>) recordComponentValue(pairingTicket, "state")).name());
        Object matchedTicket = invokeNoArg(queueTicketType, pairingTicket, "markMatched");
        assertEquals("MATCHED", ((Enum<?>) recordComponentValue(matchedTicket, "state")).name());
        Object cancelledTicket = invokeNoArg(queueTicketType, rankedTicket, "cancel");
        assertEquals("CANCELLED", ((Enum<?>) recordComponentValue(cancelledTicket, "state")).name());
        Object expiredTicket = invokeNoArg(queueTicketType, rankedTicket, "expire");
        assertEquals("EXPIRED", ((Enum<?>) recordComponentValue(expiredTicket, "state")).name());

        assertIllegalState(() -> invokeNoArg(queueTicketType, rankedTicket, "markMatched"));
        assertIllegalState(() -> invokeNoArg(queueTicketType, pairingTicket, "cancel"));
        assertIllegalState(() -> invokeNoArg(queueTicketType, matchedTicket, "expire"));
        assertIllegalState(() -> invokeNoArg(queueTicketType, cancelledTicket, "markPairing"));
        assertIllegalState(() -> invokeNoArg(queueTicketType, expiredTicket, "cancel"));

        Object secondPairingTicket = invokeNoArg(queueTicketType, instantiateRecord(queueTicketType, Map.of(
                "id", ticketId(queueTicketIdType, "896df0bf-7d09-4c91-aa24-410dcb84855c"),
                "playerId", playerId("3d8e225e-96a0-4e28-8595-5d96f4cb145d"),
                "key", rankedKey,
                "joinedAtTick", 12L,
                "searchRating", 1190,
                "state", searching)), "markPairing");

        var assignmentConstructor = queuedMatchAssignmentType.getDeclaredConstructor(
                queueTicketType, queueTicketType, queueModeType, KitId.class, int.class);
        assignmentConstructor.setAccessible(true);
        Object assignment = assignmentConstructor.newInstance(pairingTicket, secondPairingTicket, ranked, noDebuff, 60);
        assertEquals(pairingTicket, recordComponentValue(assignment, "first"));
        assertEquals(secondPairingTicket, recordComponentValue(assignment, "second"));
        assertEquals(60, recordComponentValue(assignment, "ratingDelta"));

        Object samePlayerPairing = invokeNoArg(queueTicketType, instantiateRecord(queueTicketType, Map.of(
                "id", ticketId(queueTicketIdType, "21d415fa-e0c0-4cf0-a1b2-7a6313ebca41"),
                "playerId", playerId("7c889ba8-7978-40d1-80cb-c2e6c8cff54b"),
                "key", rankedKey,
                "joinedAtTick", 13L,
                "searchRating", 1300,
                "state", searching)), "markPairing");
        Object mismatchedKeyPairing = invokeNoArg(queueTicketType, instantiateRecord(queueTicketType, Map.of(
                "id", ticketId(queueTicketIdType, "7c3a0fb0-a7e8-44e0-bbe3-4df3776ca6ab"),
                "playerId", playerId("7b4052b7-f4f9-4f46-b574-d14122e0dc90"),
                "key", instantiateRecord(queueKeyType, Map.of(
                        "mode", ranked,
                        "kitId", new KitId("sumo"))),
                "joinedAtTick", 13L,
                "searchRating", 1300,
                "state", searching)), "markPairing");

        assertIllegalArgument(() -> assignmentConstructor.newInstance(pairingTicket, samePlayerPairing, ranked, noDebuff, 50));
        assertIllegalArgument(() -> assignmentConstructor.newInstance(pairingTicket, mismatchedKeyPairing, ranked, noDebuff, 50));
        assertIllegalArgument(() -> assignmentConstructor.newInstance(rankedTicket, secondPairingTicket, ranked, noDebuff, 60));
    }

    @Test
    void queueDomainSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        Path domainDirectory = Path.of(DOMAIN_QUEUES_DIR);

        assertTrue(Files.isDirectory(domainDirectory), "Expected queue domain directory to exist: " + domainDirectory);

        try (Stream<Path> sources = Files.walk(domainDirectory)) {
            var javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected queue domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static Object invokeNoArg(Class<?> type, Object target, String methodName) throws ReflectiveOperationException {
        return type.getMethod(methodName).invoke(target);
    }

    private static Object ticketId(Class<?> queueTicketIdType, String value) {
        return instantiateRecord(queueTicketIdType, Map.of("value", UUID.fromString(value)));
    }

    private static PlayerId playerId(String value) {
        return new PlayerId(UUID.fromString(value));
    }

    private static Object enumConstant(Class<?> enumType, String constantName) {
        return Enum.valueOf(enumType.asSubclass(Enum.class), constantName);
    }

    private static void assertNullPointer(ThrowingOperation operation) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, operation::run);
        assertInstanceOf(NullPointerException.class, exception.getCause());
    }

    private static void assertIllegalArgument(ThrowingOperation operation) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, operation::run);
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    private static void assertIllegalState(ThrowingOperation operation) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, operation::run);
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ReflectiveOperationException;
    }
}
