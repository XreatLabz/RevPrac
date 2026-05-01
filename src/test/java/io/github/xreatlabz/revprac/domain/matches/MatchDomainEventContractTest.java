package io.github.xreatlabz.revprac.domain.matches;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class MatchDomainEventContractTest {

    private static final String DOMAIN_MATCHES_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/matches";
    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String ARENA_RESERVATION_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId";
    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String DUEL_REQUEST_ID_TYPE = "io.github.xreatlabz.revprac.domain.matches.DuelRequestId";
    private static final String MATCH_ID_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchId";
    private static final String MATCH_OUTCOME_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchOutcome";
    private static final String MATCH_EVENT_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchEvent";

    @Test
    void matchEventSealedHierarchyUsesRecordsWithExplicitSequence() {
        Class<?> matchEventType = loadClass(MATCH_EVENT_TYPE);

        assertTrue(matchEventType.isSealed(), "MatchEvent should be a sealed interface");

        for (Class<?> eventType : matchEventType.getPermittedSubclasses()) {
            assertTrue(eventType.isRecord(), "Each MatchEvent implementation should be a record: " + eventType.getName());

            RecordComponent[] components = eventType.getRecordComponents();
            assertTrue(components.length >= 2, "Each MatchEvent record should capture sequence plus event context");
            assertEquals("sequence", components[0].getName(), "Each MatchEvent record should expose sequence explicitly");
            assertEquals(long.class, components[0].getType(), "MatchEvent sequence should be a primitive long");

            for (RecordComponent component : components) {
                String typeName = component.getType().getName();
                assertFalse(typeName.startsWith("org.bukkit"), "MatchEvent component should not use Bukkit types: " + eventType.getName());
                assertFalse(typeName.startsWith("io.papermc.paper"), "MatchEvent component should not use Paper types: " + eventType.getName());
            }
        }
    }

    @Test
    void matchEventsPreserveExplicitSequenceOrdering() {
        Class<?> eventType = loadClass(MATCH_EVENT_TYPE);
        Class<?>[] eventTypes = eventType.getPermittedSubclasses();

        Object requestCreated = instantiateRecord(
                findEventType(eventTypes, "DuelRequestCreated"),
                Map.of(
                        "sequence", 1L,
                        "requestId", instantiateRecord(loadClass(DUEL_REQUEST_ID_TYPE), Map.of("value", UUID.fromString("da291f81-b51b-43d8-a6ef-ef0d67bf61bf"))),
                        "requesterId", instantiateRecord(loadClass(PLAYER_ID_TYPE), Map.of("value", UUID.fromString("500ac2d8-faad-4cc8-a680-47bd7a382d1d"))),
                        "targetId", instantiateRecord(loadClass(PLAYER_ID_TYPE), Map.of("value", UUID.fromString("7127087b-f2fe-4db4-b660-fc4a4fb05736"))),
                        "arenaId", instantiateRecord(loadClass(ARENA_ID_TYPE), Map.of("value", "nodebuff")),
                        "kitId", instantiateRecord(loadClass(KIT_ID_TYPE), Map.of("value", "nodebuff"))));
        Object matchCompleted = instantiateRecord(
                findEventType(eventTypes, "MatchCompleted"),
                Map.of(
                        "sequence", 7L,
                        "matchId", instantiateRecord(loadClass(MATCH_ID_TYPE), Map.of("value", UUID.fromString("3dd94b31-a7d5-4a30-95d4-73f98eef4b87"))),
                        "outcome", invokeStatic(loadClass(MATCH_OUTCOME_TYPE), "forfeit",
                                instantiateRecord(loadClass(PLAYER_ID_TYPE), Map.of("value", UUID.fromString("500ac2d8-faad-4cc8-a680-47bd7a382d1d"))),
                                instantiateRecord(loadClass(PLAYER_ID_TYPE), Map.of("value", UUID.fromString("7127087b-f2fe-4db4-b660-fc4a4fb05736"))))));

        assertTrue((long) recordComponentValue(requestCreated, "sequence") < (long) recordComponentValue(matchCompleted, "sequence"));
    }

    @Test
    void domainMatchSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        Path domainDirectory = Path.of(DOMAIN_MATCHES_DIR);

        assertTrue(Files.isDirectory(domainDirectory), "Expected match domain directory to exist: " + domainDirectory);

        try (Stream<Path> sources = Files.walk(domainDirectory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected match domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static Class<?> findEventType(Class<?>[] eventTypes, String simpleName) {
        for (Class<?> eventType : eventTypes) {
            if (eventType.getSimpleName().equals(simpleName)) {
                return eventType;
            }
        }
        throw new AssertionError("Expected MatchEvent permitted subclass to exist: " + simpleName);
    }

    private static Object invokeStatic(Class<?> owner, String methodName, Object... arguments) {
        try {
            Class<?>[] parameterTypes = new Class<?>[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                parameterTypes[index] = arguments[index].getClass();
            }
            return owner.getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke static method " + owner.getName() + "#" + methodName, exception);
        }
    }
}
