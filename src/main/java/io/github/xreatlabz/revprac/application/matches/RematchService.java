package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public final class RematchService {

    private final MatchSettlementRepository matchSettlementRepository;
    private final DuelRequestService duelRequestService;
    private final Clock clock;
    private final Duration eligibilityWindow;

    public RematchService(
            MatchSettlementRepository matchSettlementRepository,
            DuelRequestService duelRequestService,
            Clock clock,
            Duration eligibilityWindow) {
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
        this.duelRequestService = Objects.requireNonNull(duelRequestService, "duelRequestService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eligibilityWindow = Objects.requireNonNull(eligibilityWindow, "eligibilityWindow");
        if (eligibilityWindow.isZero() || eligibilityWindow.isNegative()) {
            throw new IllegalArgumentException("eligibilityWindow must be positive");
        }
    }

    public io.github.xreatlabz.revprac.domain.matches.DuelRequest request(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");

        MatchHistoryEntry history = latestEligibleMutualMatch(requesterId, targetId, clock.instant());
        return duelRequestService.request(requesterId, targetId, history.arenaId(), history.kitId());
    }

    private MatchHistoryEntry latestEligibleMutualMatch(PlayerId requesterId, PlayerId targetId, Instant now) {
        return matchSettlementRepository.findAllHistory(requesterId).stream()
                .filter(history -> isMutualMatch(history, requesterId, targetId))
                .filter(history -> history.completedAt().plus(eligibilityWindow).isAfter(now))
                .max(Comparator.comparing(MatchHistoryEntry::completedAt)
                        .thenComparing(history -> history.matchId().value()))
                .orElseThrow(() -> new IllegalStateException("no recent match found for rematch"));
    }

    private static boolean isMutualMatch(MatchHistoryEntry history, PlayerId firstPlayerId, PlayerId secondPlayerId) {
        return (history.playerOneId().equals(firstPlayerId) && history.playerTwoId().equals(secondPlayerId))
                || (history.playerOneId().equals(secondPlayerId) && history.playerTwoId().equals(firstPlayerId));
    }
}
