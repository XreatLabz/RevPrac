package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryQueueTicketRepository implements QueueTicketRepository {

    private static final Comparator<QueueTicket> TICKET_ORDER = Comparator.comparingLong(QueueTicket::joinedAtTick)
            .thenComparing(ticket -> ticket.id().value());

    private final Object mutex = new Object();
    private final ConcurrentMap<QueueTicketId, QueueTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public Optional<QueueTicket> find(QueueTicketId ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        synchronized (mutex) {
            return Optional.ofNullable(tickets.get(ticketId));
        }
    }

    @Override
    public Collection<QueueTicket> findAll() {
        synchronized (mutex) {
            return tickets.values().stream().sorted(TICKET_ORDER).toList();
        }
    }

    @Override
    public Optional<QueueTicket> findByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return tickets.values().stream()
                    .filter(ticket -> ticket.playerId().equals(playerId))
                    .filter(InMemoryQueueTicketRepository::isActive)
                    .sorted(TICKET_ORDER)
                    .findFirst();
        }
    }

    @Override
    public Collection<QueueTicket> findSearchingByKey(QueueKey queueKey) {
        Objects.requireNonNull(queueKey, "queueKey");
        synchronized (mutex) {
            return tickets.values().stream()
                    .filter(ticket -> ticket.key().equals(queueKey))
                    .filter(ticket -> ticket.state() == QueueTicketState.SEARCHING)
                    .sorted(TICKET_ORDER)
                    .toList();
        }
    }

    @Override
    public boolean create(QueueTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        synchronized (mutex) {
            if (tickets.containsKey(ticket.id())) {
                return false;
            }
            if (isActive(ticket) && activeTicketExists(ticket.playerId())) {
                return false;
            }
            tickets.put(ticket.id(), ticket);
            return true;
        }
    }

    @Override
    public void save(QueueTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        synchronized (mutex) {
            if (differentActiveTicketExists(ticket)) {
                throw new IllegalStateException("player already has an active queue ticket");
            }
            tickets.put(ticket.id(), ticket);
        }
    }

    @Override
    public Optional<QueuedMatchAssignment> claimPair(QueueTicketId firstId, QueueTicketId secondId) {
        Objects.requireNonNull(firstId, "firstId");
        Objects.requireNonNull(secondId, "secondId");
        synchronized (mutex) {
            if (firstId.equals(secondId)) {
                return Optional.empty();
            }

            QueueTicket first = tickets.get(firstId);
            QueueTicket second = tickets.get(secondId);
            if (first == null || second == null) {
                return Optional.empty();
            }
            if (first.state() != QueueTicketState.SEARCHING || second.state() != QueueTicketState.SEARCHING) {
                return Optional.empty();
            }
            if (!first.key().equals(second.key())) {
                return Optional.empty();
            }
            if (first.playerId().equals(second.playerId())) {
                return Optional.empty();
            }

            QueueTicket firstPairing = first.markPairing();
            QueueTicket secondPairing = second.markPairing();
            tickets.put(firstId, firstPairing);
            tickets.put(secondId, secondPairing);
            int ratingDelta = Math.abs(firstPairing.searchRating() - secondPairing.searchRating());
            return Optional.of(new QueuedMatchAssignment(
                    firstPairing,
                    secondPairing,
                    firstPairing.key().mode(),
                    firstPairing.key().kitId(),
                    ratingDelta));
        }
    }

    @Override
    public void restoreSearching(QueueTicketId firstId, QueueTicketId secondId) {
        Objects.requireNonNull(firstId, "firstId");
        Objects.requireNonNull(secondId, "secondId");
        synchronized (mutex) {
            restoreSearching(firstId);
            restoreSearching(secondId);
        }
    }

    @Override
    public void delete(QueueTicketId ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        synchronized (mutex) {
            tickets.remove(ticketId);
        }
    }

    @Override
    public void deleteByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            tickets.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        }
    }

    private void restoreSearching(QueueTicketId ticketId) {
        QueueTicket ticket = tickets.get(ticketId);
        if (ticket != null && ticket.state() == QueueTicketState.PAIRING) {
            tickets.put(
                    ticketId,
                    new QueueTicket(
                            ticket.id(),
                            ticket.playerId(),
                            ticket.key(),
                            ticket.joinedAtTick(),
                            ticket.searchRating(),
                            QueueTicketState.SEARCHING));
        }
    }

    private boolean activeTicketExists(PlayerId playerId) {
        return tickets.values().stream()
                .filter(ticket -> ticket.playerId().equals(playerId))
                .anyMatch(InMemoryQueueTicketRepository::isActive);
    }

    private boolean differentActiveTicketExists(QueueTicket ticket) {
        return tickets.values().stream()
                .filter(stored -> !stored.id().equals(ticket.id()))
                .filter(stored -> stored.playerId().equals(ticket.playerId()))
                .anyMatch(InMemoryQueueTicketRepository::isActive);
    }

    private static boolean isActive(QueueTicket ticket) {
        return ticket.state() == QueueTicketState.SEARCHING || ticket.state() == QueueTicketState.PAIRING;
    }
}
