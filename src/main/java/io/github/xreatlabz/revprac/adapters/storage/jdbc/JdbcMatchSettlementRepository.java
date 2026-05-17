package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class JdbcMatchSettlementRepository implements MatchSettlementRepository {

    private final DataSource dataSource;
    private final Supplier<String> activeSeasonIdSupplier;

    public JdbcMatchSettlementRepository(DataSource dataSource, Supplier<String> activeSeasonIdSupplier) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.activeSeasonIdSupplier = Objects.requireNonNull(activeSeasonIdSupplier, "activeSeasonIdSupplier");
    }

    @Override
    public boolean record(MatchSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted = insertHistory(connection, activeSeasonId, settlement.history());
                if (inserted == 0) {
                    connection.commit();
                    return false;
                }
                for (PlayerKitStatDelta delta : settlement.statDeltas()) {
                    upsertStats(connection, activeSeasonId, delta);
                }
                for (PlayerRating rating : settlement.ratingUpdates()) {
                    upsertRating(connection, activeSeasonId, rating);
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to record match settlement for " + settlement.history().matchId().value(),
                    exception);
        }
    }

    @Override
    public Optional<MatchHistoryEntry> findHistory(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, "
                                + "end_reason, winner_id, loser_id, active_ticks, completed_at "
                                + "from match_history where season_id = ? and match_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, matchId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapHistory(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load match history for " + matchId.value(), exception);
        }
    }

    @Override
    public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, kit_id, matches_played, wins, losses, forfeits, timeouts, shutdowns, "
                                + "updated_at from player_kit_stats where season_id = ? and player_id = ? and kit_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.setString(3, kitId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapStats(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load player kit stats for " + playerId.value() + " and kit " + kitId.value(),
                    exception);
        }
    }

    @Override
    public List<PlayerKitStats> findStatsByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            return findStatsByPlayer(connection, activeSeasonId, playerId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list player kit stats for " + playerId.value(), exception);
        }
    }

    @Override
    public List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, "
                                + "end_reason, winner_id, loser_id, active_ticks, completed_at "
                                + "from match_history "
                                + "where season_id = ? and (player_one_id = ? or player_two_id = ?) "
                                + "order by completed_at desc, match_id desc "
                                + "limit ? offset ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.setString(3, playerId.value().toString());
            statement.setInt(4, limit);
            statement.setInt(5, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MatchHistoryEntry> history = new ArrayList<>();
                while (resultSet.next()) {
                    history.add(mapHistory(resultSet));
                }
                return List.copyOf(history);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load recent match history for " + playerId.value(), exception);
        }
    }

    @Override
    public List<MatchHistoryEntry> findAllHistory(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            return findAllHistory(connection, activeSeasonId, playerId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list match history for " + playerId.value(), exception);
        }
    }

    @Override
    public void validateImportHistoryCompatibility(PlayerId playerId, List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(importedHistory, "importedHistory");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            validateImportHistoryCompatibility(connection, activeSeasonId, playerId, importedHistory);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to validate player record import history for " + playerId.value(),
                    exception);
        }
    }

    @Override
    public void importPlayerRecords(
            PlayerId playerId,
            List<PlayerKitStats> replacementStats,
            List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                importPlayerRecords(connection, activeSeasonId, playerId, replacementStats, importedHistory);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to import player records for " + playerId.value(), exception);
        }
    }

    @Override
    public void restoreImportedPlayerRecords(
            PlayerId playerId,
            List<PlayerKitStats> replacementStats,
            List<MatchHistoryEntry> importedHistory) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                restoreImportedPlayerRecords(connection, activeSeasonId, playerId, replacementStats, importedHistory);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to restore player records for " + playerId.value(), exception);
        }
    }

    static void importPlayerRecords(
            Connection connection,
            String activeSeasonId,
            PlayerId playerId,
            List<PlayerKitStats> replacementStats,
            List<MatchHistoryEntry> importedHistory)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateReplacementStats(playerId, replacementStats);
        validateImportHistoryCompatibility(connection, activeSeasonId, playerId, importedHistory);
        deletePlayerStats(connection, activeSeasonId, playerId);
        for (PlayerKitStats stats : replacementStats) {
            insertOrReplaceStats(connection, activeSeasonId, stats);
        }
        for (MatchHistoryEntry historyEntry : importedHistory) {
            insertHistory(connection, activeSeasonId, historyEntry);
        }
    }

    static void restoreImportedPlayerRecords(
            Connection connection,
            String activeSeasonId,
            PlayerId playerId,
            List<PlayerKitStats> replacementStats,
            List<MatchHistoryEntry> importedHistory)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(replacementStats, "replacementStats");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateReplacementStats(playerId, replacementStats);
        validateImportedHistory(playerId, importedHistory);
        deletePlayerStats(connection, activeSeasonId, playerId);
        for (PlayerKitStats stats : replacementStats) {
            insertOrReplaceStats(connection, activeSeasonId, stats);
        }
        Set<MatchId> importedMatchIds = new HashSet<>();
        for (MatchHistoryEntry historyEntry : importedHistory) {
            importedMatchIds.add(historyEntry.matchId());
        }
        for (MatchHistoryEntry existingEntry : findAllHistory(connection, activeSeasonId, playerId)) {
            if (!importedMatchIds.contains(existingEntry.matchId())) {
                deleteHistory(connection, activeSeasonId, existingEntry.matchId());
            }
        }
        for (MatchHistoryEntry historyEntry : importedHistory) {
            Optional<MatchHistoryEntry> existingEntry = findHistory(connection, activeSeasonId, historyEntry.matchId());
            if (existingEntry.isPresent() && !existingEntry.orElseThrow().equals(historyEntry)) {
                throw new IllegalStateException(
                        "Player record restore conflict for "
                                + playerId.value()
                                + ": existing match-id "
                                + historyEntry.matchId().value()
                                + " differs from the rollback history entry.");
            }
            if (existingEntry.isEmpty()) {
                insertHistory(connection, activeSeasonId, historyEntry);
            }
        }
    }

    static void validateImportHistoryCompatibility(
            Connection connection,
            String activeSeasonId,
            PlayerId playerId,
            List<MatchHistoryEntry> importedHistory)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(importedHistory, "importedHistory");
        validateImportedHistory(playerId, importedHistory);
        Set<MatchId> importedMatchIds = new HashSet<>();
        for (MatchHistoryEntry historyEntry : importedHistory) {
            importedMatchIds.add(historyEntry.matchId());
        }
        for (MatchHistoryEntry existingEntry : findAllHistory(connection, activeSeasonId, playerId)) {
            if (!importedMatchIds.contains(existingEntry.matchId())) {
                throw new IllegalStateException(
                        "Player record import history conflict for "
                                + playerId.value()
                                + ": existing current-season history contains rows not present in the imported bundle.");
            }
        }
        for (MatchHistoryEntry historyEntry : importedHistory) {
            Optional<MatchHistoryEntry> existingEntry = findHistory(connection, activeSeasonId, historyEntry.matchId());
            if (existingEntry.isPresent() && !existingEntry.orElseThrow().equals(historyEntry)) {
                throw new IllegalStateException(
                        "Player record import history conflict for "
                                + playerId.value()
                                + ": existing match-id "
                                + historyEntry.matchId().value()
                                + " differs from the imported history entry.");
            }
        }
    }

    static List<PlayerKitStats> findStatsByPlayer(Connection connection, String activeSeasonId, PlayerId playerId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        try (PreparedStatement statement = connection.prepareStatement(
                "select player_id, kit_id, matches_played, wins, losses, forfeits, timeouts, shutdowns, "
                        + "updated_at from player_kit_stats where season_id = ? and player_id = ? "
                        + "order by kit_id asc")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PlayerKitStats> stats = new ArrayList<>();
                while (resultSet.next()) {
                    stats.add(mapStats(resultSet));
                }
                return List.copyOf(stats);
            }
        }
    }

    static List<MatchHistoryEntry> findAllHistory(Connection connection, String activeSeasonId, PlayerId playerId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        try (PreparedStatement statement = connection.prepareStatement(
                "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, "
                        + "end_reason, winner_id, loser_id, active_ticks, completed_at "
                        + "from match_history "
                        + "where season_id = ? and (player_one_id = ? or player_two_id = ?) "
                        + "order by completed_at desc, match_id desc")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.setString(3, playerId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MatchHistoryEntry> history = new ArrayList<>();
                while (resultSet.next()) {
                    history.add(mapHistory(resultSet));
                }
                return List.copyOf(history);
            }
        }
    }

    static Optional<MatchHistoryEntry> findHistory(Connection connection, String activeSeasonId, MatchId matchId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(matchId, "matchId");
        try (PreparedStatement statement = connection.prepareStatement(
                "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, "
                        + "end_reason, winner_id, loser_id, active_ticks, completed_at "
                        + "from match_history where season_id = ? and match_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, matchId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapHistory(resultSet));
            }
        }
    }

    private String activeSeasonId() {
        String activeSeasonId = activeSeasonIdSupplier.get();
        if (activeSeasonId == null || activeSeasonId.isBlank()) {
            throw new IllegalStateException("No active season is configured");
        }
        return activeSeasonId;
    }

    static int insertHistory(Connection connection, String activeSeasonId, MatchHistoryEntry history)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into match_history (match_id, season_id, player_one_id, player_two_id, arena_id, kit_id, "
                        + "match_origin, end_reason, winner_id, loser_id, active_ticks, completed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(season_id, match_id) do nothing")) {
            statement.setString(1, history.matchId().value().toString());
            statement.setString(2, activeSeasonId);
            statement.setString(3, history.playerOneId().value().toString());
            statement.setString(4, history.playerTwoId().value().toString());
            statement.setString(5, history.arenaId().value());
            statement.setString(6, history.kitId().value());
            statement.setString(7, history.origin().name());
            statement.setString(8, history.endReason().name());
            statement.setString(9, history.winnerId().map(PlayerId::value).map(UUID::toString).orElse(null));
            statement.setString(10, history.loserId().map(PlayerId::value).map(UUID::toString).orElse(null));
            statement.setInt(11, history.activeTicks());
            statement.setLong(12, history.completedAt().toEpochMilli());
            return statement.executeUpdate();
        }
    }

    static void deletePlayerStats(Connection connection, String activeSeasonId, PlayerId playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from player_kit_stats where season_id = ? and player_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.executeUpdate();
        }
    }

    private static void validateReplacementStats(PlayerId playerId, List<PlayerKitStats> replacementStats) {
        for (PlayerKitStats stats : replacementStats) {
            Objects.requireNonNull(stats, "replacementStats entry");
            if (!stats.playerId().equals(playerId)) {
                throw new IllegalArgumentException("stats player does not match import player");
            }
        }
    }

    private static void validateImportedHistory(PlayerId playerId, List<MatchHistoryEntry> importedHistory) {
        for (MatchHistoryEntry historyEntry : importedHistory) {
            Objects.requireNonNull(historyEntry, "importedHistory entry");
            if (!historyEntry.playerOneId().equals(playerId) && !historyEntry.playerTwoId().equals(playerId)) {
                throw new IllegalArgumentException("history entry does not include import player");
            }
        }
    }

    private void upsertStats(Connection connection, String activeSeasonId, PlayerKitStatDelta delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_kit_stats (season_id, player_id, kit_id, matches_played, wins, losses, forfeits, "
                        + "timeouts, shutdowns, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(season_id, player_id, kit_id) do update set "
                        + "matches_played = player_kit_stats.matches_played + excluded.matches_played, "
                        + "wins = player_kit_stats.wins + excluded.wins, "
                        + "losses = player_kit_stats.losses + excluded.losses, "
                        + "forfeits = player_kit_stats.forfeits + excluded.forfeits, "
                        + "timeouts = player_kit_stats.timeouts + excluded.timeouts, "
                        + "shutdowns = player_kit_stats.shutdowns + excluded.shutdowns, "
                        + "updated_at = case "
                        + "when player_kit_stats.updated_at >= excluded.updated_at then player_kit_stats.updated_at "
                        + "else excluded.updated_at end")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, delta.playerId().value().toString());
            statement.setString(3, delta.kitId().value());
            statement.setLong(4, delta.matchesPlayed());
            statement.setLong(5, delta.wins());
            statement.setLong(6, delta.losses());
            statement.setLong(7, delta.forfeits());
            statement.setLong(8, delta.timeouts());
            statement.setLong(9, delta.shutdowns());
            statement.setLong(10, delta.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    static void insertOrReplaceStats(Connection connection, String activeSeasonId, PlayerKitStats stats)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_kit_stats (season_id, player_id, kit_id, matches_played, wins, losses, forfeits, "
                        + "timeouts, shutdowns, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(season_id, player_id, kit_id) do update set "
                        + "matches_played = excluded.matches_played, "
                        + "wins = excluded.wins, "
                        + "losses = excluded.losses, "
                        + "forfeits = excluded.forfeits, "
                        + "timeouts = excluded.timeouts, "
                        + "shutdowns = excluded.shutdowns, "
                        + "updated_at = excluded.updated_at")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, stats.playerId().value().toString());
            statement.setString(3, stats.kitId().value());
            statement.setLong(4, stats.matchesPlayed());
            statement.setLong(5, stats.wins());
            statement.setLong(6, stats.losses());
            statement.setLong(7, stats.forfeits());
            statement.setLong(8, stats.timeouts());
            statement.setLong(9, stats.shutdowns());
            statement.setLong(10, stats.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    static void deleteHistory(Connection connection, String activeSeasonId, MatchId matchId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(matchId, "matchId");
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from match_history where season_id = ? and match_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, matchId.value().toString());
            statement.executeUpdate();
        }
    }

    private void upsertRating(Connection connection, String activeSeasonId, PlayerRating rating) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_ratings (season_id, player_id, kit_id, rating, wins, losses, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(season_id, player_id, kit_id) do update set "
                        + "rating = excluded.rating, "
                        + "wins = excluded.wins, "
                        + "losses = excluded.losses, "
                        + "updated_at = excluded.updated_at")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, rating.playerId().value().toString());
            statement.setString(3, rating.kitId().value());
            statement.setInt(4, rating.rating());
            statement.setInt(5, rating.wins());
            statement.setInt(6, rating.losses());
            statement.setLong(7, rating.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    private static MatchHistoryEntry mapHistory(ResultSet resultSet) throws SQLException {
        return new MatchHistoryEntry(
                new MatchId(UUID.fromString(resultSet.getString("match_id"))),
                new PlayerId(UUID.fromString(resultSet.getString("player_one_id"))),
                new PlayerId(UUID.fromString(resultSet.getString("player_two_id"))),
                new ArenaId(resultSet.getString("arena_id")),
                new KitId(resultSet.getString("kit_id")),
                MatchOrigin.valueOf(resultSet.getString("match_origin")),
                MatchEndReason.valueOf(resultSet.getString("end_reason")),
                playerId(resultSet.getString("winner_id")),
                playerId(resultSet.getString("loser_id")),
                resultSet.getInt("active_ticks"),
                Instant.ofEpochMilli(resultSet.getLong("completed_at")));
    }

    private static PlayerKitStats mapStats(ResultSet resultSet) throws SQLException {
        return new PlayerKitStats(
                new PlayerId(UUID.fromString(resultSet.getString("player_id"))),
                new KitId(resultSet.getString("kit_id")),
                resultSet.getLong("matches_played"),
                resultSet.getLong("wins"),
                resultSet.getLong("losses"),
                resultSet.getLong("forfeits"),
                resultSet.getLong("timeouts"),
                resultSet.getLong("shutdowns"),
                Instant.ofEpochMilli(resultSet.getLong("updated_at")));
    }

    private static Optional<PlayerId> playerId(String value) {
        return value == null ? Optional.empty() : Optional.of(new PlayerId(UUID.fromString(value)));
    }

    private static void rollback(Connection connection, Exception originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean previousAutoCommit) throws SQLException {
        connection.setAutoCommit(previousAutoCommit);
    }
}
