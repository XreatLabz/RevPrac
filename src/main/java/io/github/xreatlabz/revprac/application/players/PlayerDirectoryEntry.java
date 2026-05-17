package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;

public record PlayerDirectoryEntry(PlayerId playerId, String displayName) {

    public PlayerDirectoryEntry {
        Objects.requireNonNull(playerId, "playerId");
        displayName = requireNonBlank(displayName, "displayName");
    }

    public String displayLabel() {
        return displayName + " (" + playerId.value() + ")";
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
