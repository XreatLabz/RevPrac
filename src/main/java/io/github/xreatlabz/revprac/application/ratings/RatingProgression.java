package io.github.xreatlabz.revprac.application.ratings;

import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import java.util.List;
import java.util.Objects;

public record RatingProgression(PlayerRating winner, PlayerRating loser) {

    public RatingProgression {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        if (winner.playerId().equals(loser.playerId())) {
            throw new IllegalArgumentException("winner and loser must be different players");
        }
        if (!winner.kitId().equals(loser.kitId())) {
            throw new IllegalArgumentException("winner and loser must share the same kit");
        }
    }

    public List<PlayerRating> asList() {
        return List.of(winner, loser);
    }
}
