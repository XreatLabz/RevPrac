package io.github.xreatlabz.revprac.domain.stats;

import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import java.util.List;
import java.util.Objects;

public record MatchSettlement(
        MatchHistoryEntry history,
        List<PlayerKitStatDelta> statDeltas,
        List<PlayerRating> ratingUpdates) {

    public MatchSettlement(MatchHistoryEntry history, List<PlayerKitStatDelta> statDeltas) {
        this(history, statDeltas, List.of());
    }

    public MatchSettlement {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(statDeltas, "statDeltas");
        Objects.requireNonNull(ratingUpdates, "ratingUpdates");
        statDeltas = List.copyOf(statDeltas);
        ratingUpdates = List.copyOf(ratingUpdates);
        if (statDeltas.isEmpty()) {
            throw new IllegalArgumentException("match settlement must include stat deltas");
        }
        statDeltas.forEach(delta -> Objects.requireNonNull(delta, "statDelta"));
        ratingUpdates.forEach(rating -> Objects.requireNonNull(rating, "ratingUpdate"));
    }
}
