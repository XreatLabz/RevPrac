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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class JdbcPlayerRatingRepository implements PlayerRatingRepository {

    private final DataSource dataSource;
    private final Supplier<String> activeSeasonIdSupplier;

    public JdbcPlayerRatingRepository(DataSource dataSource, Supplier<String> activeSeasonIdSupplier) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.activeSeasonIdSupplier = Objects.requireNonNull(activeSeasonIdSupplier, "activeSeasonIdSupplier");
    }

    @Override
    public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, kit_id, rating, wins, losses, updated_at "
                                + "from player_ratings where season_id = ? and player_id = ? and kit_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.setString(3, kitId.value());
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
    public List<PlayerRating> findByPlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            return findByPlayer(connection, activeSeasonId, playerId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list player ratings for " + playerId.value(), exception);
        }
    }

    @Override
    public void replaceAllForPlayer(PlayerId playerId, List<PlayerRating> ratings) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ratings, "ratings");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                replaceAllForPlayer(connection, activeSeasonId, playerId, ratings);
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
            throw new IllegalStateException("Failed to replace player ratings for " + playerId.value(), exception);
        }
    }

    static void replaceAllForPlayer(
            Connection connection,
            String activeSeasonId,
            PlayerId playerId,
            List<PlayerRating> ratings)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ratings, "ratings");
        deleteRatings(connection, activeSeasonId, playerId);
        for (PlayerRating rating : ratings) {
            Objects.requireNonNull(rating, "ratings entry");
            if (!rating.playerId().equals(playerId)) {
                throw new IllegalArgumentException("rating player does not match replacement player");
            }
            insertOrReplaceRating(connection, activeSeasonId, rating);
        }
    }

    static List<PlayerRating> findByPlayer(Connection connection, String activeSeasonId, PlayerId playerId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        Objects.requireNonNull(playerId, "playerId");
        try (PreparedStatement statement = connection.prepareStatement(
                "select player_id, kit_id, rating, wins, losses, updated_at "
                        + "from player_ratings where season_id = ? and player_id = ? "
                        + "order by kit_id asc")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PlayerRating> ratings = new ArrayList<>();
                while (resultSet.next()) {
                    ratings.add(mapRating(resultSet));
                }
                return List.copyOf(ratings);
            }
        }
    }

    @Override
    public void upsert(PlayerRating rating) {
        Objects.requireNonNull(rating, "rating");
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertOrReplaceRating(connection, activeSeasonId, rating);
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

    private String activeSeasonId() {
        String activeSeasonId = activeSeasonIdSupplier.get();
        if (activeSeasonId == null || activeSeasonId.isBlank()) {
            throw new IllegalStateException("No active season is configured");
        }
        return activeSeasonId;
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

    static void deleteRatings(Connection connection, String activeSeasonId, PlayerId playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from player_ratings where season_id = ? and player_id = ?")) {
            statement.setString(1, activeSeasonId);
            statement.setString(2, playerId.value().toString());
            statement.executeUpdate();
        }
    }

    static void insertOrReplaceRating(Connection connection, String activeSeasonId, PlayerRating rating)
            throws SQLException {
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
