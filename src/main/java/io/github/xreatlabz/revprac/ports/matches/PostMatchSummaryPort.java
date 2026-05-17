package io.github.xreatlabz.revprac.ports.matches;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Optional;

public interface PostMatchSummaryPort {

    Optional<String> playerName(PlayerId playerId);

    void send(PlayerId playerId, String message);
}
