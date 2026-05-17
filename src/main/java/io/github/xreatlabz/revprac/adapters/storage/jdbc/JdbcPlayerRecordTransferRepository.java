package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class JdbcPlayerRecordTransferRepository implements PlayerRecordTransferRepository {

    private final DataSource dataSource;
    private final Supplier<String> activeSeasonIdSupplier;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final MatchSettlementRepository matchSettlementRepository;

    public JdbcPlayerRecordTransferRepository(
            DataSource dataSource,
            Supplier<String> activeSeasonIdSupplier,
            PlayerProfileRepository playerProfileRepository,
            PlayerRatingRepository playerRatingRepository,
            MatchSettlementRepository matchSettlementRepository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.activeSeasonIdSupplier = Objects.requireNonNull(activeSeasonIdSupplier, "activeSeasonIdSupplier");
        this.playerProfileRepository = Objects.requireNonNull(playerProfileRepository, "playerProfileRepository");
        this.playerRatingRepository = Objects.requireNonNull(playerRatingRepository, "playerRatingRepository");
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
    }

    @Override
    public PlayerRecordBundle exportBundle(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = dataSource.getConnection()) {
            String activeSeasonId = JdbcSeasonRepository.requireActiveSeasonId(connection);
            PlayerProfile profile = JdbcPlayerProfileRepository.find(connection, playerId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId.value() + "."));
            return new PlayerRecordBundle(
                    profile,
                    JdbcPlayerRatingRepository.findByPlayer(connection, activeSeasonId, playerId),
                    JdbcMatchSettlementRepository.findStatsByPlayer(connection, activeSeasonId, playerId),
                    JdbcMatchSettlementRepository.findAllHistory(connection, activeSeasonId, playerId));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to export player records for " + playerId.value(), exception);
        }
    }

    @Override
    public void importBundle(PlayerRecordBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        PlayerId playerId = bundle.profile().playerId();
        String activeSeasonId = activeSeasonId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                JdbcMatchSettlementRepository.validateImportHistoryCompatibility(
                        connection, activeSeasonId, playerId, bundle.history());
                JdbcPlayerProfileRepository.upsert(connection, bundle.profile());
                JdbcPlayerRatingRepository.replaceAllForPlayer(
                        connection, activeSeasonId, playerId, bundle.ratings());
                JdbcMatchSettlementRepository.importPlayerRecords(
                        connection, activeSeasonId, playerId, bundle.stats(), bundle.history());
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

    private String activeSeasonId() {
        String activeSeasonId = activeSeasonIdSupplier.get();
        if (activeSeasonId == null || activeSeasonId.isBlank()) {
            throw new IllegalStateException("No active season is configured");
        }
        return activeSeasonId;
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
