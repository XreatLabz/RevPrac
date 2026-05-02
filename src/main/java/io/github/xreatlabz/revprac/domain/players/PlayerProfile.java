package io.github.xreatlabz.revprac.domain.players;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PlayerProfile(
        PlayerId playerId,
        Optional<String> lastKnownName,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        lastKnownName = lastKnownName.map(PlayerProfile::normalizeName);
        if (lastSeenAt.isBefore(firstSeenAt)) {
            throw new IllegalArgumentException("lastSeenAt must not be before firstSeenAt");
        }
    }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "lastKnownName value");
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("lastKnownName must not be blank");
        }
        return normalized;
    }
}
