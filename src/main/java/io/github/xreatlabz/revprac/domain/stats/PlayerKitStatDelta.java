package io.github.xreatlabz.revprac.domain.stats;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;

public record PlayerKitStatDelta(
        PlayerId playerId,
        KitId kitId,
        long matchesPlayed,
        long wins,
        long losses,
        long forfeits,
        long timeouts,
        long shutdowns,
        Instant updatedAt) {

    public PlayerKitStatDelta {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireNonNegative(matchesPlayed, "matchesPlayed");
        requireNonNegative(wins, "wins");
        requireNonNegative(losses, "losses");
        requireNonNegative(forfeits, "forfeits");
        requireNonNegative(timeouts, "timeouts");
        requireNonNegative(shutdowns, "shutdowns");
        if (matchesPlayed + wins + losses + forfeits + timeouts + shutdowns <= 0) {
            throw new IllegalArgumentException("stat delta must increment at least one counter");
        }
    }

    public PlayerKitStats applyTo(PlayerKitStats current) {
        Objects.requireNonNull(current, "current");
        if (!playerId.equals(current.playerId()) || !kitId.equals(current.kitId())) {
            throw new IllegalArgumentException("stat delta target does not match current stats");
        }
        return new PlayerKitStats(
                playerId,
                kitId,
                current.matchesPlayed() + matchesPlayed,
                current.wins() + wins,
                current.losses() + losses,
                current.forfeits() + forfeits,
                current.timeouts() + timeouts,
                current.shutdowns() + shutdowns,
                updatedAt);
    }

    public PlayerKitStats toStats() {
        return new PlayerKitStats(
                playerId,
                kitId,
                matchesPlayed,
                wins,
                losses,
                forfeits,
                timeouts,
                shutdowns,
                updatedAt);
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
