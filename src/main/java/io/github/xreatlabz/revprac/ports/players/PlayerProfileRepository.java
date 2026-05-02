package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import java.util.Optional;

public interface PlayerProfileRepository {

    Optional<PlayerProfile> find(PlayerId playerId);

    void upsert(PlayerProfile profile);
}
