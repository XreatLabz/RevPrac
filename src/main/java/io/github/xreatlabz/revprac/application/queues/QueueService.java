package io.github.xreatlabz.revprac.application.queues;

import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QueueService {

    private final QueueTicketRepository queueTicketRepository;
    private final QueueRatingRepository queueRatingRepository;
    private final PlayerAvailabilityService playerAvailabilityService;
    private final PlayerSessionService playerSessionService;
    private final KitRegistryService kitRegistryService;
    private final PlayerStatePort playerStatePort;
    @SuppressWarnings("unused")
    private final Clock clock;
    private final QueueConfig queueConfig;
    private final AtomicBoolean intakeClosed = new AtomicBoolean(false);

    public QueueService(
            QueueTicketRepository queueTicketRepository,
            QueueRatingRepository queueRatingRepository,
            PlayerAvailabilityService playerAvailabilityService,
            PlayerSessionService playerSessionService,
            KitRegistryService kitRegistryService,
            PlayerStatePort playerStatePort,
            Clock clock,
            QueueConfig queueConfig) {
        this.queueTicketRepository = Objects.requireNonNull(queueTicketRepository, "queueTicketRepository");
        this.queueRatingRepository = Objects.requireNonNull(queueRatingRepository, "queueRatingRepository");
        this.playerAvailabilityService = Objects.requireNonNull(playerAvailabilityService, "playerAvailabilityService");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.kitRegistryService = Objects.requireNonNull(kitRegistryService, "kitRegistryService");
        this.playerStatePort = Objects.requireNonNull(playerStatePort, "playerStatePort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.queueConfig = Objects.requireNonNull(queueConfig, "queueConfig");
    }

    public QueueTicket join(PlayerId playerId, QueueMode mode, KitId kitId, long currentTick) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(kitId, "kitId");
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must be non-negative");
        }
        ensureIntakeOpen();
        if (!playerStatePort.isOnline(playerId)) {
            throw new IllegalStateException("player is offline");
        }
        playerAvailabilityService.requireAvailableForQueue(playerId);

        KitDefinition kitDefinition = requireEnabledKit(kitId);
        if (mode == QueueMode.RANKED && !kitDefinition.rules().ranked()) {
            throw new IllegalStateException("ranked queue is disabled for kit: " + kitId.value());
        }

        playerSessionService.transitionTo(playerId, PlayerContext.QUEUE, TransitionReason.QUEUE_JOIN);
        try {
            QueueTicket ticket = new QueueTicket(
                    new QueueTicketId(UUID.randomUUID()),
                    playerId,
                    new QueueKey(mode, kitId),
                    currentTick,
                    mode == QueueMode.RANKED
                            ? queueRatingRepository.rating(playerId, kitId, queueConfig.rankedBaseRating())
                            : 0,
                    QueueTicketState.SEARCHING);
            if (!queueTicketRepository.create(ticket)) {
                throw new IllegalStateException("player already has an active queue ticket");
            }
            return ticket;
        } catch (RuntimeException exception) {
            rollbackToLobby(playerId, exception);
            throw exception;
        }
    }

    public Optional<QueueTicket> ticket(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return queueTicketRepository.findByPlayer(playerId);
    }

    public QueueTicket leave(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        QueueTicket ticket = queueTicketRepository.findByPlayer(playerId)
                .orElseThrow(() -> new IllegalStateException("player is not queued"));
        playerSessionService.returnToLobby(playerId);
        queueTicketRepository.delete(ticket.id());
        return ticket;
    }

    public void handleQuit(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        queueTicketRepository.findByPlayer(playerId).ifPresent(ticket -> queueTicketRepository.delete(ticket.id()));
    }

    public void closeIntake() {
        intakeClosed.set(true);
    }

    public void shutdownAll() {
        intakeClosed.set(true);
        RuntimeException failure = null;
        for (QueueTicket ticket : List.copyOf(queueTicketRepository.findAll())) {
            if (!isActive(ticket)) {
                continue;
            }
            if (!playerStatePort.isOnline(ticket.playerId())) {
                queueTicketRepository.delete(ticket.id());
                continue;
            }
            try {
                playerSessionService.returnToLobby(ticket.playerId());
                queueTicketRepository.delete(ticket.id());
            } catch (RuntimeException exception) {
                failure = mergeFailures(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private KitDefinition requireEnabledKit(KitId kitId) {
        return kitRegistryService.kits().stream()
                .filter(kit -> kit.id().equals(kitId))
                .findFirst()
                .filter(KitDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown kit: " + kitId.value()));
    }

    private void rollbackToLobby(PlayerId playerId, RuntimeException originalFailure) {
        try {
            playerSessionService.returnToLobby(playerId);
        } catch (RuntimeException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private void ensureIntakeOpen() {
        if (intakeClosed.get()) {
            throw new IllegalStateException("queue intake is closed");
        }
    }

    private static boolean isActive(QueueTicket ticket) {
        return ticket.state() == QueueTicketState.SEARCHING || ticket.state() == QueueTicketState.PAIRING;
    }

    private RuntimeException mergeFailures(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
