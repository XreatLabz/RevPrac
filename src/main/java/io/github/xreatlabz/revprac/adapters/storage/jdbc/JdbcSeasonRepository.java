package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.domain.seasons.Season;
import io.github.xreatlabz.revprac.domain.seasons.SeasonId;
import io.github.xreatlabz.revprac.ports.seasons.SeasonRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcSeasonRepository implements SeasonRepository {

    private final DataSource dataSource;

    public JdbcSeasonRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<Season> findActive() {
        try (Connection connection = dataSource.getConnection()) {
            return findActive(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active season", exception);
        }
    }

    static Optional<Season> findActive(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        try (PreparedStatement statement = connection.prepareStatement(
                "select season_id, active, created_at, activated_at from seasons where active = ?")) {
            statement.setBoolean(1, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSeason(resultSet));
            }
        }
    }

    @Override
    public List<Season> findAll() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select season_id, active, created_at, activated_at from seasons order by created_at, season_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<Season> seasons = new ArrayList<>();
            while (resultSet.next()) {
                seasons.add(mapSeason(resultSet));
            }
            return List.copyOf(seasons);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load seasons", exception);
        }
    }

    @Override
    public void create(SeasonId seasonId, Instant createdAt) {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(createdAt, "createdAt");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into seasons (season_id, active, created_at, activated_at) values (?, ?, ?, ?)")) {
            statement.setString(1, seasonId.value());
            statement.setBoolean(2, false);
            statement.setLong(3, createdAt.toEpochMilli());
            statement.setObject(4, null);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create season " + seasonId.value(), exception);
        }
    }

    @Override
    public Season activate(SeasonId seasonId, Instant activatedAt) {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(activatedAt, "activatedAt");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deactivate = connection.prepareStatement("update seasons set active = ?");
                    PreparedStatement activate = connection.prepareStatement(
                            "update seasons set active = ?, activated_at = ? where season_id = ?");
                    PreparedStatement select = connection.prepareStatement(
                            "select season_id, active, created_at, activated_at from seasons where season_id = ?")) {
                deactivate.setBoolean(1, false);
                deactivate.executeUpdate();
                activate.setBoolean(1, true);
                activate.setLong(2, activatedAt.toEpochMilli());
                activate.setString(3, seasonId.value());
                int updated = activate.executeUpdate();
                if (updated != 1) {
                    connection.rollback();
                    throw new IllegalStateException("Unknown season: " + seasonId.value());
                }
                select.setString(1, seasonId.value());
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        throw new IllegalStateException("Unknown season: " + seasonId.value());
                    }
                    Season activatedSeason = mapSeason(resultSet);
                    connection.commit();
                    return activatedSeason;
                }
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to activate season " + seasonId.value(), exception);
        }
    }

    static String requireActiveSeasonId(Connection connection) throws SQLException {
        return findActive(connection)
                .map(Season::id)
                .map(SeasonId::value)
                .orElseThrow(() -> new IllegalStateException("No active season is configured"));
    }

    private static Season mapSeason(ResultSet resultSet) throws SQLException {
        Object activatedAtValue = resultSet.getObject("activated_at");
        return new Season(
                new SeasonId(resultSet.getString("season_id")),
                resultSet.getBoolean("active"),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                Optional.ofNullable((Number) activatedAtValue).map(Number::longValue).map(Instant::ofEpochMilli));
    }
}
