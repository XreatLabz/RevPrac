package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.application.ratings.RatingService;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MatchSettlementService {

    private final MatchSettlementRepository matchSettlementRepository;
    private final RatingService ratingService;
    private final int rankedBaseRating;
    private final boolean enabled;
    private final Object rankedSettlementMutex;

    private MatchSettlementService() {
        this.matchSettlementRepository = null;
        this.ratingService = null;
        this.rankedBaseRating = 0;
        this.enabled = false;
        this.rankedSettlementMutex = null;
    }

    public MatchSettlementService(MatchSettlementRepository matchSettlementRepository) {
        this(matchSettlementRepository, null, 0);
    }

    public MatchSettlementService(
            MatchSettlementRepository matchSettlementRepository,
            RatingService ratingService,
            int rankedBaseRating) {
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
        this.ratingService = ratingService;
        this.rankedBaseRating = rankedBaseRating;
        if (ratingService != null && rankedBaseRating <= 0) {
            throw new IllegalArgumentException("rankedBaseRating must be positive");
        }
        this.enabled = true;
        this.rankedSettlementMutex = new Object();
    }

    public static MatchSettlementService noOp() {
        return new MatchSettlementService();
    }

    public void settle(Match match) {
        Objects.requireNonNull(match, "match");
        if (match.state() != MatchState.COMPLETED) {
            throw new IllegalArgumentException("only completed matches can be settled");
        }
        if (!enabled) {
            return;
        }
        if (requiresRankedSettlementSerialization(match)) {
            synchronized (rankedSettlementMutex) {
                recordSettlement(match);
            }
            return;
        }
        recordSettlement(match);
    }

    private void recordSettlement(Match match) {
        Instant completedAt = match.completedAt().orElseThrow();
        MatchOutcome outcome = match.outcome().orElseThrow();
        matchSettlementRepository.record(new MatchSettlement(
                history(match, outcome, completedAt),
                deltas(match, outcome, completedAt),
                ratingUpdates(match, outcome, completedAt)));
    }

    private boolean requiresRankedSettlementSerialization(Match match) {
        return ratingService != null && match.origin() == io.github.xreatlabz.revprac.domain.matches.MatchOrigin.QUEUE_RANKED;
    }

    private static MatchHistoryEntry history(Match match, MatchOutcome outcome, Instant completedAt) {
        return new MatchHistoryEntry(
                match.id(),
                match.participants().playerOne(),
                match.participants().playerTwo(),
                match.arenaId(),
                match.kitId(),
                match.origin(),
                outcome.reason(),
                outcome.winnerId(),
                outcome.loserId(),
                match.activeTicksElapsed(),
                completedAt);
    }

    private static List<PlayerKitStatDelta> deltas(Match match, MatchOutcome outcome, Instant completedAt) {
        return List.of(
                deltaFor(match.participants().playerOne(), match, outcome, completedAt),
                deltaFor(match.participants().playerTwo(), match, outcome, completedAt));
    }

    private List<PlayerRating> ratingUpdates(Match match, MatchOutcome outcome, Instant completedAt) {
        if (ratingService == null) {
            return List.of();
        }
        return ratingService.progression(
                        match.origin(),
                        outcome,
                        match.participants().playerOne(),
                        match.participants().playerTwo(),
                        match.kitId(),
                        rankedBaseRating,
                        completedAt)
                .map(io.github.xreatlabz.revprac.application.ratings.RatingProgression::asList)
                .orElseGet(List::of);
    }

    private static PlayerKitStatDelta deltaFor(
            PlayerId playerId,
            Match match,
            MatchOutcome outcome,
            Instant completedAt) {
        long wins = won(playerId, outcome) ? 1 : 0;
        long losses = lost(playerId, outcome) ? 1 : 0;
        long forfeits = outcome.reason() == MatchEndReason.FORFEIT && lost(playerId, outcome) ? 1 : 0;
        long timeouts = outcome.reason() == MatchEndReason.TIMEOUT ? 1 : 0;
        long shutdowns = outcome.reason() == MatchEndReason.SHUTDOWN ? 1 : 0;
        return new PlayerKitStatDelta(
                playerId,
                match.kitId(),
                1,
                wins,
                losses,
                forfeits,
                timeouts,
                shutdowns,
                completedAt);
    }

    private static boolean won(PlayerId playerId, MatchOutcome outcome) {
        return outcome.winnerId().equals(Optional.of(playerId));
    }

    private static boolean lost(PlayerId playerId, MatchOutcome outcome) {
        return outcome.loserId().equals(Optional.of(playerId));
    }
}
