package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import java.util.Collection;
import java.util.Optional;

public interface PlayerSessionRepository {

    Optional<PlayerSession> find(PlayerId playerId);

    void save(PlayerSession session);

    void delete(PlayerId playerId);

    Collection<PlayerSession> findAll();
}
