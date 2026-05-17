package io.github.xreatlabz.revprac.domain.parties;

import java.util.Objects;
import java.util.UUID;

public record PartyId(UUID value) {

    public PartyId {
        Objects.requireNonNull(value, "value");
    }
}
