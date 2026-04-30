package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryPendingRestorationRepository implements PendingRestorationRepository {

    private final ConcurrentMap<PlayerId, PendingRestoration> restorations = new ConcurrentHashMap<>();

    @Override
    public Optional<PendingRestoration> find(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(restorations.get(playerId));
    }

    @Override
    public void save(PendingRestoration restoration) {
        Objects.requireNonNull(restoration, "restoration");
        restorations.put(restoration.playerId(), restoration);
    }

    @Override
    public void delete(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        restorations.remove(playerId);
    }

    @Override
    public Collection<PendingRestoration> findAll() {
        return List.copyOf(restorations.values());
    }
}
