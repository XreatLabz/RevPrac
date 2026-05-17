package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.xreatlabz.revprac.application.ratings.RatingProgression;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.ports.matches.PostMatchSummaryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PostMatchSummaryServiceTest {

    @Test
    void rankedDecisiveSummaryIncludesPerRecipientRatingDeltaAndOpponentFallback() {
        CapturingPostMatchSummaryPort port = new CapturingPostMatchSummaryPort();
        port.names.put(player("winner"), "Winner");
        PostMatchSummaryService service = new PostMatchSummaryService(port);
        Match match = completedMatch(
                "ranked-win",
                MatchOrigin.QUEUE_RANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.win(player("winner"), player("loser")));

        service.send(match, settlementResult(match, Optional.of(ratingProgression())));

        assertEquals(
                "Match summary: opponent="
                        + player("loser").value()
                        + " kit=nodebuff result=win end=win rating=1016 (+16)",
                port.messagesFor(player("winner")).getFirst());
        assertEquals(
                "Match summary: opponent=Winner kit=nodebuff result=loss end=win rating=984 (-16)",
                port.messagesFor(player("loser")).getFirst());
    }

    @Test
    void unrankedAndTimeoutSummariesOmitRatingTextAndUseDrawForTimeouts() {
        CapturingPostMatchSummaryPort port = new CapturingPostMatchSummaryPort();
        port.names.put(player("winner"), "Winner");
        port.names.put(player("loser"), "Loser");
        PostMatchSummaryService service = new PostMatchSummaryService(port);

        Match unranked = completedMatch(
                "unranked-forfeit",
                MatchOrigin.QUEUE_UNRANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.forfeit(player("winner"), player("loser")));
        service.send(unranked, settlementResult(unranked, Optional.of(ratingProgression())));

        Match timeout = completedMatch(
                "ranked-timeout",
                MatchOrigin.QUEUE_RANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.timeout());
        service.send(timeout, settlementResult(timeout, Optional.empty()));

        assertEquals(
                "Match summary: opponent=Loser kit=nodebuff result=win end=forfeit",
                port.messagesFor(player("winner")).get(0));
        assertEquals(
                "Match summary: opponent=Winner kit=nodebuff result=loss end=forfeit",
                port.messagesFor(player("loser")).get(0));
        assertEquals(
                "Match summary: opponent=Loser kit=nodebuff result=draw end=timeout",
                port.messagesFor(player("winner")).get(1));
        assertEquals(
                "Match summary: opponent=Winner kit=nodebuff result=draw end=timeout",
                port.messagesFor(player("loser")).get(1));
    }

    @Test
    void shutdownSummariesAreSkippedAndSendFailuresAreSwallowedPerRecipient() {
        CapturingPostMatchSummaryPort port = new CapturingPostMatchSummaryPort();
        port.names.put(player("winner"), "Winner");
        port.names.put(player("loser"), "Loser");
        port.failures.put(player("winner"), new IllegalStateException("send failed"));
        PostMatchSummaryService service = new PostMatchSummaryService(port);

        Match shutdown = completedMatch(
                "shutdown",
                MatchOrigin.QUEUE_RANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.shutdown());
        service.send(shutdown, settlementResult(shutdown, Optional.of(ratingProgression())));

        assertEquals(List.of(), port.messagesFor(player("winner")));
        assertEquals(List.of(), port.messagesFor(player("loser")));

        Match rankedWin = completedMatch(
                "send-failure",
                MatchOrigin.QUEUE_RANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.win(player("winner"), player("loser")));
        assertDoesNotThrow(() -> service.send(rankedWin, settlementResult(rankedWin, Optional.of(ratingProgression()))));
        assertEquals(List.of(), port.messagesFor(player("winner")));
        assertEquals(
                List.of("Match summary: opponent=Winner kit=nodebuff result=loss end=win rating=984 (-16)"),
                port.messagesFor(player("loser")));
    }

    @Test
    void duplicateSettlementResultsDoNotSendAnySummary() {
        CapturingPostMatchSummaryPort port = new CapturingPostMatchSummaryPort();
        port.names.put(player("winner"), "Winner");
        port.names.put(player("loser"), "Loser");
        PostMatchSummaryService service = new PostMatchSummaryService(port);
        Match rankedWin = completedMatch(
                "duplicate-ranked-win",
                MatchOrigin.QUEUE_RANKED,
                player("winner"),
                player("loser"),
                MatchOutcome.win(player("winner"), player("loser")));

        service.send(rankedWin, settlementResult(rankedWin, false, Optional.of(ratingProgression())));

        assertEquals(List.of(), port.messagesFor(player("winner")));
        assertEquals(List.of(), port.messagesFor(player("loser")));
    }

    private static MatchSettlementResult settlementResult(
            Match match,
            Optional<RatingProgression> ratingProgression) {
        return settlementResult(match, true, ratingProgression);
    }

    private static MatchSettlementResult settlementResult(
            Match match,
            boolean applied,
            Optional<RatingProgression> ratingProgression) {
        MatchSettlement settlement = new MatchSettlement(
                new io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry(
                        match.id(),
                        match.participants().playerOne(),
                        match.participants().playerTwo(),
                        match.arenaId(),
                        match.kitId(),
                        match.origin(),
                        match.outcome().orElseThrow().reason(),
                        match.outcome().orElseThrow().winnerId(),
                        match.outcome().orElseThrow().loserId(),
                        match.activeTicksElapsed(),
                        match.completedAt().orElseThrow()),
                List.of(
                        new PlayerKitStatDelta(
                                match.participants().playerOne(),
                                match.kitId(),
                                1,
                                1,
                                0,
                                0,
                                0,
                                0,
                                match.completedAt().orElseThrow()),
                        new PlayerKitStatDelta(
                                match.participants().playerTwo(),
                                match.kitId(),
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                match.completedAt().orElseThrow())),
                ratingProgression.map(RatingProgression::asList).orElseGet(List::of));
        return new MatchSettlementResult(settlement, applied, ratingProgression);
    }

    private static RatingProgression ratingProgression() {
        PlayerId winner = player("winner");
        PlayerId loser = player("loser");
        KitId kitId = new KitId("nodebuff");
        Instant updatedAt = Instant.parse("2026-05-06T12:00:00Z");
        return new RatingProgression(
                new PlayerRating(winner, kitId, 1000, 0, 0, updatedAt),
                new PlayerRating(winner, kitId, 1016, 1, 0, updatedAt),
                new PlayerRating(loser, kitId, 1000, 0, 0, updatedAt),
                new PlayerRating(loser, kitId, 984, 0, 1, updatedAt));
    }

    private static Match completedMatch(String seed, MatchOrigin origin, PlayerId one, PlayerId two, MatchOutcome outcome) {
        return new Match(
                new io.github.xreatlabz.revprac.domain.matches.MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                new MatchParticipants(one, two),
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                origin,
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                new MatchRuleset(1, 1_200, true),
                MatchState.COMPLETED,
                0,
                45,
                Set.of(),
                Optional.of(outcome),
                Optional.of(Instant.parse("2026-05-06T12:00:00Z")));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static final class CapturingPostMatchSummaryPort implements PostMatchSummaryPort {
        private final Map<PlayerId, String> names = new HashMap<>();
        private final Map<PlayerId, RuntimeException> failures = new HashMap<>();
        private final Map<PlayerId, List<String>> messages = new HashMap<>();

        @Override
        public Optional<String> playerName(PlayerId playerId) {
            return Optional.ofNullable(names.get(playerId));
        }

        @Override
        public void send(PlayerId playerId, String message) {
            RuntimeException failure = failures.get(playerId);
            if (failure != null) {
                throw failure;
            }
            messages.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(message);
        }

        private List<String> messagesFor(PlayerId playerId) {
            return messages.getOrDefault(playerId, List.of());
        }
    }
}
