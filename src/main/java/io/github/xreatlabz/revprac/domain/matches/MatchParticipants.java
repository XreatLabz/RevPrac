package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import java.util.Optional;

public record MatchParticipants(PlayerId playerOne, PlayerId playerTwo) {

    public MatchParticipants {
        Objects.requireNonNull(playerOne, "playerOne");
        Objects.requireNonNull(playerTwo, "playerTwo");
        if (playerOne.equals(playerTwo)) {
            throw new IllegalArgumentException("match participants must be distinct");
        }
    }

    public boolean contains(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerOne.equals(playerId) || playerTwo.equals(playerId);
    }

    public Optional<MatchSide> sideOf(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (playerOne.equals(playerId)) {
            return Optional.of(MatchSide.ONE);
        }
        if (playerTwo.equals(playerId)) {
            return Optional.of(MatchSide.TWO);
        }
        return Optional.empty();
    }

    public Optional<PlayerId> opponentOf(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (playerOne.equals(playerId)) {
            return Optional.of(playerTwo);
        }
        if (playerTwo.equals(playerId)) {
            return Optional.of(playerOne);
        }
        return Optional.empty();
    }
}
