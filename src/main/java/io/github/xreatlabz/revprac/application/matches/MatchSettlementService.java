package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MatchSettlementService {

    private final MatchSettlementRepository matchSettlementRepository;
    private final boolean enabled;

    private MatchSettlementService() {
        this.matchSettlementRepository = null;
        this.enabled = false;
    }

    public MatchSettlementService(MatchSettlementRepository matchSettlementRepository) {
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
        this.enabled = true;
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
        Instant completedAt = match.completedAt().orElseThrow();
        MatchOutcome outcome = match.outcome().orElseThrow();
        matchSettlementRepository.record(new MatchSettlement(history(match, outcome, completedAt), deltas(match, outcome, completedAt)));
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
