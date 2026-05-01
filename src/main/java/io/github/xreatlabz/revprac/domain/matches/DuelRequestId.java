package io.github.xreatlabz.revprac.domain.matches;

import java.util.Objects;
import java.util.UUID;

public record DuelRequestId(UUID value) {

    public DuelRequestId {
        Objects.requireNonNull(value, "value");
    }
}
