package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPlayerRatingRepository implements PlayerRatingRepository {

    private final DataSource dataSource;

    public JdbcPlayerRatingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, kit_id, rating, wins, losses, updated_at "
                                + "from player_ratings where player_id = ? and kit_id = ?")) {
            statement.setString(1, playerId.value().toString());
            statement.setString(2, kitId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRating(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load player rating for " + playerId.value() + " and kit " + kitId.value(),
                    exception);
        }
    }

    @Override
    public void upsert(PlayerRating rating) {
        Objects.requireNonNull(rating, "rating");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into player_ratings (player_id, kit_id, rating, wins, losses, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?) "
                            + "on conflict(player_id, kit_id) do update set "
                            + "rating = excluded.rating, "
                            + "wins = excluded.wins, "
                            + "losses = excluded.losses, "
                            + "updated_at = excluded.updated_at")) {
                statement.setString(1, rating.playerId().value().toString());
                statement.setString(2, rating.kitId().value());
                statement.setInt(3, rating.rating());
                statement.setInt(4, rating.wins());
                statement.setInt(5, rating.losses());
                statement.setLong(6, rating.updatedAt().toEpochMilli());
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to upsert player rating for "
                            + rating.playerId().value()
                            + " and kit "
                            + rating.kitId().value(),
                    exception);
        }
    }

    private static PlayerRating mapRating(ResultSet resultSet) throws SQLException {
        return new PlayerRating(
                new PlayerId(UUID.fromString(resultSet.getString("player_id"))),
                new KitId(resultSet.getString("kit_id")),
                resultSet.getInt("rating"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                Instant.ofEpochMilli(resultSet.getLong("updated_at")));
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
