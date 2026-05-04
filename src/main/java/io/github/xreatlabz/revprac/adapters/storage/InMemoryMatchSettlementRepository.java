package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryMatchSettlementRepository implements MatchSettlementRepository {

    private static final Comparator<MatchHistoryEntry> RECENT_HISTORY_ORDER = Comparator
            .comparing(MatchHistoryEntry::completedAt)
            .reversed()
            .thenComparing(entry -> entry.matchId().value().toString(), Comparator.reverseOrder());

    private final Object mutex = new Object();
    private final Map<MatchId, MatchHistoryEntry> history = new HashMap<>();
    private final Map<StatsKey, PlayerKitStats> stats = new HashMap<>();
    private final Map<StatsKey, PlayerRating> ratings = new HashMap<>();

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
            for (PlayerRating rating : settlement.ratingUpdates()) {
                ratings.put(new StatsKey(rating.playerId(), rating.kitId()), rating);
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

    @Override
    public List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        synchronized (mutex) {
            return history.values().stream()
                    .filter(entry -> entry.playerOneId().equals(playerId) || entry.playerTwoId().equals(playerId))
                    .sorted(RECENT_HISTORY_ORDER)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }
    }

    private record StatsKey(PlayerId playerId, KitId kitId) {
    }
}
