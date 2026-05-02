package io.github.xreatlabz.revprac.domain.stats;

import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import java.util.List;
import java.util.Objects;

public record MatchSettlement(MatchHistoryEntry history, List<PlayerKitStatDelta> statDeltas) {

    public MatchSettlement {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(statDeltas, "statDeltas");
        statDeltas = List.copyOf(statDeltas);
        if (statDeltas.isEmpty()) {
            throw new IllegalArgumentException("match settlement must include stat deltas");
        }
        statDeltas.forEach(delta -> Objects.requireNonNull(delta, "statDelta"));
    }
}
