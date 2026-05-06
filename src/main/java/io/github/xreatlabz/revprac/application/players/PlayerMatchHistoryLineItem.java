package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PlayerMatchHistoryLineItem(
        MatchId matchId,
        KitId kitId,
        MatchOrigin origin,
        MatchEndReason endReason,
        PlayerId opponentId,
        String opponentName,
        Optional<Boolean> won,
        Instant completedAt) {

    public PlayerMatchHistoryLineItem {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(endReason, "endReason");
        Objects.requireNonNull(opponentId, "opponentId");
        opponentName = requireNonBlank(opponentName, "opponentName");
        Objects.requireNonNull(won, "won");
        won.ifPresent(value -> Objects.requireNonNull(value, "won value"));
        Objects.requireNonNull(completedAt, "completedAt");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
