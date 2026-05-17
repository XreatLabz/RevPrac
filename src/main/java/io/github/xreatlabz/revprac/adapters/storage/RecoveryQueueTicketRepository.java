package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class RecoveryQueueTicketRepository implements QueueTicketRepository {

    private final QueueTicketRepository delegate;
    private final RuntimeRecoveryRepository recoveryRepository;
    private final Clock clock;

    public RecoveryQueueTicketRepository(
            QueueTicketRepository delegate,
            RuntimeRecoveryRepository recoveryRepository,
            Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recoveryRepository = Objects.requireNonNull(recoveryRepository, "recoveryRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<QueueTicket> find(QueueTicketId ticketId) {
        return delegate.find(ticketId);
    }

    @Override
    public Collection<QueueTicket> findAll() {
        return delegate.findAll();
    }

    @Override
    public Optional<QueueTicket> findByPlayer(PlayerId playerId) {
        return delegate.findByPlayer(playerId);
    }

    @Override
    public Collection<QueueTicket> findSearchingByKey(QueueKey queueKey) {
        return delegate.findSearchingByKey(queueKey);
    }

    @Override
    public boolean create(QueueTicket ticket) {
        boolean created = delegate.create(ticket);
        if (created) {
            recoveryRepository.saveQueueTicket(ticket, clock.instant());
        }
        return created;
    }

    @Override
    public void save(QueueTicket ticket) {
        delegate.save(ticket);
        recoveryRepository.saveQueueTicket(ticket, clock.instant());
    }

    @Override
    public Optional<QueuedMatchAssignment> claimPair(QueueTicketId firstId, QueueTicketId secondId) {
        Optional<QueuedMatchAssignment> assignment = delegate.claimPair(firstId, secondId);
        assignment.ifPresent(value -> {
            recoveryRepository.saveQueueTicket(value.first(), clock.instant());
            recoveryRepository.saveQueueTicket(value.second(), clock.instant());
        });
        return assignment;
    }

    @Override
    public void restoreSearching(QueueTicketId firstId, QueueTicketId secondId) {
        delegate.restoreSearching(firstId, secondId);
        delegate.find(firstId).ifPresent(ticket -> recoveryRepository.saveQueueTicket(ticket, clock.instant()));
        delegate.find(secondId).ifPresent(ticket -> recoveryRepository.saveQueueTicket(ticket, clock.instant()));
    }

    @Override
    public void delete(QueueTicketId ticketId) {
        delegate.delete(ticketId);
        recoveryRepository.deleteQueueTicket(ticketId);
    }

    @Override
    public void deleteByPlayer(PlayerId playerId) {
        delegate.deleteByPlayer(playerId);
        recoveryRepository.deleteQueueTicketByPlayer(playerId);
    }
}
