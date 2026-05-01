package io.github.xreatlabz.revprac.ports.matches;

import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Collection;
import java.util.Optional;

public interface DuelRequestRepository {

    Optional<DuelRequest> find(DuelRequestId requestId);

    Optional<DuelRequest> findByPlayers(PlayerId requesterId, PlayerId targetId);

    Optional<DuelRequest> findPendingByPlayers(PlayerId requesterId, PlayerId targetId);

    Collection<DuelRequest> findAll();

    boolean create(DuelRequest duelRequest);

    void save(DuelRequest duelRequest);

    void delete(DuelRequestId requestId);
}
