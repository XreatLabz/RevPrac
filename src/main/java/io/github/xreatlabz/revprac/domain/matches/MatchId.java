package io.github.xreatlabz.revprac.domain.matches;

import java.util.Objects;
import java.util.UUID;

public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "value");
    }
}
