package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.DuelRequestRepository;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class DuelRequestService {

    private final DuelRequestRepository duelRequestRepository;
    private final MatchRepository matchRepository;
    private final ArenaRegistryService arenaRegistryService;
    private final KitRegistryService kitRegistryService;
    private final MatchPlayerPort matchPlayerPort;
    private final MatchLifecycleService matchLifecycleService;
    private final PlayerAvailabilityService availabilityService;
    private final Clock clock;
    private final Duration requestTtl;
    private final MatchEventPublisher eventPublisher;
    private final AtomicBoolean intakeClosed = new AtomicBoolean(false);
    private final ReentrantLock mutationLock = new ReentrantLock();

    public DuelRequestService(
            DuelRequestRepository duelRequestRepository,
            MatchRepository matchRepository,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            MatchPlayerPort matchPlayerPort,
            MatchLifecycleService matchLifecycleService,
            PlayerAvailabilityService availabilityService,
            Clock clock,
            Duration requestTtl,
            Consumer<MatchEvent> eventSink) {
        this.duelRequestRepository = Objects.requireNonNull(duelRequestRepository, "duelRequestRepository");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.arenaRegistryService = Objects.requireNonNull(arenaRegistryService, "arenaRegistryService");
        this.kitRegistryService = Objects.requireNonNull(kitRegistryService, "kitRegistryService");
        this.matchPlayerPort = Objects.requireNonNull(matchPlayerPort, "matchPlayerPort");
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
        this.availabilityService = Objects.requireNonNull(availabilityService, "availabilityService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestTtl = Objects.requireNonNull(requestTtl, "requestTtl");
        this.eventPublisher = new MatchEventPublisher(Objects.requireNonNull(eventSink, "eventSink"));
        if (requestTtl.isZero() || requestTtl.isNegative()) {
            throw new IllegalArgumentException("requestTtl must be positive");
        }
    }

    public DuelRequest request(PlayerId requesterId, PlayerId targetId, ArenaId arenaId, KitId kitId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(kitId, "kitId");
        mutationLock.lock();
        try {
            ensureIntakeOpen();
            if (requesterId.equals(targetId)) {
                throw new IllegalArgumentException("requester and target must be different players");
            }

            requireOnline(requesterId, "requester");
            requireOnline(targetId, "target");
            requireNoPendingRequestBetween(requesterId, targetId);
            availabilityService.requireAvailableForDuel(requesterId, "requester");
            availabilityService.requireAvailableForDuel(targetId, "target");
            requireArena(arenaId);
            requireKit(kitId);

            Instant createdAt = clock.instant();
            DuelRequest duelRequest = new DuelRequest(
                    new DuelRequestId(UUID.randomUUID()),
                    requesterId,
                    targetId,
                    arenaId,
                    kitId,
                    DuelRequestState.PENDING,
                    createdAt,
                    createdAt.plus(requestTtl));
            if (!duelRequestRepository.create(duelRequest)) {
                throw new IllegalStateException("duel request could not be created");
            }

            emit(sequence -> new MatchEvent.DuelRequestCreated(
                    sequence, duelRequest.id(), requesterId, targetId, arenaId, kitId));
            return duelRequest;
        } finally {
            mutationLock.unlock();
        }
    }

    public Match accept(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        mutationLock.lock();
        try {
            ensureIntakeOpen();
            DuelRequest pendingRequest = requirePendingRequest(requesterId, targetId);
            DuelRequest acceptedRequest = pendingRequest.accept();
            requireAvailableForAcceptedDuel(acceptedRequest);
            Match match = matchLifecycleService.startAcceptedDuel(acceptedRequest, startedMatch -> {
                duelRequestRepository.save(acceptedRequest);
                emit(sequence -> new MatchEvent.DuelRequestAccepted(
                        sequence, acceptedRequest.id(), startedMatch.id()));
            });
            return match;
        } finally {
            mutationLock.unlock();
        }
    }

    public DuelRequest decline(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        mutationLock.lock();
        try {
            DuelRequest pendingRequest = requirePendingRequest(requesterId, targetId);
            DuelRequest declinedRequest = pendingRequest.decline();
            duelRequestRepository.delete(pendingRequest.id());
            return declinedRequest;
        } finally {
            mutationLock.unlock();
        }
    }

    public DuelRequest cancel(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        mutationLock.lock();
        try {
            DuelRequest pendingRequest = requirePendingRequest(requesterId, targetId);
            DuelRequest cancelledRequest = pendingRequest.cancel();
            duelRequestRepository.delete(pendingRequest.id());
            return cancelledRequest;
        } finally {
            mutationLock.unlock();
        }
    }

    public List<DuelRequest> expirePendingRequests() {
        mutationLock.lock();
        try {
            Instant now = clock.instant();
            List<DuelRequest> expiredRequests = new ArrayList<>();
            for (DuelRequest duelRequest : duelRequestRepository.findAll()) {
                if (duelRequest.state() == DuelRequestState.PENDING && !duelRequest.expiresAt().isAfter(now)) {
                    expiredRequests.add(duelRequest.expire());
                    duelRequestRepository.delete(duelRequest.id());
                }
            }
            return List.copyOf(expiredRequests);
        } finally {
            mutationLock.unlock();
        }
    }

    public void closeIntake() {
        intakeClosed.set(true);
    }

    private DuelRequest requirePendingRequest(PlayerId requesterId, PlayerId targetId) {
        return duelRequestRepository.findPendingByPlayers(requesterId, targetId)
                .orElseThrow(() -> new IllegalStateException("pending duel request not found"));
    }

    private void requireOnline(PlayerId playerId, String role) {
        if (!matchPlayerPort.isOnline(playerId)) {
            throw new IllegalStateException(role + " is offline");
        }
    }

    private void requireNoPendingRequestBetween(PlayerId requesterId, PlayerId targetId) {
        boolean duplicatePending = duelRequestRepository.findPendingByPlayers(requesterId, targetId).isPresent()
                || duelRequestRepository.findPendingByPlayers(targetId, requesterId).isPresent();
        if (duplicatePending) {
            throw new IllegalStateException("a pending duel request already exists for these players");
        }
    }

    private void requireAvailableForAcceptedDuel(DuelRequest acceptedRequest) {
        requireAvailableForAcceptedDuel(acceptedRequest.requesterId(), "requester", acceptedRequest.id());
        requireAvailableForAcceptedDuel(acceptedRequest.targetId(), "target", acceptedRequest.id());
    }

    private void requireAvailableForAcceptedDuel(PlayerId playerId, String role, DuelRequestId acceptedRequestId) {
        if (hasActiveMatch(playerId)
                || hasOtherPendingDuelRequest(playerId, acceptedRequestId)
                || availabilityService.isQueued(playerId)) {
            throw new IllegalStateException(role + " is already busy");
        }
    }

    private boolean hasActiveMatch(PlayerId playerId) {
        return matchRepository.findByPlayer(playerId).filter(DuelRequestService::isActiveMatch).isPresent()
                || matchRepository.findBySpectator(playerId).filter(DuelRequestService::isActiveMatch).isPresent();
    }

    private boolean hasOtherPendingDuelRequest(PlayerId playerId, DuelRequestId acceptedRequestId) {
        return duelRequestRepository.findAll().stream()
                .filter(duelRequest -> !duelRequest.id().equals(acceptedRequestId))
                .filter(duelRequest -> duelRequest.state() == DuelRequestState.PENDING)
                .anyMatch(duelRequest -> duelRequest.requesterId().equals(playerId)
                        || duelRequest.targetId().equals(playerId));
    }

    private static boolean isActiveMatch(Match match) {
        return match.state() != MatchState.COMPLETED;
    }

    private ArenaDefinition requireArena(ArenaId arenaId) {
        return arenaRegistryService.arenas().stream()
                .filter(arenaDefinition -> arenaDefinition.id().equals(arenaId))
                .findFirst()
                .filter(ArenaDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown arena: " + arenaId.value()));
    }

    private KitDefinition requireKit(KitId kitId) {
        return kitRegistryService.kits().stream()
                .filter(kitDefinition -> kitDefinition.id().equals(kitId))
                .findFirst()
                .filter(KitDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown kit: " + kitId.value()));
    }

    private void ensureIntakeOpen() {
        if (intakeClosed.get()) {
            throw new IllegalStateException("duel request intake is closed");
        }
    }

    private void emit(java.util.function.LongFunction<MatchEvent> eventFactory) {
        eventPublisher.emit(eventFactory);
    }
}
