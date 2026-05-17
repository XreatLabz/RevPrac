package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.PostMatchSummaryPort;
import java.util.Objects;

public final class PostMatchSummaryService {

    private final PostMatchSummaryPort postMatchSummaryPort;
    private final boolean enabled;

    private PostMatchSummaryService() {
        this.postMatchSummaryPort = null;
        this.enabled = false;
    }

    public PostMatchSummaryService(PostMatchSummaryPort postMatchSummaryPort) {
        this.postMatchSummaryPort = Objects.requireNonNull(postMatchSummaryPort, "postMatchSummaryPort");
        this.enabled = true;
    }

    public static PostMatchSummaryService noOp() {
        return new PostMatchSummaryService();
    }

    public void send(Match match, MatchSettlementResult settlementResult) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(settlementResult, "settlementResult");
        if (!enabled || !settlementResult.applied()) {
            return;
        }

        MatchOutcome outcome = match.outcome().orElseThrow(() -> new IllegalArgumentException("match must be completed"));
        if (outcome.reason() == MatchEndReason.SHUTDOWN) {
            return;
        }

        sendToParticipant(match, settlementResult, match.participants().playerOne(), outcome);
        sendToParticipant(match, settlementResult, match.participants().playerTwo(), outcome);
    }

    private void sendToParticipant(
            Match match,
            MatchSettlementResult settlementResult,
            PlayerId recipientId,
            MatchOutcome outcome) {
        try {
            PlayerId opponentId = match.participants().opponentOf(recipientId)
                    .orElseThrow(() -> new IllegalArgumentException("opponent not found"));
            String opponentLabel = postMatchSummaryPort.playerName(opponentId).orElse(opponentId.value().toString());
            StringBuilder message = new StringBuilder("Match summary: opponent=")
                    .append(opponentLabel)
                    .append(" kit=")
                    .append(match.kitId().value())
                    .append(" result=")
                    .append(resultFor(recipientId, outcome))
                    .append(" end=")
                    .append(endFor(outcome.reason()));
            if (match.origin() == io.github.xreatlabz.revprac.domain.matches.MatchOrigin.QUEUE_RANKED
                    && isDecisive(outcome.reason())) {
                settlementResult.ratingChangeFor(recipientId).ifPresent(change -> message.append(" rating=")
                        .append(change.newRating())
                        .append(" (")
                        .append(formatDelta(change.delta()))
                        .append(")"));
            }
            postMatchSummaryPort.send(recipientId, message.toString());
        } catch (RuntimeException ignored) {
            // Post-match summaries are best-effort and must not block teardown.
        }
    }

    private static String resultFor(PlayerId recipientId, MatchOutcome outcome) {
        if (outcome.winnerId().filter(recipientId::equals).isPresent()) {
            return "win";
        }
        if (outcome.loserId().filter(recipientId::equals).isPresent()) {
            return "loss";
        }
        return "draw";
    }

    private static String endFor(MatchEndReason endReason) {
        return switch (endReason) {
            case WIN -> "win";
            case FORFEIT -> "forfeit";
            case TIMEOUT -> "timeout";
            case SHUTDOWN -> "shutdown";
        };
    }

    private static boolean isDecisive(MatchEndReason endReason) {
        return endReason == MatchEndReason.WIN || endReason == MatchEndReason.FORFEIT;
    }

    private static String formatDelta(int delta) {
        return delta >= 0 ? "+" + delta : Integer.toString(delta);
    }
}
