package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Objects;

public record ArenaReservation(ArenaReservationId reservationId, ArenaId arenaId, String ownerKey) {

    public ArenaReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(arenaId, "arenaId");
        ownerKey = requireNonBlank(ownerKey, "ownerKey");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
