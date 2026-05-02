package io.github.xreatlabz.revprac.domain.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerKitStatsTest {

    @Test
    void playerKitStatsRejectNegativeCounters() {
        PlayerId playerId = player("stats-player");
        KitId kitId = new KitId("nodebuff");
        Instant updatedAt = Instant.parse("2026-05-02T13:15:00Z");

        PlayerKitStats stats = new PlayerKitStats(playerId, kitId, 10, 6, 4, 1, 2, 0, updatedAt);

        assertEquals(10, stats.matchesPlayed());
        assertEquals(6, stats.wins());
        assertEquals(4, stats.losses());
        assertEquals(1, stats.forfeits());
        assertEquals(2, stats.timeouts());
        assertEquals(0, stats.shutdowns());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerKitStats(playerId, kitId, -1, 0, 0, 0, 0, 0, updatedAt));
    }

    @Test
    void matchSettlementCopiesStatDeltas() {
        PlayerId firstPlayer = player("settlement-one");
        PlayerId secondPlayer = player("settlement-two");
        PlayerKitStatDelta firstDelta = new PlayerKitStatDelta(
                firstPlayer, new KitId("nodebuff"), 1, 1, 0, 0, 0, 0, Instant.parse("2026-05-02T13:20:00Z"));
        PlayerKitStatDelta secondDelta = new PlayerKitStatDelta(
                secondPlayer, new KitId("nodebuff"), 1, 0, 1, 0, 0, 0, Instant.parse("2026-05-02T13:20:00Z"));
        MatchSettlement settlement = new MatchSettlement(nullHistory(), List.of(firstDelta, secondDelta));

        assertEquals(List.of(firstDelta, secondDelta), settlement.statDeltas());
        assertThrows(UnsupportedOperationException.class, () -> settlement.statDeltas().add(firstDelta));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerKitStatDelta(
                        firstPlayer,
                        new KitId("nodebuff"),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.parse("2026-05-02T13:20:00Z")));
    }

    private static io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry nullHistory() {
        PlayerId firstPlayer = player("history-one");
        PlayerId secondPlayer = player("history-two");
        return new io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry(
                new io.github.xreatlabz.revprac.domain.matches.MatchId(UUID.nameUUIDFromBytes("history".getBytes())),
                firstPlayer,
                secondPlayer,
                new io.github.xreatlabz.revprac.domain.arenas.ArenaId("arena-one"),
                new KitId("nodebuff"),
                io.github.xreatlabz.revprac.domain.matches.MatchOrigin.DIRECT_DUEL,
                io.github.xreatlabz.revprac.domain.matches.MatchEndReason.WIN,
                java.util.Optional.of(firstPlayer),
                java.util.Optional.of(secondPlayer),
                20,
                Instant.parse("2026-05-02T13:20:00Z"));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
