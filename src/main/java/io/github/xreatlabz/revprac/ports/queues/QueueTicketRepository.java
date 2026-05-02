package io.github.xreatlabz.revprac.ports.queues;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import java.util.Collection;
import java.util.Optional;

public interface QueueTicketRepository {

    Optional<QueueTicket> find(QueueTicketId ticketId);

    Collection<QueueTicket> findAll();

    Optional<QueueTicket> findByPlayer(PlayerId playerId);

    Collection<QueueTicket> findSearchingByKey(QueueKey queueKey);

    boolean create(QueueTicket ticket);

    void save(QueueTicket ticket);

    Optional<QueuedMatchAssignment> claimPair(QueueTicketId firstId, QueueTicketId secondId);

    void restoreSearching(QueueTicketId firstId, QueueTicketId secondId);

    void delete(QueueTicketId ticketId);

    void deleteByPlayer(PlayerId playerId);
}
