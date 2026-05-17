package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class RecoveryMatchRepository implements MatchRepository {

    private final MatchRepository delegate;
    private final RuntimeRecoveryRepository recoveryRepository;

    public RecoveryMatchRepository(MatchRepository delegate, RuntimeRecoveryRepository recoveryRepository) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recoveryRepository = Objects.requireNonNull(recoveryRepository, "recoveryRepository");
    }

    @Override
    public Optional<Match> find(MatchId matchId) {
        return delegate.find(matchId);
    }

    @Override
    public Collection<Match> findAll() {
        return delegate.findAll();
    }

    @Override
    public Optional<Match> findByPlayer(PlayerId playerId) {
        return delegate.findByPlayer(playerId);
    }

    @Override
    public Optional<Match> findBySpectator(PlayerId playerId) {
        return delegate.findBySpectator(playerId);
    }

    @Override
    public boolean create(Match match) {
        boolean created = delegate.create(match);
        if (created) {
            recoveryRepository.saveMatch(match);
        }
        return created;
    }

    @Override
    public void save(Match match) {
        delegate.save(match);
        recoveryRepository.saveMatch(match);
    }

    @Override
    public void delete(MatchId matchId) {
        delegate.delete(matchId);
        recoveryRepository.deleteMatch(matchId);
    }
}
