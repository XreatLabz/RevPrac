package io.github.xreatlabz.revprac.ports.tournaments;

import io.github.xreatlabz.revprac.domain.tournaments.Tournament;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentId;
import java.util.Collection;
import java.util.Optional;

public interface TournamentRepository {

    Optional<Tournament> find(TournamentId tournamentId);

    Collection<Tournament> findAll();

    boolean create(Tournament tournament);

    void save(Tournament tournament);

    void delete(TournamentId tournamentId);
}
