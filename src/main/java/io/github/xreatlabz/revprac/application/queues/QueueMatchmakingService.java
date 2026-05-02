package io.github.xreatlabz.revprac.application.queues;

import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QueueMatchmakingService {

    private static final Comparator<QueueKey> KEY_ORDER =
            Comparator.comparing((QueueKey key) -> key.mode().name()).thenComparing(key -> key.kitId().value());
    private static final Comparator<QueueTicket> TICKET_ORDER =
            Comparator.comparingLong(QueueTicket::joinedAtTick).thenComparing(ticket -> ticket.id().value());

    private final QueueTicketRepository queueTicketRepository;
    private final MatchLifecycleService matchLifecycleService;
    private final MatchmakingWindowPolicy matchmakingWindowPolicy;
    private final QueueConfig queueConfig;
    private final AtomicBoolean intakeClosed = new AtomicBoolean(false);

    public QueueMatchmakingService(
            QueueTicketRepository queueTicketRepository,
            MatchLifecycleService matchLifecycleService,
            MatchmakingWindowPolicy matchmakingWindowPolicy,
            QueueConfig queueConfig) {
        this.queueTicketRepository = Objects.requireNonNull(queueTicketRepository, "queueTicketRepository");
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
        this.matchmakingWindowPolicy = Objects.requireNonNull(matchmakingWindowPolicy, "matchmakingWindowPolicy");
        this.queueConfig = Objects.requireNonNull(queueConfig, "queueConfig");
    }

    public void tick(long currentTick) {
        if (intakeClosed.get()) {
            return;
        }

        Map<QueueKey, List<QueueTicket>> ticketsByKey = new HashMap<>();
        for (QueueTicket ticket : queueTicketRepository.findAll()) {
            if (ticket.state() != io.github.xreatlabz.revprac.domain.queues.QueueTicketState.SEARCHING) {
                continue;
            }
            ticketsByKey.computeIfAbsent(ticket.key(), ignored -> new ArrayList<>()).add(ticket);
        }

        Set<PlayerId> usedPlayers = new HashSet<>();
        for (QueueKey queueKey : ticketsByKey.keySet().stream().sorted(KEY_ORDER).toList()) {
            List<QueueTicket> group = ticketsByKey.get(queueKey).stream().sorted(TICKET_ORDER).toList();
            for (QueueTicket anchor : group) {
                if (usedPlayers.contains(anchor.playerId())) {
                    continue;
                }
                QueueTicket candidate = selectCandidate(group, anchor, usedPlayers, currentTick);
                if (candidate == null) {
                    continue;
                }
                usedPlayers.add(anchor.playerId());
                usedPlayers.add(candidate.playerId());

                Optional<QueuedMatchAssignment> claim = queueTicketRepository.claimPair(anchor.id(), candidate.id());
                if (claim.isEmpty()) {
                    continue;
                }
                handleClaimedPair(claim.orElseThrow());
            }
        }
    }

    public void closeIntake() {
        intakeClosed.set(true);
    }

    private QueueTicket selectCandidate(
            Collection<QueueTicket> group,
            QueueTicket anchor,
            Set<PlayerId> usedPlayers,
            long currentTick) {
        if (anchor.key().mode() == QueueMode.UNRANKED) {
            for (QueueTicket candidate : group) {
                if (candidate.id().equals(anchor.id())
                        || usedPlayers.contains(candidate.playerId())
                        || !matchmakingWindowPolicy.isCompatible(
                                anchor, candidate, currentTick, queueConfig.ticksPerSecond())) {
                    continue;
                }
                return candidate;
            }
            return null;
        }

        return group.stream()
                .filter(candidate -> !candidate.id().equals(anchor.id()))
                .filter(candidate -> !usedPlayers.contains(candidate.playerId()))
                .filter(candidate -> matchmakingWindowPolicy.isCompatible(anchor, candidate, currentTick, queueConfig.ticksPerSecond()))
                .min(Comparator.comparingInt((QueueTicket candidate) -> ratingDelta(anchor, candidate))
                        .thenComparingLong(QueueTicket::joinedAtTick)
                        .thenComparing(ticket -> ticket.id().value()))
                .orElse(null);
    }

    private void handleClaimedPair(QueuedMatchAssignment assignment) {
        try {
            matchLifecycleService.startQueuedMatch(
                    assignment.first().playerId(),
                    assignment.second().playerId(),
                    assignment.kitId());
            queueTicketRepository.delete(assignment.first().id());
            queueTicketRepository.delete(assignment.second().id());
        } catch (MatchLifecycleService.ArenaUnavailableException exception) {
            queueTicketRepository.restoreSearching(assignment.first().id(), assignment.second().id());
        } catch (RuntimeException exception) {
            queueTicketRepository.delete(assignment.first().id());
            queueTicketRepository.delete(assignment.second().id());
            throw exception;
        }
    }

    private static int ratingDelta(QueueTicket first, QueueTicket second) {
        return Math.abs(first.searchRating() - second.searchRating());
    }
}
