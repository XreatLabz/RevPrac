package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Objects;
import java.util.UUID;

public record ArenaReservationId(UUID value) {

    public ArenaReservationId {
        Objects.requireNonNull(value, "value");
    }
}
