package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.application.ratings.RatingProgression;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import java.util.Objects;
import java.util.Optional;

public record MatchSettlementResult(
        MatchSettlement settlement,
        boolean applied,
        Optional<RatingProgression> ratingProgression) {

    public MatchSettlementResult {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(ratingProgression, "ratingProgression");
    }

    public MatchSettlementResult withApplied(boolean applied) {
        return new MatchSettlementResult(settlement, applied, ratingProgression);
    }

    public Optional<RatingProgression.RatingChange> ratingChangeFor(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!applied) {
            return Optional.empty();
        }
        return ratingProgression.flatMap(progression -> progression.changeFor(playerId));
    }
}
