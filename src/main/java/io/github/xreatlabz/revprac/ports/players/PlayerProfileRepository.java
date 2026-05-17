package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import java.util.List;
import java.util.Optional;

public interface PlayerProfileRepository {

    Optional<PlayerProfile> find(PlayerId playerId);

    List<PlayerProfile> findByLastKnownNameIgnoreCase(String lastKnownName);

    void delete(PlayerId playerId);

    void restoreExact(PlayerProfile profile);

    void upsert(PlayerProfile profile);
}
