package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPlayerProfileRepository implements PlayerProfileRepository {

    private final DataSource dataSource;

    public JdbcPlayerProfileRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<PlayerProfile> find(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select player_id, last_known_name, first_seen_at, last_seen_at "
                                + "from player_profiles where player_id = ?")) {
            statement.setString(1, playerId.value().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load player profile for " + playerId.value(), exception);
        }
    }

    @Override
    public void upsert(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into player_profiles (player_id, last_known_name, first_seen_at, last_seen_at) "
                            + "values (?, ?, ?, ?) "
                            + "on conflict(player_id) do update set "
                            + "last_known_name = excluded.last_known_name, "
                            + "first_seen_at = excluded.first_seen_at, "
                            + "last_seen_at = excluded.last_seen_at")) {
                statement.setString(1, profile.playerId().value().toString());
                statement.setString(2, profile.lastKnownName().orElse(null));
                statement.setLong(3, profile.firstSeenAt().toEpochMilli());
                statement.setLong(4, profile.lastSeenAt().toEpochMilli());
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
                    "Failed to upsert player profile for " + profile.playerId().value(), exception);
        }
    }

    private static PlayerProfile mapProfile(ResultSet resultSet) throws SQLException {
        return new PlayerProfile(
                new PlayerId(UUID.fromString(resultSet.getString("player_id"))),
                Optional.ofNullable(resultSet.getString("last_known_name")),
                Instant.ofEpochMilli(resultSet.getLong("first_seen_at")),
                Instant.ofEpochMilli(resultSet.getLong("last_seen_at")));
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
