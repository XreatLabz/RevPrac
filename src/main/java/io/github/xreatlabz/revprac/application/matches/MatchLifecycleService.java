package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservation;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class MatchLifecycleService {

    private final MatchRepository matchRepository;
    private final PlayerSessionService playerSessionService;
    private final ArenaRegistryService arenaRegistryService;
    private final KitRegistryService kitRegistryService;
    private final MatchPlayerPort matchPlayerPort;
    private final MatchRuleset matchRuleset;
    private final MatchSettlementService matchSettlementService;
    private final PostMatchSummaryService postMatchSummaryService;
    private final Clock clock;
    private final MatchEventPublisher eventPublisher;
    private final AtomicBoolean intakeClosed = new AtomicBoolean(false);
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final Map<MatchId, MatchSettlementResult> retainedSettlementResults = new HashMap<>();

    public MatchLifecycleService(
            MatchRepository matchRepository,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchRuleset matchRuleset,
            Consumer<MatchEvent> eventSink) {
        this(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchRuleset,
                MatchSettlementService.noOp(),
                PostMatchSummaryService.noOp(),
                Clock.systemUTC(),
                eventSink);
    }

    public MatchLifecycleService(
            MatchRepository matchRepository,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchRuleset matchRuleset,
            MatchSettlementService matchSettlementService,
            Consumer<MatchEvent> eventSink) {
        this(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchRuleset,
                matchSettlementService,
                PostMatchSummaryService.noOp(),
                Clock.systemUTC(),
                eventSink);
    }

    public MatchLifecycleService(
            MatchRepository matchRepository,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchRuleset matchRuleset,
            MatchSettlementService matchSettlementService,
            PostMatchSummaryService postMatchSummaryService,
            Consumer<MatchEvent> eventSink) {
        this(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchRuleset,
                matchSettlementService,
                postMatchSummaryService,
                Clock.systemUTC(),
                eventSink);
    }

    public MatchLifecycleService(
            MatchRepository matchRepository,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchRuleset matchRuleset,
            MatchSettlementService matchSettlementService,
            Clock clock,
            Consumer<MatchEvent> eventSink) {
        this(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchRuleset,
                matchSettlementService,
                PostMatchSummaryService.noOp(),
                clock,
                eventSink);
    }

    public MatchLifecycleService(
            MatchRepository matchRepository,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchRuleset matchRuleset,
            MatchSettlementService matchSettlementService,
            PostMatchSummaryService postMatchSummaryService,
            Clock clock,
            Consumer<MatchEvent> eventSink) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.arenaRegistryService = Objects.requireNonNull(arenaRegistryService, "arenaRegistryService");
        this.kitRegistryService = Objects.requireNonNull(kitRegistryService, "kitRegistryService");
        this.matchPlayerPort = Objects.requireNonNull(matchPlayerPort, "matchPlayerPort");
        this.matchRuleset = Objects.requireNonNull(matchRuleset, "matchRuleset");
        this.matchSettlementService = Objects.requireNonNull(matchSettlementService, "matchSettlementService");
        this.postMatchSummaryService = Objects.requireNonNull(postMatchSummaryService, "postMatchSummaryService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventPublisher = new MatchEventPublisher(Objects.requireNonNull(eventSink, "eventSink"));
    }

    public Match startAcceptedDuel(DuelRequest duelRequest) {
        return startAcceptedDuel(duelRequest, match -> {
        });
    }

    public Match startQueuedMatch(PlayerId firstPlayerId, PlayerId secondPlayerId, KitId kitId) {
        return startQueuedMatch(firstPlayerId, secondPlayerId, kitId, QueueMode.UNRANKED);
    }

    public Match startQueuedMatch(PlayerId firstPlayerId, PlayerId secondPlayerId, KitId kitId, QueueMode queueMode) {
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(queueMode, "queueMode");
        mutationLock.lock();
        try {
            ensureIntakeOpen();
            if (firstPlayerId.equals(secondPlayerId)) {
                throw new IllegalArgumentException("queued match requires distinct players");
            }

            MatchId matchId = new MatchId(UUID.randomUUID());
            KitDefinition kitDefinition = reserveKit(kitId);
            ReservedArena reservedArena = reserveFirstEnabledArena(matchId);
            return startReservedMatchLocked(
                    matchId,
                    firstPlayerId,
                    secondPlayerId,
                    originForQueueMode(queueMode),
                    reservedArena.definition(),
                    kitDefinition,
                    reservedArena.reservation(),
                    match -> {
                    });
        } finally {
            mutationLock.unlock();
        }
    }

    Match startAcceptedDuel(DuelRequest duelRequest, Consumer<Match> beforeCountdown) {
        Objects.requireNonNull(duelRequest, "duelRequest");
        Objects.requireNonNull(beforeCountdown, "beforeCountdown");
        mutationLock.lock();
        try {
            ensureIntakeOpen();
            if (duelRequest.state() != DuelRequestState.ACCEPTED) {
                throw new IllegalArgumentException("duel request must be accepted before a match can start");
            }

            ArenaDefinition arenaDefinition = requireArena(duelRequest.arenaId());
            MatchId matchId = new MatchId(UUID.randomUUID());
            return startReservedMatchLocked(
                    matchId,
                    duelRequest.requesterId(),
                    duelRequest.targetId(),
                    MatchOrigin.DIRECT_DUEL,
                    arenaDefinition,
                    reserveKit(duelRequest.kitId()),
                    reserveArena(arenaDefinition, matchId),
                    beforeCountdown);
        } finally {
            mutationLock.unlock();
        }
    }

    public Match spectate(PlayerId spectatorId, PlayerId targetPlayerId) {
        Objects.requireNonNull(spectatorId, "spectatorId");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        mutationLock.lock();
        try {
            ensureIntakeOpen();
            if (!matchPlayerPort.isOnline(spectatorId)) {
                throw new IllegalStateException("spectator is offline");
            }
            Match current = matchRepository.findByPlayer(targetPlayerId)
                    .orElseThrow(() -> new IllegalStateException("target is not in a match"));
            if (current.participants().contains(spectatorId)) {
                throw new IllegalArgumentException("participants cannot become spectators in their own match");
            }
            if (matchRepository.findByPlayer(spectatorId).isPresent() || matchRepository.findBySpectator(spectatorId).isPresent()) {
                throw new IllegalStateException("spectator is already busy");
            }
            Match updated = current.addSpectator(spectatorId);
            ArenaDefinition arenaDefinition = requireArena(current.arenaId());

            boolean transitioned = false;
            try {
                playerSessionService.transitionTo(spectatorId, PlayerContext.SPECTATOR, TransitionReason.SPECTATE);
                transitioned = true;
                matchPlayerPort.prepareSpectator(spectatorId, updated, arenaDefinition);
                matchRepository.save(updated);
            } catch (RuntimeException exception) {
                if (transitioned) {
                    try {
                        matchPlayerPort.clearMatchState(spectatorId);
                    } catch (RuntimeException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                    try {
                        playerSessionService.returnToLobby(spectatorId);
                    } catch (RuntimeException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                }
                throw exception;
            }

            emit(sequence -> new MatchEvent.MatchSpectatorJoined(sequence, updated.id(), spectatorId));
            return updated;
        } finally {
            mutationLock.unlock();
        }
    }

    public void tick() {
        mutationLock.lock();
        try {
            for (Match match : List.copyOf(matchRepository.findAll())) {
                if (match.state() == MatchState.COUNTDOWN) {
                    Match next = match.tickCountdown();
                    matchRepository.save(next);
                    if (next.state() == MatchState.ACTIVE) {
                        emit(sequence -> new MatchEvent.MatchStarted(sequence, next.id()));
                    }
                    continue;
                }
                if (match.state() == MatchState.ACTIVE) {
                    Match next = match.tickActive(clock.instant());
                    if (next.state() == MatchState.COMPLETED) {
                        completeMatchLocked(next);
                    } else {
                        matchRepository.save(next);
                    }
                }
            }
        } finally {
            mutationLock.unlock();
        }
    }

    public void completeByDeath(PlayerId deadPlayerId) {
        Objects.requireNonNull(deadPlayerId, "deadPlayerId");
        mutationLock.lock();
        try {
            Match match = requireMatchForPlayer(deadPlayerId);
            PlayerId winnerId = match.participants().opponentOf(deadPlayerId)
                    .orElseThrow(() -> new IllegalStateException("opponent not found"));
            completeMatchLocked(match.complete(MatchOutcome.win(winnerId, deadPlayerId), clock.instant()));
        } finally {
            mutationLock.unlock();
        }
    }

    public void forfeit(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        mutationLock.lock();
        try {
            Match match = requireMatchForPlayer(playerId);
            PlayerId winnerId = match.participants().opponentOf(playerId)
                    .orElseThrow(() -> new IllegalStateException("opponent not found"));
            completeMatchLocked(match.complete(MatchOutcome.forfeit(winnerId, playerId), clock.instant()));
        } finally {
            mutationLock.unlock();
        }
    }

    public void handleQuit(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        mutationLock.lock();
        try {
            if (matchRepository.findByPlayer(playerId).isPresent()) {
                Match match = requireMatchForPlayer(playerId);
                PlayerId winnerId = match.participants().opponentOf(playerId)
                        .orElseThrow(() -> new IllegalStateException("opponent not found"));
                completeMatchLocked(match.complete(MatchOutcome.forfeit(winnerId, playerId), clock.instant()));
                return;
            }

            matchRepository.findBySpectator(playerId).ifPresent(match -> {
                Match updated = match.removeSpectator(playerId);
                matchPlayerPort.clearMatchState(playerId);
                playerSessionService.returnToLobby(playerId);
                matchRepository.save(updated);
                emit(sequence -> new MatchEvent.MatchSpectatorLeft(sequence, match.id(), playerId));
            });
        } finally {
            mutationLock.unlock();
        }
    }

    public void tearDown(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        mutationLock.lock();
        try {
            Match match = matchRepository.find(matchId)
                    .orElseThrow(() -> new IllegalStateException("unknown match: " + matchId.value()));
            drainCompletedMatchLocked(match);
        } finally {
            mutationLock.unlock();
        }
    }

    public void shutdownAll() {
        mutationLock.lock();
        try {
            intakeClosed.set(true);
            RuntimeException failure = null;
            for (Match match : List.copyOf(matchRepository.findAll())) {
                try {
                    if (match.state() == MatchState.COMPLETED) {
                        drainCompletedMatchLocked(match);
                    } else {
                        completeMatchLocked(match.complete(MatchOutcome.shutdown(), clock.instant()));
                    }
                } catch (RuntimeException exception) {
                    RuntimeException finalFailure = retryRetainedCompletedDrain(match.id(), exception);
                    if (finalFailure != null) {
                        failure = mergeFailures(failure, finalFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            mutationLock.unlock();
        }
    }

    public void closeIntake() {
        intakeClosed.set(true);
    }

    public long activeMatchCount() {
        mutationLock.lock();
        try {
            return matchRepository.findAll().stream()
                    .filter(match -> match.state() != MatchState.COMPLETED)
                    .count();
        } finally {
            mutationLock.unlock();
        }
    }

    private void transitionParticipant(PlayerId playerId, List<PlayerId> transitionedPlayers) {
        playerSessionService.transitionTo(playerId, PlayerContext.MATCH, TransitionReason.MATCH_START);
        transitionedPlayers.add(playerId);
    }

    private Match startReservedMatchLocked(
            MatchId matchId,
            PlayerId firstPlayerId,
            PlayerId secondPlayerId,
            MatchOrigin origin,
            ArenaDefinition arenaDefinition,
            KitDefinition kitDefinition,
            ArenaReservation reservation,
            Consumer<Match> beforeCountdown) {
        Match match = Match.create(
                matchId,
                new MatchParticipants(firstPlayerId, secondPlayerId),
                arenaDefinition.id(),
                kitDefinition.id(),
                origin,
                reservation.reservationId(),
                matchRuleset);

        List<PlayerId> transitionedPlayers = new ArrayList<>();
        List<PlayerId> preparedPlayers = new ArrayList<>();
        boolean matchCreated = false;
        try {
            transitionParticipant(firstPlayerId, transitionedPlayers);
            transitionParticipant(secondPlayerId, transitionedPlayers);

            matchPlayerPort.prepareCombatant(firstPlayerId, match, MatchSide.ONE, arenaDefinition, kitDefinition);
            preparedPlayers.add(firstPlayerId);
            matchPlayerPort.prepareCombatant(secondPlayerId, match, MatchSide.TWO, arenaDefinition, kitDefinition);
            preparedPlayers.add(secondPlayerId);

            if (!matchRepository.create(match)) {
                throw new IllegalStateException("match could not be created");
            }
            matchCreated = true;
            beforeCountdown.accept(match);
        } catch (RuntimeException exception) {
            if (matchCreated) {
                try {
                    matchRepository.delete(match.id());
                } catch (RuntimeException deleteFailure) {
                    exception.addSuppressed(deleteFailure);
                }
            }
            rollbackPreparation(preparedPlayers, exception);
            rollbackTransitions(transitionedPlayers, exception);
            try {
                arenaRegistryService.release(reservation.reservationId());
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw exception;
        }

        emit(sequence -> new MatchEvent.MatchCountdownStarted(
                sequence, match.id(), match.countdownTicksRemaining()));
        return match;
    }

    private void rollbackPreparation(List<PlayerId> preparedPlayers, RuntimeException originalFailure) {
        for (PlayerId preparedPlayer : preparedPlayers) {
            try {
                matchPlayerPort.clearMatchState(preparedPlayer);
            } catch (RuntimeException rollbackFailure) {
                originalFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void rollbackTransitions(List<PlayerId> transitionedPlayers, RuntimeException originalFailure) {
        for (PlayerId transitionedPlayer : transitionedPlayers) {
            try {
                playerSessionService.returnToLobby(transitionedPlayer);
            } catch (RuntimeException rollbackFailure) {
                originalFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void completeMatchLocked(Match completedMatch) {
        matchRepository.save(completedMatch);
        emit(sequence -> new MatchEvent.MatchCompleted(
                sequence, completedMatch.id(), completedMatch.outcome().orElseThrow()));
        drainCompletedMatchLocked(completedMatch);
    }

    private void drainCompletedMatchLocked(Match match) {
        if (match.state() != MatchState.COMPLETED) {
            throw new IllegalStateException("match must be completed before teardown");
        }
        MatchSettlementResult settlementResult = retainedSettlementResults.get(match.id());
        if (settlementResult == null) {
            settlementResult = matchSettlementService.settle(match);
            retainedSettlementResults.put(match.id(), settlementResult);
        }
        tearDownLocked(match, settlementResult);
    }

    private void tearDownLocked(Match match, MatchSettlementResult settlementResult) {
        if (match.state() != MatchState.COMPLETED) {
            throw new IllegalStateException("match must be completed before teardown");
        }

        RuntimeException failure = null;
        Collection<PlayerId> allPlayers = allPlayers(match);
        for (PlayerId playerId : allPlayers) {
            try {
                matchPlayerPort.clearMatchState(playerId);
            } catch (RuntimeException exception) {
                failure = mergeFailures(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
        for (PlayerId playerId : allPlayers) {
            try {
                playerSessionService.returnToLobby(playerId);
            } catch (RuntimeException exception) {
                failure = mergeFailures(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
        try {
            arenaRegistryService.release(match.arenaReservationId());
        } catch (RuntimeException exception) {
            failure = mergeFailures(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }

        emit(sequence -> new MatchEvent.MatchTornDown(
                sequence, match.id(), match.outcome().orElseThrow().reason()));
        matchRepository.delete(match.id());
        try {
            postMatchSummaryService.send(match, settlementResult);
        } finally {
            retainedSettlementResults.remove(match.id());
        }
    }

    private Collection<PlayerId> allPlayers(Match match) {
        LinkedHashSet<PlayerId> players = new LinkedHashSet<>();
        players.add(match.participants().playerOne());
        players.add(match.participants().playerTwo());
        players.addAll(match.spectators());
        return players;
    }

    private Match requireMatchForPlayer(PlayerId playerId) {
        Match match = matchRepository.findByPlayer(playerId)
                .orElseThrow(() -> new IllegalStateException("player is not in a match"));
        if (match.state() == MatchState.COMPLETED) {
            throw new IllegalStateException("match is already completed");
        }
        return match;
    }

    private RuntimeException retryRetainedCompletedDrain(MatchId matchId, RuntimeException initialFailure) {
        Match retainedMatch = matchRepository.find(matchId)
                .filter(match -> match.state() == MatchState.COMPLETED)
                .orElse(null);
        if (retainedMatch == null) {
            return initialFailure;
        }

        try {
            drainCompletedMatchLocked(retainedMatch);
            return null;
        } catch (RuntimeException retryFailure) {
            initialFailure.addSuppressed(retryFailure);
            return initialFailure;
        }
    }

    private ArenaDefinition requireArena(ArenaId arenaId) {
        return arenaRegistryService.arenas().stream()
                .filter(arenaDefinition -> arenaDefinition.id().equals(arenaId))
                .findFirst()
                .filter(ArenaDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown arena: " + arenaId.value()));
    }

    private KitDefinition reserveKit(KitId kitId) {
        return kitRegistryService.kits().stream()
                .filter(kitDefinition -> kitDefinition.id().equals(kitId))
                .findFirst()
                .filter(KitDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown kit: " + kitId.value()));
    }

    private ArenaReservation reserveArena(ArenaDefinition arenaDefinition, MatchId matchId) {
        try {
            return arenaRegistryService.reserve(arenaDefinition.id(), "match:" + matchId.value());
        } catch (IllegalStateException exception) {
            throw new ArenaUnavailableException(exception.getMessage(), exception);
        }
    }

    private ReservedArena reserveFirstEnabledArena(MatchId matchId) {
        RuntimeException lastFailure = null;
        for (ArenaDefinition arenaDefinition : arenaRegistryService.arenas()) {
            if (!arenaDefinition.enabled()) {
                continue;
            }
            try {
                return new ReservedArena(arenaDefinition, arenaRegistryService.reserve(
                        arenaDefinition.id(), "match:" + matchId.value()));
            } catch (IllegalStateException exception) {
                lastFailure = exception;
            }
        }
        throw new ArenaUnavailableException("no enabled arenas available for queued match", lastFailure);
    }

    private void ensureIntakeOpen() {
        if (intakeClosed.get()) {
            throw new IllegalStateException("match intake is closed");
        }
    }

    private void emit(java.util.function.LongFunction<MatchEvent> eventFactory) {
        eventPublisher.emit(eventFactory);
    }

    private RuntimeException mergeFailures(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static MatchOrigin originForQueueMode(QueueMode queueMode) {
        return switch (queueMode) {
            case RANKED -> MatchOrigin.QUEUE_RANKED;
            case UNRANKED -> MatchOrigin.QUEUE_UNRANKED;
        };
    }

    private record ReservedArena(ArenaDefinition definition, ArenaReservation reservation) {
    }

    public static final class ArenaUnavailableException extends IllegalStateException {

        public ArenaUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public ArenaUnavailableException(String message) {
            super(message);
        }
    }
}
