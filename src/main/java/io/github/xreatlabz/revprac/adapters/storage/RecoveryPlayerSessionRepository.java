package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class RecoveryPlayerSessionRepository implements PlayerSessionRepository {

    private final PlayerSessionRepository delegate;
    private final RuntimeRecoveryRepository recoveryRepository;

    public RecoveryPlayerSessionRepository(
            PlayerSessionRepository delegate,
            RuntimeRecoveryRepository recoveryRepository) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recoveryRepository = Objects.requireNonNull(recoveryRepository, "recoveryRepository");
    }

    @Override
    public Optional<PlayerSession> find(PlayerId playerId) {
        return delegate.find(playerId);
    }

    @Override
    public void save(PlayerSession session) {
        delegate.save(session);
        if (session.context() == PlayerContext.LOBBY) {
            recoveryRepository.deletePlayerSession(session.playerId());
            return;
        }
        recoveryRepository.savePlayerSession(session);
    }

    @Override
    public void delete(PlayerId playerId) {
        delegate.delete(playerId);
        recoveryRepository.deletePlayerSession(playerId);
    }

    @Override
    public Collection<PlayerSession> findAll() {
        return delegate.findAll();
    }
}
