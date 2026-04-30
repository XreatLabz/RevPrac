package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;
import java.util.UUID;

public record PlayerId(UUID value) {

    public PlayerId {
        Objects.requireNonNull(value, "value");
    }
}
