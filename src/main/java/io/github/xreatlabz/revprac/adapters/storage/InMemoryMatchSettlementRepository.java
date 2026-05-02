package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryMatchSettlementRepository implements MatchSettlementRepository {

    private final Object mutex = new Object();
    private final Map<MatchId, MatchHistoryEntry> history = new HashMap<>();
    private final Map<StatsKey, PlayerKitStats> stats = new HashMap<>();

    @Override
    public void record(MatchSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        synchronized (mutex) {
            if (history.containsKey(settlement.history().matchId())) {
                return;
            }
            history.put(settlement.history().matchId(), settlement.history());
            for (PlayerKitStatDelta delta : settlement.statDeltas()) {
                StatsKey key = new StatsKey(delta.playerId(), delta.kitId());
                PlayerKitStats current = stats.get(key);
                stats.put(key, current == null ? delta.toStats() : delta.applyTo(current));
            }
        }
    }

    @Override
    public Optional<MatchHistoryEntry> findHistory(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        synchronized (mutex) {
            return Optional.ofNullable(history.get(matchId));
        }
    }

    @Override
    public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        synchronized (mutex) {
            return Optional.ofNullable(stats.get(new StatsKey(playerId, kitId)));
        }
    }

    private record StatsKey(PlayerId playerId, KitId kitId) {
    }
}
