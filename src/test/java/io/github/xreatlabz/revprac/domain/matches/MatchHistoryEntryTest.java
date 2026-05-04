package io.github.xreatlabz.revprac.domain.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MatchHistoryEntryTest {

    @Test
    void completedWinnerEntriesRequireDistinctParticipantsAndNonNegativeTicks() {
        MatchId matchId = new MatchId(UUID.nameUUIDFromBytes("history-entry".getBytes()));
        PlayerId winner = player("winner");
        PlayerId loser = player("loser");
        Instant completedAt = Instant.parse("2026-05-02T13:00:00Z");

        MatchHistoryEntry entry = new MatchHistoryEntry(
                matchId,
                winner,
                loser,
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                MatchOrigin.DIRECT_DUEL,
                MatchEndReason.WIN,
                Optional.of(winner),
                Optional.of(loser),
                120,
                completedAt);

        assertEquals(matchId, entry.matchId());
        assertEquals(Optional.of(winner), entry.winnerId());
        assertEquals(Optional.of(loser), entry.loserId());
        assertEquals(120, entry.activeTicks());
        assertEquals(completedAt, entry.completedAt());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchHistoryEntry(
                        matchId,
                        winner,
                        loser,
                        new ArenaId("arena-one"),
                        new KitId("nodebuff"),
                        MatchOrigin.DIRECT_DUEL,
                        MatchEndReason.WIN,
                        Optional.of(winner),
                        Optional.of(winner),
                        120,
                        completedAt));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchHistoryEntry(
                        matchId,
                        winner,
                        loser,
                        new ArenaId("arena-one"),
                        new KitId("nodebuff"),
                        MatchOrigin.DIRECT_DUEL,
                        MatchEndReason.WIN,
                        Optional.of(winner),
                        Optional.of(loser),
                        -1,
                        completedAt));
    }

    @Test
    void timeoutAndShutdownEntriesRejectWinnerAndLoserValues() {
        PlayerId firstPlayer = player("timeout-one");
        PlayerId secondPlayer = player("timeout-two");

        MatchHistoryEntry timeoutEntry = new MatchHistoryEntry(
                new MatchId(UUID.nameUUIDFromBytes("timeout-entry".getBytes())),
                firstPlayer,
                secondPlayer,
                new ArenaId("arena-one"),
                new KitId("sumo"),
                MatchOrigin.QUEUE_RANKED,
                MatchEndReason.TIMEOUT,
                Optional.empty(),
                Optional.empty(),
                600,
                Instant.parse("2026-05-02T13:10:00Z"));

        assertEquals(MatchEndReason.TIMEOUT, timeoutEntry.endReason());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchHistoryEntry(
                        new MatchId(UUID.nameUUIDFromBytes("shutdown-entry".getBytes())),
                        firstPlayer,
                        secondPlayer,
                        new ArenaId("arena-one"),
                        new KitId("sumo"),
                        MatchOrigin.QUEUE_RANKED,
                        MatchEndReason.SHUTDOWN,
                        Optional.of(firstPlayer),
                        Optional.empty(),
                        600,
                        Instant.parse("2026-05-02T13:10:00Z")));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
