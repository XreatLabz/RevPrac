package io.github.xreatlabz.revprac.application.ratings;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RatingProgression(
        PlayerRating winnerBefore,
        PlayerRating winner,
        PlayerRating loserBefore,
        PlayerRating loser) {

    public RatingProgression {
        Objects.requireNonNull(winnerBefore, "winnerBefore");
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loserBefore, "loserBefore");
        Objects.requireNonNull(loser, "loser");
        if (winner.playerId().equals(loser.playerId())) {
            throw new IllegalArgumentException("winner and loser must be different players");
        }
        if (!winnerBefore.playerId().equals(winner.playerId())) {
            throw new IllegalArgumentException("winner before/after players must match");
        }
        if (!loserBefore.playerId().equals(loser.playerId())) {
            throw new IllegalArgumentException("loser before/after players must match");
        }
        if (!winnerBefore.kitId().equals(winner.kitId())
                || !loserBefore.kitId().equals(loser.kitId())
                || !winner.kitId().equals(loser.kitId())) {
            throw new IllegalArgumentException("rating progression entries must share the same kit");
        }
    }

    public List<PlayerRating> asList() {
        return List.of(winner, loser);
    }

    public Optional<RatingChange> changeFor(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (winner.playerId().equals(playerId)) {
            return Optional.of(new RatingChange(playerId, winner.rating(), winner.rating() - winnerBefore.rating()));
        }
        if (loser.playerId().equals(playerId)) {
            return Optional.of(new RatingChange(playerId, loser.rating(), loser.rating() - loserBefore.rating()));
        }
        return Optional.empty();
    }

    public record RatingChange(PlayerId playerId, int newRating, int delta) {
        public RatingChange {
            Objects.requireNonNull(playerId, "playerId");
            if (newRating <= 0) {
                throw new IllegalArgumentException("newRating must be positive");
            }
        }
    }
}
