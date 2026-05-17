package io.github.xreatlabz.revprac.domain.seasons;

import java.util.Objects;

public record SeasonId(String value) {

    public SeasonId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("season id must not be blank");
        }
    }
}
