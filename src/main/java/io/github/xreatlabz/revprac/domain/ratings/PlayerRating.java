package io.github.xreatlabz.revprac.domain.ratings;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;

public record PlayerRating(
        PlayerId playerId,
        KitId kitId,
        int rating,
        int wins,
        int losses,
        Instant updatedAt) {

    public PlayerRating {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rating <= 0) {
            throw new IllegalArgumentException("rating must be positive");
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins must be non-negative");
        }
        if (losses < 0) {
            throw new IllegalArgumentException("losses must be non-negative");
        }
    }
}
