package io.github.xreatlabz.revprac.application.recovery;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.util.Objects;
import java.util.Set;

public final class RuntimeRecoveryService {

    private final RuntimeRecoveryRepository recoveryRepository;
    private final PlayerSessionRepository playerSessionRepository;
    private final PendingRestorationRepository pendingRestorationRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final MatchRepository matchRepository;
    private final PlayerStatePort playerStatePort;

    public RuntimeRecoveryService(
            RuntimeRecoveryRepository recoveryRepository,
            PlayerSessionRepository playerSessionRepository,
            PendingRestorationRepository pendingRestorationRepository,
            QueueTicketRepository queueTicketRepository,
            MatchRepository matchRepository,
            PlayerStatePort playerStatePort) {
        this.recoveryRepository = Objects.requireNonNull(recoveryRepository, "recoveryRepository");
        this.playerSessionRepository = Objects.requireNonNull(playerSessionRepository, "playerSessionRepository");
        this.pendingRestorationRepository = Objects.requireNonNull(pendingRestorationRepository, "pendingRestorationRepository");
        this.queueTicketRepository = Objects.requireNonNull(queueTicketRepository, "queueTicketRepository");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.playerStatePort = Objects.requireNonNull(playerStatePort, "playerStatePort");
    }

    public void recoverBootstrapState() {
        for (PendingRestoration restoration : recoveryRepository.pendingRestorations()) {
            pendingRestorationRepository.save(restoration);
        }
        for (PlayerSession session : recoveryRepository.playerSessions()) {
            if (playerStatePort.isOnline(session.playerId())) {
                playerSessionRepository.save(session);
                continue;
            }
            PendingRestoration restoration = new PendingRestoration(
                    session.playerId(),
                    session.returnSnapshot(),
                    TransitionReason.PLUGIN_DISABLE);
            pendingRestorationRepository.save(restoration);
            recoveryRepository.deletePlayerSession(session.playerId());
        }
        for (QueueTicket ticket : recoveryRepository.queueTickets()) {
            recoverQueueTicketIfOnline(ticket);
        }
        for (Match match : recoveryRepository.matches()) {
            recoverMatchIfSafe(match);
        }
    }

    public void recoverPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        recoveryRepository.queueTicket(playerId).ifPresent(this::recoverQueueTicketIfOnline);
        for (Match match : recoveryRepository.matches()) {
            if (match.participants().contains(playerId)) {
                recoverMatchIfSafe(match);
            }
        }
    }

    private void recoverQueueTicketIfOnline(QueueTicket ticket) {
        if (!playerStatePort.isOnline(ticket.playerId())) {
            return;
        }
        QueueTicket searchableTicket = ticket.state() == QueueTicketState.PAIRING
                ? new QueueTicket(
                        ticket.id(),
                        ticket.playerId(),
                        ticket.key(),
                        ticket.joinedAtTick(),
                        ticket.searchRating(),
                        QueueTicketState.SEARCHING)
                : ticket;
        if (searchableTicket.state() == QueueTicketState.SEARCHING) {
            queueTicketRepository.create(searchableTicket);
        }
    }

    private void recoverMatchIfSafe(Match match) {
        if (match.state() == MatchState.COMPLETED) {
            matchRepository.create(match);
            return;
        }
        if (!playerStatePort.isOnline(match.participants().playerOne())
                || !playerStatePort.isOnline(match.participants().playerTwo())) {
            return;
        }
        Match freshCountdown = new Match(
                match.id(),
                match.participants(),
                match.arenaId(),
                match.kitId(),
                match.origin(),
                match.arenaReservationId(),
                match.ruleset(),
                MatchState.COUNTDOWN,
                match.ruleset().countdownTicks(),
                0,
                Set.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty());
        matchRepository.create(freshCountdown);
    }
}
