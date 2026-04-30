package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public enum PlayerSessionTransitionPolicy {
    ;

    public static boolean isAllowed(PlayerContext from, PlayerContext to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (from == to) {
            return false;
        }
        if (from == PlayerContext.LOBBY) {
            return to.isManaged();
        }
        if (to == PlayerContext.LOBBY) {
            return true;
        }
        return from.isManaged() && to.isManaged();
    }
}
