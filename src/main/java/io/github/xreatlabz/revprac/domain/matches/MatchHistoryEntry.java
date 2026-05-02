package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MatchHistoryEntry(
        MatchId matchId,
        PlayerId playerOneId,
        PlayerId playerTwoId,
        ArenaId arenaId,
        KitId kitId,
        MatchOrigin origin,
        MatchEndReason endReason,
        Optional<PlayerId> winnerId,
        Optional<PlayerId> loserId,
        int activeTicks,
        Instant completedAt) {

    public MatchHistoryEntry {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(playerOneId, "playerOneId");
        Objects.requireNonNull(playerTwoId, "playerTwoId");
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(endReason, "endReason");
        winnerId = normalizeOptional(winnerId, "winnerId");
        loserId = normalizeOptional(loserId, "loserId");
        Objects.requireNonNull(completedAt, "completedAt");
        if (playerOneId.equals(playerTwoId)) {
            throw new IllegalArgumentException("match history participants must be distinct");
        }
        if (activeTicks < 0) {
            throw new IllegalArgumentException("activeTicks must not be negative");
        }
        switch (endReason) {
            case WIN, FORFEIT -> {
                PlayerId winner = winnerId.orElseThrow(
                        () -> new IllegalArgumentException("winner is required for " + endReason));
                PlayerId loser = loserId.orElseThrow(
                        () -> new IllegalArgumentException("loser is required for " + endReason));
                if (winner.equals(loser)) {
                    throw new IllegalArgumentException("winner and loser must be distinct");
                }
                if (!containsParticipant(playerOneId, playerTwoId, winner)
                        || !containsParticipant(playerOneId, playerTwoId, loser)) {
                    throw new IllegalArgumentException("winner and loser must be match participants");
                }
            }
            case TIMEOUT, SHUTDOWN -> {
                if (winnerId.isPresent() || loserId.isPresent()) {
                    throw new IllegalArgumentException("winner and loser must be absent for " + endReason);
                }
            }
        }
    }

    private static Optional<PlayerId> normalizeOptional(Optional<PlayerId> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        value.ifPresent(playerId -> Objects.requireNonNull(playerId, fieldName));
        return value;
    }

    private static boolean containsParticipant(PlayerId playerOneId, PlayerId playerTwoId, PlayerId playerId) {
        return playerOneId.equals(playerId) || playerTwoId.equals(playerId);
    }
}
