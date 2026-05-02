package io.github.xreatlabz.revprac.domain.stats;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;

public record PlayerKitStats(
        PlayerId playerId,
        KitId kitId,
        long matchesPlayed,
        long wins,
        long losses,
        long forfeits,
        long timeouts,
        long shutdowns,
        Instant updatedAt) {

    public PlayerKitStats {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireNonNegative(matchesPlayed, "matchesPlayed");
        requireNonNegative(wins, "wins");
        requireNonNegative(losses, "losses");
        requireNonNegative(forfeits, "forfeits");
        requireNonNegative(timeouts, "timeouts");
        requireNonNegative(shutdowns, "shutdowns");
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
