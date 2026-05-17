package io.github.xreatlabz.revprac.domain.tournaments;

import java.util.Objects;
import java.util.UUID;

public record TournamentId(UUID value) {

    public TournamentId {
        Objects.requireNonNull(value, "value");
    }
}
