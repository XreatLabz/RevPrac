package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryMatchRepository implements MatchRepository {

    private final Object mutex = new Object();
    private final ConcurrentMap<MatchId, Match> matches = new ConcurrentHashMap<>();

    @Override
    public Optional<Match> find(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        synchronized (mutex) {
            return Optional.ofNullable(matches.get(matchId));
        }
    }

    @Override
    public Collection<Match> findAll() {
        synchronized (mutex) {
            return List.copyOf(matches.values());
        }
    }

    @Override
    public Optional<Match> findByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return matches.values().stream()
                    .filter(match -> match.participants().contains(playerId))
                    .findFirst();
        }
    }

    @Override
    public Optional<Match> findBySpectator(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return matches.values().stream()
                    .filter(match -> match.spectators().contains(playerId))
                    .findFirst();
        }
    }

    @Override
    public boolean create(Match match) {
        Objects.requireNonNull(match, "match");
        synchronized (mutex) {
            if (matches.containsKey(match.id())) {
                return false;
            }
            if (playerOccupied(match.participants().playerOne())
                    || playerOccupied(match.participants().playerTwo())
                    || spectatorOccupied(match.participants().playerOne())
                    || spectatorOccupied(match.participants().playerTwo())) {
                return false;
            }
            matches.put(match.id(), match);
            return true;
        }
    }

    @Override
    public void save(Match match) {
        Objects.requireNonNull(match, "match");
        synchronized (mutex) {
            matches.put(match.id(), match);
        }
    }

    @Override
    public void delete(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        synchronized (mutex) {
            matches.remove(matchId);
        }
    }

    private boolean playerOccupied(PlayerId playerId) {
        return matches.values().stream()
                .anyMatch(match -> match.participants().contains(playerId));
    }

    private boolean spectatorOccupied(PlayerId playerId) {
        return matches.values().stream()
                .anyMatch(match -> match.spectators().contains(playerId));
    }
}
