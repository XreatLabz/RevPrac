package io.github.xreatlabz.revprac.ports.matches;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Collection;
import java.util.Optional;

public interface MatchRepository {

    Optional<Match> find(MatchId matchId);

    Collection<Match> findAll();

    Optional<Match> findByPlayer(PlayerId playerId);

    Optional<Match> findBySpectator(PlayerId playerId);

    boolean create(Match match);

    void save(Match match);

    void delete(MatchId matchId);
}
