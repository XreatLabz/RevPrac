package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class RecoveryPendingRestorationRepository implements PendingRestorationRepository {

    private final PendingRestorationRepository delegate;
    private final RuntimeRecoveryRepository recoveryRepository;

    public RecoveryPendingRestorationRepository(
            PendingRestorationRepository delegate,
            RuntimeRecoveryRepository recoveryRepository) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recoveryRepository = Objects.requireNonNull(recoveryRepository, "recoveryRepository");
    }

    @Override
    public Optional<PendingRestoration> find(PlayerId playerId) {
        return delegate.find(playerId);
    }

    @Override
    public void save(PendingRestoration restoration) {
        delegate.save(restoration);
        recoveryRepository.savePendingRestoration(restoration);
    }

    @Override
    public void delete(PlayerId playerId) {
        delegate.delete(playerId);
        recoveryRepository.deletePendingRestoration(playerId);
    }

    @Override
    public Collection<PendingRestoration> findAll() {
        return delegate.findAll();
    }
}
