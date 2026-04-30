package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public record PlayerSession(PlayerId playerId, PlayerContext context, PlayerSafetySnapshot returnSnapshot) {

    public PlayerSession {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(context, "context");
        if (context.isManaged() && returnSnapshot == null) {
            throw new IllegalArgumentException("Managed sessions require a return snapshot");
        }
        if (!context.isManaged() && returnSnapshot != null) {
            throw new IllegalArgumentException("Lobby sessions must not retain a return snapshot");
        }
    }

    public boolean isManaged() {
        return context.isManaged();
    }
}
