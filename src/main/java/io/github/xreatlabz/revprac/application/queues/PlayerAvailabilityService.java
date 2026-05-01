package io.github.xreatlabz.revprac.application.queues;

import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.matches.DuelRequestRepository;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.util.Objects;

public final class PlayerAvailabilityService {

    private final MatchRepository matchRepository;
    private final DuelRequestRepository duelRequestRepository;
    private final QueueTicketRepository queueTicketRepository;

    public PlayerAvailabilityService(
            MatchRepository matchRepository,
            DuelRequestRepository duelRequestRepository,
            QueueTicketRepository queueTicketRepository) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.duelRequestRepository = Objects.requireNonNull(duelRequestRepository, "duelRequestRepository");
        this.queueTicketRepository = Objects.requireNonNull(queueTicketRepository, "queueTicketRepository");
    }

    public void requireAvailableForQueue(PlayerId playerId) {
        requireAvailable(playerId, "player");
    }

    public void requireAvailableForDuel(PlayerId playerId, String role) {
        Objects.requireNonNull(role, "role");
        requireAvailable(playerId, role);
    }

    public boolean isQueued(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return queueTicketRepository.findByPlayer(playerId).filter(PlayerAvailabilityService::isActiveTicket).isPresent();
    }

    private void requireAvailable(PlayerId playerId, String role) {
        Objects.requireNonNull(playerId, "playerId");
        if (hasActiveMatch(playerId) || hasPendingDuelRequest(playerId) || isQueued(playerId)) {
            throw new IllegalStateException(role + " is already busy");
        }
    }

    private boolean hasActiveMatch(PlayerId playerId) {
        return matchRepository.findByPlayer(playerId).filter(PlayerAvailabilityService::isActiveMatch).isPresent()
                || matchRepository.findBySpectator(playerId).filter(PlayerAvailabilityService::isActiveMatch).isPresent();
    }

    private boolean hasPendingDuelRequest(PlayerId playerId) {
        return duelRequestRepository.findAll().stream()
                .filter(PlayerAvailabilityService::isPending)
                .anyMatch(request -> request.requesterId().equals(playerId) || request.targetId().equals(playerId));
    }

    private static boolean isActiveMatch(Match match) {
        return match.state() != MatchState.COMPLETED;
    }

    private static boolean isPending(DuelRequest request) {
        return request.state() == DuelRequestState.PENDING;
    }

    private static boolean isActiveTicket(QueueTicket ticket) {
        return ticket.state() == QueueTicketState.SEARCHING || ticket.state() == QueueTicketState.PAIRING;
    }
}
