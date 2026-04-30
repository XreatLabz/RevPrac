package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public record PendingRestoration(PlayerId playerId, PlayerSafetySnapshot snapshot, TransitionReason reason) {

    public PendingRestoration {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(reason, "reason");
    }
}
