package io.github.xreatlabz.revprac.domain.seasons;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Season(SeasonId id, boolean active, Instant createdAt, Optional<Instant> activatedAt) {

    public Season {
        id = Objects.requireNonNull(id, "id");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        activatedAt = Objects.requireNonNull(activatedAt, "activatedAt");
        if (active && activatedAt.isEmpty()) {
            throw new IllegalArgumentException("active season must have an activation timestamp");
        }
    }
}
