package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryPlayerSessionRepository implements PlayerSessionRepository {

    private final ConcurrentMap<PlayerId, PlayerSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<PlayerSession> find(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(sessions.get(playerId));
    }

    @Override
    public void save(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.playerId(), session);
    }

    @Override
    public void delete(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        sessions.remove(playerId);
    }

    @Override
    public Collection<PlayerSession> findAll() {
        return List.copyOf(sessions.values());
    }
}
