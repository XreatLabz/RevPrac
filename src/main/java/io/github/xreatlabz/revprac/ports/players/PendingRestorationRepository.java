package io.github.xreatlabz.revprac.ports.players;

import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Collection;
import java.util.Optional;

public interface PendingRestorationRepository {

    Optional<PendingRestoration> find(PlayerId playerId);

    void save(PendingRestoration restoration);

    void delete(PlayerId playerId);

    Collection<PendingRestoration> findAll();
}
