package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import java.util.Optional;

public record MatchOutcome(MatchEndReason reason, Optional<PlayerId> winnerId, Optional<PlayerId> loserId) {

    public MatchOutcome {
        Objects.requireNonNull(reason, "reason");
        winnerId = normalizeOptional(winnerId, "winnerId");
        loserId = normalizeOptional(loserId, "loserId");

        switch (reason) {
            case WIN, FORFEIT -> {
                if (winnerId.isEmpty() || loserId.isEmpty()) {
                    throw new IllegalArgumentException("winner and loser are required for " + reason);
                }
                if (winnerId.get().equals(loserId.get())) {
                    throw new IllegalArgumentException("winner and loser must be distinct");
                }
            }
            case TIMEOUT, SHUTDOWN -> {
                if (winnerId.isPresent() || loserId.isPresent()) {
                    throw new IllegalArgumentException("winner and loser must be absent for " + reason);
                }
            }
        }
    }

    public static MatchOutcome win(PlayerId winnerId, PlayerId loserId) {
        return new MatchOutcome(MatchEndReason.WIN, Optional.of(Objects.requireNonNull(winnerId, "winnerId")),
                Optional.of(Objects.requireNonNull(loserId, "loserId")));
    }

    public static MatchOutcome forfeit(PlayerId winnerId, PlayerId loserId) {
        return new MatchOutcome(MatchEndReason.FORFEIT, Optional.of(Objects.requireNonNull(winnerId, "winnerId")),
                Optional.of(Objects.requireNonNull(loserId, "loserId")));
    }

    public static MatchOutcome timeout() {
        return new MatchOutcome(MatchEndReason.TIMEOUT, Optional.empty(), Optional.empty());
    }

    public static MatchOutcome shutdown() {
        return new MatchOutcome(MatchEndReason.SHUTDOWN, Optional.empty(), Optional.empty());
    }

    private static Optional<PlayerId> normalizeOptional(Optional<PlayerId> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        value.ifPresent(playerId -> Objects.requireNonNull(playerId, fieldName));
        return value;
    }
}
