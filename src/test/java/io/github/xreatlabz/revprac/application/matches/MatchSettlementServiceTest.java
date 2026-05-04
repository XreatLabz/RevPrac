package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MatchSettlementServiceTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-05-02T14:00:00Z");

    @Test
    void settlesWinAndForfeitOutcomesIntoHistoryAndPlayerStats() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match win = completedMatch("win", MatchOutcome.win(player("one"), player("two")), 45);
        Match forfeit = completedMatch(
                "forfeit",
                player("three"),
                player("four"),
                MatchOutcome.forfeit(player("three"), player("four")),
                12);

        service.settle(win);
        service.settle(forfeit);

        assertEquals(MatchEndReason.WIN, repository.findHistory(win.id()).orElseThrow().endReason());
        assertEquals(MatchOrigin.DIRECT_DUEL, repository.findHistory(win.id()).orElseThrow().origin());
        assertEquals(Optional.of(player("one")), repository.findHistory(win.id()).orElseThrow().winnerId());
        assertStats(repository.findStats(player("one"), new KitId("nodebuff")).orElseThrow(), 1, 1, 0, 0, 0, 0);
        assertStats(repository.findStats(player("two"), new KitId("nodebuff")).orElseThrow(), 1, 0, 1, 0, 0, 0);
        assertStats(repository.findStats(player("three"), new KitId("nodebuff")).orElseThrow(), 1, 1, 0, 0, 0, 0);
        assertStats(repository.findStats(player("four"), new KitId("nodebuff")).orElseThrow(), 1, 0, 1, 1, 0, 0);
    }

    @Test
    void settlesTimeoutAndShutdownOutcomesWithoutWinnerOrLoserStats() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match timeout = completedMatch("timeout", MatchOutcome.timeout(), 600);
        Match shutdown = completedMatch("shutdown", MatchOutcome.shutdown(), 20);

        service.settle(timeout);
        service.settle(shutdown);

        assertEquals(Optional.empty(), repository.findHistory(timeout.id()).orElseThrow().winnerId());
        assertStats(repository.findStats(player("one"), new KitId("nodebuff")).orElseThrow(), 2, 0, 0, 0, 1, 1);
        assertStats(repository.findStats(player("two"), new KitId("nodebuff")).orElseThrow(), 2, 0, 0, 0, 1, 1);
    }

    @Test
    void rejectsMatchesThatAreNotCompleted() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match active = Match.create(
                        new MatchId(UUID.nameUUIDFromBytes("active".getBytes())),
                        new MatchParticipants(player("one"), player("two")),
                        new ArenaId("arena-one"),
                        new KitId("nodebuff"),
                        new ArenaReservationId(UUID.nameUUIDFromBytes("reservation".getBytes())),
                        new MatchRuleset(1, 200, true))
                .tickCountdown();

        assertThrows(IllegalArgumentException.class, () -> service.settle(active));
    }

    private static Match completedMatch(String seed, MatchOutcome outcome, int activeTicks) {
        return completedMatch(seed, player("one"), player("two"), outcome, activeTicks);
    }

    private static Match completedMatch(
            String seed,
            PlayerId firstPlayer,
            PlayerId secondPlayer,
            MatchOutcome outcome,
            int activeTicks) {
        return new Match(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                new MatchParticipants(firstPlayer, secondPlayer),
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                MatchOrigin.DIRECT_DUEL,
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                new MatchRuleset(1, 1_200, true),
                MatchState.COMPLETED,
                0,
                activeTicks,
                Set.of(),
                Optional.of(outcome),
                Optional.of(COMPLETED_AT));
    }

    private static void assertStats(
            PlayerKitStats stats,
            long matchesPlayed,
            long wins,
            long losses,
            long forfeits,
            long timeouts,
            long shutdowns) {
        assertEquals(matchesPlayed, stats.matchesPlayed());
        assertEquals(wins, stats.wins());
        assertEquals(losses, stats.losses());
        assertEquals(forfeits, stats.forfeits());
        assertEquals(timeouts, stats.timeouts());
        assertEquals(shutdowns, stats.shutdowns());
        assertEquals(COMPLETED_AT, stats.updatedAt());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
