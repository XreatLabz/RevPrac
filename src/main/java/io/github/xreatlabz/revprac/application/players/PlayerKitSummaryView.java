package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import java.util.Objects;
import java.util.Optional;

public record PlayerKitSummaryView(
        KitId kitId,
        String displayName,
        long matchesPlayed,
        long wins,
        long losses,
        long forfeits,
        long timeouts,
        long shutdowns,
        Optional<PlayerRatingView> rating) {

    public PlayerKitSummaryView {
        Objects.requireNonNull(kitId, "kitId");
        displayName = requireNonBlank(displayName, "displayName");
        requireNonNegative(matchesPlayed, "matchesPlayed");
        requireNonNegative(wins, "wins");
        requireNonNegative(losses, "losses");
        requireNonNegative(forfeits, "forfeits");
        requireNonNegative(timeouts, "timeouts");
        requireNonNegative(shutdowns, "shutdowns");
        Objects.requireNonNull(rating, "rating");
        rating.ifPresent(value -> Objects.requireNonNull(value, "rating value"));
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
