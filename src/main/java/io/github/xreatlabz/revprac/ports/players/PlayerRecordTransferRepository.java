package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.domain.players.PlayerId;

public interface PlayerRecordTransferRepository {

    PlayerRecordBundle exportBundle(PlayerId playerId);

    void importBundle(PlayerRecordBundle bundle);
}
