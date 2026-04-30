package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.PlayerSessionTransitionPolicy;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.ports.players.PendingRestorationRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerSessionRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerSessionService {

    private final PlayerSessionRepository playerSessionRepository;
    private final PendingRestorationRepository pendingRestorationRepository;
    private final PlayerStatePort playerStatePort;
    private final AtomicBoolean intakeClosed = new AtomicBoolean(false);
    private final ReentrantLock mutationLock = new ReentrantLock();

    public PlayerSessionService(
            PlayerSessionRepository playerSessionRepository,
            PendingRestorationRepository pendingRestorationRepository,
            PlayerStatePort playerStatePort) {
        this.playerSessionRepository = Objects.requireNonNull(playerSessionRepository, "playerSessionRepository");
        this.pendingRestorationRepository = Objects.requireNonNull(pendingRestorationRepository, "pendingRestorationRepository");
        this.playerStatePort = Objects.requireNonNull(playerStatePort, "playerStatePort");
    }

    public PlayerSession join(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        mutationLock.lock();
        try {
            ensureIntakeOpen();

            PlayerSession existingSession = playerSessionRepository.find(playerId).orElse(null);
            if (existingSession != null) {
                return existingSession;
            }

            pendingRestorationRepository.find(playerId).ifPresent(restoration -> {
                playerStatePort.restore(playerId, restoration.snapshot());
                pendingRestorationRepository.delete(playerId);
            });

            PlayerSession lobbySession = new PlayerSession(playerId, PlayerContext.LOBBY, null);
            playerSessionRepository.save(lobbySession);
            return lobbySession;
        } finally {
            mutationLock.unlock();
        }
    }

    public PlayerSession transitionTo(PlayerId playerId, PlayerContext nextContext, TransitionReason reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nextContext, "nextContext");
        Objects.requireNonNull(reason, "reason");
        mutationLock.lock();
        try {
            ensureIntakeOpen();

            PlayerSession currentSession = activeSession(playerId);
            if (!PlayerSessionTransitionPolicy.isAllowed(currentSession.context(), nextContext)) {
                throw new IllegalStateException("Transition is not allowed: " + currentSession.context() + " -> " + nextContext);
            }
            if (!nextContext.isManaged()) {
                return returnToLobbyLocked(playerId);
            }

            PlayerSafetySnapshot baseline = currentSession.isManaged()
                    ? currentSession.returnSnapshot()
                    : playerStatePort.capture(playerId);
            PlayerSession nextSession = new PlayerSession(playerId, nextContext, baseline);
            playerSessionRepository.save(nextSession);
            return nextSession;
        } finally {
            mutationLock.unlock();
        }
    }

    public PlayerSession returnToLobby(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        mutationLock.lock();
        try {
            return returnToLobbyLocked(playerId);
        } finally {
            mutationLock.unlock();
        }
    }

    private PlayerSession returnToLobbyLocked(PlayerId playerId) {
        PlayerSession currentSession = activeSession(playerId);
        if (!currentSession.isManaged()) {
            return currentSession;
        }

        playerStatePort.restore(playerId, currentSession.returnSnapshot());
        PlayerSession lobbySession = new PlayerSession(playerId, PlayerContext.LOBBY, null);
        playerSessionRepository.save(lobbySession);
        return lobbySession;
    }

    public void quit(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        mutationLock.lock();
        try {
            playerSessionRepository.find(playerId).ifPresent(session -> {
                if (session.isManaged()) {
                    pendingRestorationRepository.save(new PendingRestoration(
                            playerId,
                            session.returnSnapshot(),
                            TransitionReason.QUIT));
                }
                playerSessionRepository.delete(playerId);
            });
        } finally {
            mutationLock.unlock();
        }
    }

    public void shutdownAll() {
        mutationLock.lock();
        try {
            intakeClosed.set(true);

            RuntimeException shutdownFailure = null;
            for (PlayerSession session : playerSessionRepository.findAll()) {
                try {
                    if (session.isManaged()) {
                        if (!playerStatePort.isOnline(session.playerId())) {
                            pendingRestorationRepository.save(new PendingRestoration(
                                    session.playerId(),
                                    session.returnSnapshot(),
                                    TransitionReason.PLUGIN_DISABLE));
                            playerSessionRepository.delete(session.playerId());
                            continue;
                        }
                        playerStatePort.restore(session.playerId(), session.returnSnapshot());
                    }
                    playerSessionRepository.delete(session.playerId());
                } catch (RuntimeException exception) {
                    if (shutdownFailure == null) {
                        shutdownFailure = exception;
                    } else {
                        shutdownFailure.addSuppressed(exception);
                    }
                }
            }

            if (shutdownFailure != null) {
                throw shutdownFailure;
            }
        } finally {
            mutationLock.unlock();
        }
    }

    private void ensureIntakeOpen() {
        if (intakeClosed.get()) {
            throw new IllegalStateException("Player session intake is closed");
        }
    }

    private PlayerSession activeSession(PlayerId playerId) {
        return playerSessionRepository.find(playerId)
                .orElseThrow(() -> new IllegalStateException("No active session for player " + playerId.value()));
    }
}
