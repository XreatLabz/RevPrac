package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcMatchSettlementRepository implements MatchSettlementRepository {

    private final DataSource dataSource;

    public JdbcMatchSettlementRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void record(MatchSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted = insertHistory(connection, settlement.history());
                if (inserted == 0) {
                    connection.commit();
                    return;
                }
                for (PlayerKitStatDelta delta : settlement.statDeltas()) {
                    upsertStats(connection, delta);
                }
                connection.commit();
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select match_id, player_one_id, player_two_id, arena_id, kit_id, match_origin, "
                                + "end_reason, winner_id, loser_id, active_ticks, completed_at "
                                + "from match_history where match_id = ?")) {
            statement.setString(1, matchId.value().toString());
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, kit_id, matches_played, wins, losses, forfeits, timeouts, shutdowns, "
                                + "updated_at from player_kit_stats where player_id = ? and kit_id = ?")) {
            statement.setString(1, playerId.value().toString());
            statement.setString(2, kitId.value());
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

    private static int insertHistory(Connection connection, MatchHistoryEntry history) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into match_history (match_id, player_one_id, player_two_id, arena_id, kit_id, "
                        + "match_origin, end_reason, winner_id, loser_id, active_ticks, completed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(match_id) do nothing")) {
            statement.setString(1, history.matchId().value().toString());
            statement.setString(2, history.playerOneId().value().toString());
            statement.setString(3, history.playerTwoId().value().toString());
            statement.setString(4, history.arenaId().value());
            statement.setString(5, history.kitId().value());
            statement.setString(6, history.origin().name());
            statement.setString(7, history.endReason().name());
            statement.setString(8, history.winnerId().map(PlayerId::value).map(UUID::toString).orElse(null));
            statement.setString(9, history.loserId().map(PlayerId::value).map(UUID::toString).orElse(null));
            statement.setInt(10, history.activeTicks());
            statement.setLong(11, history.completedAt().toEpochMilli());
            return statement.executeUpdate();
        }
    }

    private static void upsertStats(Connection connection, PlayerKitStatDelta delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into player_kit_stats (player_id, kit_id, matches_played, wins, losses, forfeits, "
                        + "timeouts, shutdowns, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(player_id, kit_id) do update set "
                        + "matches_played = player_kit_stats.matches_played + excluded.matches_played, "
                        + "wins = player_kit_stats.wins + excluded.wins, "
                        + "losses = player_kit_stats.losses + excluded.losses, "
                        + "forfeits = player_kit_stats.forfeits + excluded.forfeits, "
                        + "timeouts = player_kit_stats.timeouts + excluded.timeouts, "
                        + "shutdowns = player_kit_stats.shutdowns + excluded.shutdowns, "
                        + "updated_at = max(player_kit_stats.updated_at, excluded.updated_at)")) {
            statement.setString(1, delta.playerId().value().toString());
            statement.setString(2, delta.kitId().value());
            statement.setLong(3, delta.matchesPlayed());
            statement.setLong(4, delta.wins());
            statement.setLong(5, delta.losses());
            statement.setLong(6, delta.forfeits());
            statement.setLong(7, delta.timeouts());
            statement.setLong(8, delta.shutdowns());
            statement.setLong(9, delta.updatedAt().toEpochMilli());
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

    private static void rollback(Connection connection, SQLException originalFailure) {
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
