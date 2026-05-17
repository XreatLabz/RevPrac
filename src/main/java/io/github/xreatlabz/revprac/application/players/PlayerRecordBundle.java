package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PlayerRecordBundle(
        PlayerProfile profile,
        List<PlayerRating> ratings,
        List<PlayerKitStats> stats,
        List<MatchHistoryEntry> history) {

    public PlayerRecordBundle {
        Objects.requireNonNull(profile, "profile");
        ratings = copyAndValidate(ratings, "ratings");
        stats = copyAndValidate(stats, "stats");
        history = copyAndValidate(history, "history");

        PlayerId playerId = profile.playerId();
        ratings.forEach(rating -> requireSamePlayer(playerId, rating.playerId(), "rating"));
        stats.forEach(playerKitStats -> requireSamePlayer(playerId, playerKitStats.playerId(), "stats"));
        history.forEach(historyEntry -> {
            if (!historyEntry.playerOneId().equals(playerId) && !historyEntry.playerTwoId().equals(playerId)) {
                throw new IllegalArgumentException("history entry must include the profile player");
            }
        });
        requireDistinctRatingKits(ratings);
        requireDistinctStatsKits(stats);
        requireDistinctHistoryMatches(history);
    }

    private static <T> List<T> copyAndValidate(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        List<T> copy = List.copyOf(values);
        copy.forEach(value -> Objects.requireNonNull(value, fieldName + " entry"));
        return copy;
    }

    private static void requireSamePlayer(PlayerId expected, PlayerId actual, String fieldName) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(fieldName + " player must match the profile player");
        }
    }

    private static void requireDistinctRatingKits(List<PlayerRating> ratings) {
        Set<String> seenKitIds = new HashSet<>();
        for (PlayerRating rating : ratings) {
            String kitId = rating.kitId().value();
            if (!seenKitIds.add(kitId)) {
                throw new IllegalArgumentException("ratings contains duplicate kit-id: " + kitId + ".");
            }
        }
    }

    private static void requireDistinctStatsKits(List<PlayerKitStats> stats) {
        Set<String> seenKitIds = new HashSet<>();
        for (PlayerKitStats playerKitStats : stats) {
            String kitId = playerKitStats.kitId().value();
            if (!seenKitIds.add(kitId)) {
                throw new IllegalArgumentException("stats contains duplicate kit-id: " + kitId + ".");
            }
        }
    }

    private static void requireDistinctHistoryMatches(List<MatchHistoryEntry> history) {
        Set<String> seenMatchIds = new HashSet<>();
        for (MatchHistoryEntry historyEntry : history) {
            String matchId = historyEntry.matchId().value().toString();
            if (!seenMatchIds.add(matchId)) {
                throw new IllegalArgumentException("history contains duplicate match-id: " + matchId + ".");
            }
        }
    }
}
