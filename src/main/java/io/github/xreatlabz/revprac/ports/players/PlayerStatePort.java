package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;

public interface PlayerStatePort {

    PlayerSafetySnapshot capture(PlayerId playerId);

    void restore(PlayerId playerId, PlayerSafetySnapshot snapshot);

    boolean isOnline(PlayerId playerId);
}
