package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.tournaments.Tournament;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentId;
import io.github.xreatlabz.revprac.ports.tournaments.TournamentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryTournamentRepository implements TournamentRepository {

    private final Object mutex = new Object();
    private final ConcurrentMap<TournamentId, Tournament> tournaments = new ConcurrentHashMap<>();

    @Override
    public Optional<Tournament> find(TournamentId tournamentId) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        synchronized (mutex) {
            return Optional.ofNullable(tournaments.get(tournamentId));
        }
    }

    @Override
    public Collection<Tournament> findAll() {
        synchronized (mutex) {
            return List.copyOf(tournaments.values());
        }
    }

    @Override
    public boolean create(Tournament tournament) {
        Objects.requireNonNull(tournament, "tournament");
        synchronized (mutex) {
            if (tournaments.containsKey(tournament.id())) {
                return false;
            }
            tournaments.put(tournament.id(), tournament);
            return true;
        }
    }

    @Override
    public void save(Tournament tournament) {
        Objects.requireNonNull(tournament, "tournament");
        synchronized (mutex) {
            tournaments.put(tournament.id(), tournament);
        }
    }

    @Override
    public void delete(TournamentId tournamentId) {
        Objects.requireNonNull(tournamentId, "tournamentId");
        synchronized (mutex) {
            tournaments.remove(tournamentId);
        }
    }
}
