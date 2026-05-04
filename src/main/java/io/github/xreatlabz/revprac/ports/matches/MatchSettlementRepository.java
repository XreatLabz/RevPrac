package io.github.xreatlabz.revprac.ports.matches;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import java.util.List;
import java.util.Optional;

public interface MatchSettlementRepository {

    void record(MatchSettlement settlement);

    Optional<MatchHistoryEntry> findHistory(MatchId matchId);

    Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId);

    default List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
        throw new UnsupportedOperationException("recent history is not implemented");
    }
}
