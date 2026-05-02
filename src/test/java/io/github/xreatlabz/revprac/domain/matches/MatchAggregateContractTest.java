package io.github.xreatlabz.revprac.domain.matches;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MatchAggregateContractTest {

    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String ARENA_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaId";
    private static final String ARENA_RESERVATION_ID_TYPE = "io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId";
    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String MATCH_ID_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchId";
    private static final String MATCH_STATE_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchState";
    private static final String MATCH_SIDE_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchSide";
    private static final String MATCH_PARTICIPANTS_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchParticipants";
    private static final String MATCH_RULESET_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchRuleset";
    private static final String MATCH_END_REASON_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchEndReason";
    private static final String MATCH_OUTCOME_TYPE = "io.github.xreatlabz.revprac.domain.matches.MatchOutcome";
    private static final String MATCH_TYPE = "io.github.xreatlabz.revprac.domain.matches.Match";

    @Test
    void matchParticipantsRequireTwoDistinctPlayersAndResolveLookups() throws ReflectiveOperationException {
        Class<?> participantsType = loadClass(MATCH_PARTICIPANTS_TYPE);
        Object playerOne = playerId("7983ee3a-c364-4268-8f94-912fcb8f2df6");
        Object playerTwo = playerId("80f7d4d6-4cdd-4e44-bb02-cbd700dce359");
        Object outsider = playerId("f858173d-a551-499a-bfd0-aa6fdd7ef05d");

        assertTrue(participantsType.isRecord(), "MatchParticipants should be a record");

        assertIllegalArgument(
                () -> instantiateRecord(participantsType, Map.of("playerOne", playerOne, "playerTwo", playerOne)),
                "MatchParticipants should reject duplicate players");

        Object participants = instantiateRecord(participantsType, Map.of("playerOne", playerOne, "playerTwo", playerTwo));

        assertEquals(Boolean.TRUE, invokeMethod(participants, "contains", playerOne));
        assertEquals(Boolean.FALSE, invokeMethod(participants, "contains", outsider));
        assertEquals(Optional.of(enumConstant(loadClass(MATCH_SIDE_TYPE), "ONE")), invokeMethod(participants, "sideOf", playerOne));
        assertEquals(Optional.of(enumConstant(loadClass(MATCH_SIDE_TYPE), "TWO")), invokeMethod(participants, "sideOf", playerTwo));
        assertEquals(Optional.of(playerTwo), invokeMethod(participants, "opponentOf", playerOne));
        assertEquals(Optional.empty(), invokeMethod(participants, "opponentOf", outsider));
    }

    @Test
    void matchRulesetRejectsNonPositiveTickBudgets() {
        Class<?> rulesetType = loadClass(MATCH_RULESET_TYPE);

        assertTrue(rulesetType.isRecord(), "MatchRuleset should be a record");

        assertIllegalArgument(
                () -> instantiateRecord(rulesetType, rulesetValues(0, 1200, true)),
                "MatchRuleset should reject non-positive countdown ticks");
        assertIllegalArgument(
                () -> instantiateRecord(rulesetType, rulesetValues(40, 0, true)),
                "MatchRuleset should reject non-positive max duration ticks");
    }

    @Test
    void matchStartsInCountdownTicksToActiveAndCompletesExactlyOnce() throws ReflectiveOperationException {
        Class<?> matchType = loadClass(MATCH_TYPE);
        Class<?> matchStateType = loadClass(MATCH_STATE_TYPE);

        Object activeRuleset = instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(2, 5, true));
        Object match = createMatch(activeRuleset);

        assertTrue(matchType.isRecord(), "Match should be a record");
        assertEquals(enumConstant(matchStateType, "COUNTDOWN"), recordComponentValue(match, "state"));
        assertEquals(2, recordComponentValue(match, "countdownTicksRemaining"));
        assertEquals(Optional.empty(), recordComponentValue(match, "outcome"));

        match = invokeMethod(match, "tickCountdown");
        assertEquals(enumConstant(matchStateType, "COUNTDOWN"), recordComponentValue(match, "state"));
        assertEquals(1, recordComponentValue(match, "countdownTicksRemaining"));

        match = invokeMethod(match, "tickCountdown");
        assertEquals(enumConstant(matchStateType, "ACTIVE"), recordComponentValue(match, "state"));
        assertEquals(0, recordComponentValue(match, "countdownTicksRemaining"));

        Instant completedAt = Instant.parse("2026-05-02T15:00:00Z");
        Object completed =
                invokeMethod(match, "complete", invokeStatic(loadClass(MATCH_OUTCOME_TYPE), "win", playerOne(), playerTwo()), completedAt);
        assertEquals(enumConstant(matchStateType, "COMPLETED"), recordComponentValue(completed, "state"));
        assertEquals(
                Optional.of(invokeStatic(loadClass(MATCH_OUTCOME_TYPE), "win", playerOne(), playerTwo())),
                recordComponentValue(completed, "outcome"));
        assertEquals(Optional.of(completedAt), recordComponentValue(completed, "completedAt"));

        assertIllegalState(
                () -> invokeMethod(
                        completed,
                        "complete",
                        invokeStatic(loadClass(MATCH_OUTCOME_TYPE), "forfeit", playerTwo(), playerOne()),
                        Instant.parse("2026-05-02T15:01:00Z")),
                "Match should complete exactly once");
    }

    @Test
    void matchTracksSpectatorsSeparatelyAndAllowsCleanupAfterCompletion() throws ReflectiveOperationException {
        Object spectator = playerId("31f8f6c2-515f-4ba4-8c9d-c24973d4ec85");
        Object match = createMatch(instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(1, 10, true)));
        match = invokeMethod(match, "tickCountdown");

        Object withSpectator = invokeMethod(match, "addSpectator", spectator);
        Set<?> spectators = assertInstanceOf(Set.class, recordComponentValue(withSpectator, "spectators"));
        assertEquals(Set.of(spectator), spectators);
        assertFalse(spectators.contains(playerOne()), "Spectators must stay separate from participants");
        assertThrows(UnsupportedOperationException.class, () -> addToSet(spectators, spectator));

        Object completed = invokeMethod(
                withSpectator,
                "complete",
                invokeStatic(loadClass(MATCH_OUTCOME_TYPE), "shutdown"),
                Instant.parse("2026-05-02T15:02:00Z"));
        assertIllegalState(
                () -> invokeMethod(completed, "addSpectator", playerId("86e15c52-e18f-4920-9d5a-6ac76ff69ca1")),
                "Completed matches must reject new spectator mutations");

        Object cleanedUp = invokeMethod(completed, "removeSpectator", spectator);
        assertEquals(Set.of(), recordComponentValue(cleanedUp, "spectators"));
    }

    @Test
    void matchRejectsSpectatorsBeforeItIsActive() throws ReflectiveOperationException {
        Object spectator = playerId("31f8f6c2-515f-4ba4-8c9d-c24973d4ec85");
        Object match = createMatch(instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(1, 10, true)));

        assertIllegalState(
                () -> invokeMethod(match, "addSpectator", spectator),
                "Countdown matches must reject new spectators");
    }

    @Test
    void matchRejectsWinAndForfeitOutcomesWithOutsiders() throws ReflectiveOperationException {
        Object match = createMatch(instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(1, 10, true)));
        Object activeMatch = invokeMethod(match, "tickCountdown");
        Object outsider = playerId("f858173d-a551-499a-bfd0-aa6fdd7ef05d");
        Class<?> outcomeType = loadClass(MATCH_OUTCOME_TYPE);

        Object outsiderWinner = invokeStatic(outcomeType, "win", outsider, playerOne());
        assertIllegalArgument(
                () -> invokeMethod(activeMatch, "complete", outsiderWinner, Instant.parse("2026-05-02T15:03:00Z")),
                "Match must reject win outcomes with an outsider winner");

        Object outsiderLoser = invokeStatic(outcomeType, "win", playerOne(), outsider);
        assertIllegalArgument(
                () -> invokeMethod(activeMatch, "complete", outsiderLoser, Instant.parse("2026-05-02T15:03:00Z")),
                "Match must reject win outcomes with an outsider loser");

        Object outsiderForfeitWinner = invokeStatic(outcomeType, "forfeit", outsider, playerTwo());
        assertIllegalArgument(
                () -> invokeMethod(activeMatch, "complete", outsiderForfeitWinner, Instant.parse("2026-05-02T15:03:00Z")),
                "Match must reject forfeit outcomes with an outsider winner");

        Object outsiderForfeitLoser = invokeStatic(outcomeType, "forfeit", playerTwo(), outsider);
        assertIllegalArgument(
                () -> invokeMethod(activeMatch, "complete", outsiderForfeitLoser, Instant.parse("2026-05-02T15:03:00Z")),
                "Match must reject forfeit outcomes with an outsider loser");
    }

    @Test
    void matchAcceptsParticipantWinForfeitTimeoutAndShutdownOutcomes() throws ReflectiveOperationException {
        Object ruleset = instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(1, 10, true));
        Class<?> outcomeType = loadClass(MATCH_OUTCOME_TYPE);

        Object winMatch = invokeMethod(createMatch(ruleset), "tickCountdown");
        Object winCompleted = invokeMethod(
                winMatch,
                "complete",
                invokeStatic(outcomeType, "win", playerOne(), playerTwo()),
                Instant.parse("2026-05-02T15:04:00Z"));
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "COMPLETED"), recordComponentValue(winCompleted, "state"));

        Object forfeitMatch = invokeMethod(createMatch(ruleset), "tickCountdown");
        Object forfeitCompleted =
                invokeMethod(
                        forfeitMatch,
                        "complete",
                        invokeStatic(outcomeType, "forfeit", playerTwo(), playerOne()),
                        Instant.parse("2026-05-02T15:04:00Z"));
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "COMPLETED"), recordComponentValue(forfeitCompleted, "state"));

        Object timeoutMatch = invokeMethod(createMatch(ruleset), "tickCountdown");
        Object timeoutCompleted = invokeMethod(
                timeoutMatch,
                "complete",
                invokeStatic(outcomeType, "timeout"),
                Instant.parse("2026-05-02T15:04:00Z"));
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "COMPLETED"), recordComponentValue(timeoutCompleted, "state"));

        Object shutdownMatch = invokeMethod(createMatch(ruleset), "tickCountdown");
        Object shutdownCompleted = invokeMethod(
                shutdownMatch,
                "complete",
                invokeStatic(outcomeType, "shutdown"),
                Instant.parse("2026-05-02T15:04:00Z"));
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "COMPLETED"), recordComponentValue(shutdownCompleted, "state"));
    }

    @Test
    void matchTicksActiveAndTimesOutAtConfiguredLimit() throws ReflectiveOperationException {
        Object match = createMatch(instantiateRecord(loadClass(MATCH_RULESET_TYPE), rulesetValues(1, 2, false)));
        match = invokeMethod(match, "tickCountdown");

        match = invokeMethod(match, "tickActive", Instant.parse("2026-05-02T15:04:30Z"));
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "ACTIVE"), recordComponentValue(match, "state"));
        assertEquals(1, recordComponentValue(match, "activeTicksElapsed"));

        Instant completedAt = Instant.parse("2026-05-02T15:05:00Z");
        match = invokeMethod(match, "tickActive", completedAt);
        assertEquals(enumConstant(loadClass(MATCH_STATE_TYPE), "COMPLETED"), recordComponentValue(match, "state"));
        assertEquals(Optional.of(completedAt), recordComponentValue(match, "completedAt"));

        Optional<?> outcome = assertInstanceOf(Optional.class, recordComponentValue(match, "outcome"));
        Object completedOutcome = outcome.orElseThrow();
        assertEquals(enumConstant(loadClass(MATCH_END_REASON_TYPE), "TIMEOUT"), recordComponentValue(completedOutcome, "reason"));
        assertEquals(Optional.empty(), recordComponentValue(completedOutcome, "winnerId"));
        assertEquals(Optional.empty(), recordComponentValue(completedOutcome, "loserId"));
    }

    @Test
    void matchOutcomeSupportsWinForfeitTimeoutAndShutdown() {
        Class<?> outcomeType = loadClass(MATCH_OUTCOME_TYPE);

        Object win = invokeStatic(outcomeType, "win", playerOne(), playerTwo());
        Object forfeit = invokeStatic(outcomeType, "forfeit", playerOne(), playerTwo());
        Object timeout = invokeStatic(outcomeType, "timeout");
        Object shutdown = invokeStatic(outcomeType, "shutdown");

        assertEquals(enumConstant(loadClass(MATCH_END_REASON_TYPE), "WIN"), recordComponentValue(win, "reason"));
        assertEquals(enumConstant(loadClass(MATCH_END_REASON_TYPE), "FORFEIT"), recordComponentValue(forfeit, "reason"));
        assertEquals(enumConstant(loadClass(MATCH_END_REASON_TYPE), "TIMEOUT"), recordComponentValue(timeout, "reason"));
        assertEquals(enumConstant(loadClass(MATCH_END_REASON_TYPE), "SHUTDOWN"), recordComponentValue(shutdown, "reason"));
        assertEquals(Optional.empty(), recordComponentValue(timeout, "winnerId"));
        assertEquals(Optional.empty(), recordComponentValue(shutdown, "winnerId"));
    }

    private static Object createMatch(Object ruleset) {
        return invokeStatic(
                loadClass(MATCH_TYPE),
                "create",
                matchId("5e1465c2-7b29-4d6c-a531-2cd0356f51cf"),
                instantiateRecord(loadClass(MATCH_PARTICIPANTS_TYPE), Map.of("playerOne", playerOne(), "playerTwo", playerTwo())),
                arenaId("nodebuff"),
                kitId("nodebuff"),
                arenaReservationId("e6693e7d-e120-4cdb-a4ea-c7760319a799"),
                ruleset);
    }

    private static Map<String, Object> rulesetValues(int countdownTicks, int maxDurationTicks, boolean spectatorsEnabled) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("countdownTicks", countdownTicks);
        values.put("maxDurationTicks", maxDurationTicks);
        values.put("spectatorsEnabled", spectatorsEnabled);
        return values;
    }

    private static Object matchId(String rawUuid) {
        return instantiateRecord(loadClass(MATCH_ID_TYPE), Map.of("value", UUID.fromString(rawUuid)));
    }

    private static Object playerOne() {
        return playerId("80ed69b0-92bc-45f4-8937-541f472b62be");
    }

    private static Object playerTwo() {
        return playerId("10a3375c-94f2-4bb9-80c0-cec3d839b4cb");
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

    private static Object arenaReservationId(String rawUuid) {
        return instantiateRecord(loadClass(ARENA_RESERVATION_ID_TYPE), Map.of("value", UUID.fromString(rawUuid)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf(enumType.asSubclass(Enum.class), name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addToSet(Set<?> values, Object value) {
        ((Set) values).add(value);
    }

    private static Object invokeMethod(Object target, String methodName, Object... arguments) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, argumentTypes(arguments));
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            throw exception;
        }
    }

    private static Object invokeStatic(Class<?> owner, String methodName, Object... arguments) {
        try {
            Method method = owner.getMethod(methodName, argumentTypes(arguments));
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not invoke static method " + owner.getName() + "#" + methodName, exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke static method " + owner.getName() + "#" + methodName, exception);
        }
    }

    private static Class<?>[] argumentTypes(Object[] arguments) {
        Class<?>[] argumentTypes = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            argumentTypes[index] = arguments[index].getClass();
        }
        return argumentTypes;
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
