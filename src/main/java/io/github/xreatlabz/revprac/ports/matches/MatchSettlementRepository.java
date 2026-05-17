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

    boolean record(MatchSettlement settlement);

    Optional<MatchHistoryEntry> findHistory(MatchId matchId);

    Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId);

    List<PlayerKitStats> findStatsByPlayer(PlayerId playerId);

    List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset);

    List<MatchHistoryEntry> findAllHistory(PlayerId playerId);

    void validateImportHistoryCompatibility(PlayerId playerId, List<MatchHistoryEntry> history);

    void importPlayerRecords(
            PlayerId playerId,
            List<PlayerKitStats> stats,
            List<MatchHistoryEntry> history);

    void restoreImportedPlayerRecords(
            PlayerId playerId,
            List<PlayerKitStats> stats,
            List<MatchHistoryEntry> history);
}
