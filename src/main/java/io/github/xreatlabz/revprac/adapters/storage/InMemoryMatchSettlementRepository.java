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
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    public boolean record(MatchSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        synchronized (mutex) {
            if (history.containsKey(settlement.history().matchId())) {
                return false;
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
            return true;
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
    public List<PlayerKitStats> findStatsByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return stats.values().stream()
                    .filter(value -> value.playerId().equals(playerId))
                    .sorted(Comparator.comparing(value -> value.kitId().value()))
                    .toList();
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

    @Override
    public List<MatchHistoryEntry> findAllHistory(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return history.values().stream()
                    .filter(entry -> entry.playerOneId().equals(playerId) || entry.playerTwoId().equals(playerId))
                    .sorted(RECENT_HISTORY_ORDER)
                    .toList();
        }
    }

    @Override
    public void validateImportHistoryCompatibility(PlayerId playerId, List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateImportedHistory(playerId, importedHistory);
        synchronized (mutex) {
            validateHistoryCompatibilityLocked(playerId, importedHistory);
        }
    }

    @Override
    public void importPlayerRecords(PlayerId playerId, List<PlayerKitStats> replacementStats, List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateReplacementStats(playerId, replacementStats);
        validateImportedHistory(playerId, importedHistory);
        synchronized (mutex) {
            validateHistoryCompatibilityLocked(playerId, importedHistory);
            stats.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
            for (PlayerKitStats playerKitStats : replacementStats) {
                stats.put(new StatsKey(playerKitStats.playerId(), playerKitStats.kitId()), playerKitStats);
            }
            for (MatchHistoryEntry historyEntry : importedHistory) {
                history.putIfAbsent(historyEntry.matchId(), historyEntry);
            }
        }
    }

    @Override
    public void restoreImportedPlayerRecords(
            PlayerId playerId,
            List<PlayerKitStats> replacementStats,
            List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateReplacementStats(playerId, replacementStats);
        validateImportedHistory(playerId, importedHistory);
        synchronized (mutex) {
            stats.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
            for (PlayerKitStats playerKitStats : replacementStats) {
                stats.put(new StatsKey(playerKitStats.playerId(), playerKitStats.kitId()), playerKitStats);
            }
            Set<MatchId> importedMatchIds = new HashSet<>();
            for (MatchHistoryEntry historyEntry : importedHistory) {
                importedMatchIds.add(historyEntry.matchId());
            }
            history.entrySet().removeIf(entry ->
                    entryInvolvesPlayer(entry.getValue(), playerId) && !importedMatchIds.contains(entry.getKey()));
            for (MatchHistoryEntry historyEntry : importedHistory) {
                MatchHistoryEntry existingEntry = history.get(historyEntry.matchId());
                if (existingEntry != null && !existingEntry.equals(historyEntry)) {
                    throw new IllegalStateException(
                            "Player record restore conflict for "
                                    + playerId.value()
                                    + ": existing match-id "
                                    + historyEntry.matchId().value()
                                    + " differs from the rollback history entry.");
                }
                history.put(historyEntry.matchId(), historyEntry);
            }
        }
    }

    private static void validateReplacementStats(PlayerId playerId, List<PlayerKitStats> replacementStats) {
        for (PlayerKitStats playerKitStats : replacementStats) {
            Objects.requireNonNull(playerKitStats, "replacementStats entry");
            if (!playerKitStats.playerId().equals(playerId)) {
                throw new IllegalArgumentException("stats player does not match import player");
            }
        }
    }

    private static void validateImportedHistory(PlayerId playerId, List<MatchHistoryEntry> importedHistory) {
        for (MatchHistoryEntry historyEntry : importedHistory) {
            Objects.requireNonNull(historyEntry, "importedHistory entry");
            if (!entryInvolvesPlayer(historyEntry, playerId)) {
                throw new IllegalArgumentException("history entry does not include import player");
            }
        }
    }

    private void validateHistoryCompatibilityLocked(PlayerId playerId, List<MatchHistoryEntry> importedHistory) {
        Set<MatchId> importedMatchIds = new HashSet<>();
        for (MatchHistoryEntry historyEntry : importedHistory) {
            importedMatchIds.add(historyEntry.matchId());
        }
        for (MatchHistoryEntry existingEntry : history.values()) {
            if (entryInvolvesPlayer(existingEntry, playerId) && !importedMatchIds.contains(existingEntry.matchId())) {
                throw new IllegalStateException(
                        "Player record import history conflict for "
                                + playerId.value()
                                + ": existing current-season history contains rows not present in the imported bundle.");
            }
        }
        for (MatchHistoryEntry historyEntry : importedHistory) {
            MatchHistoryEntry existingEntry = history.get(historyEntry.matchId());
            if (existingEntry != null && !existingEntry.equals(historyEntry)) {
                throw new IllegalStateException(
                        "Player record import history conflict for "
                                + playerId.value()
                                + ": existing match-id "
                                + historyEntry.matchId().value()
                                + " differs from the imported history entry.");
            }
        }
    }

    private static boolean entryInvolvesPlayer(MatchHistoryEntry historyEntry, PlayerId playerId) {
        return historyEntry.playerOneId().equals(playerId) || historyEntry.playerTwoId().equals(playerId);
    }

    private record StatsKey(PlayerId playerId, KitId kitId) {
    }
}
