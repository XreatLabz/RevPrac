package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.github.xreatlabz.revprac.application.seasons.SeasonService;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.operations.AuditRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import io.github.xreatlabz.revprac.ports.seasons.SeasonRepository;
import java.util.Objects;
import java.util.function.Supplier;

public final class JdbcStorageRuntime implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final SeasonRepository seasonRepository;
    private final SeasonService seasonService;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final MatchSettlementRepository matchSettlementRepository;
    private final PlayerRecordTransferRepository playerRecordTransferRepository;
    private final RuntimeRecoveryRepository runtimeRecoveryRepository;
    private final AuditRepository auditRepository;

    JdbcStorageRuntime(HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.seasonRepository = new JdbcSeasonRepository(dataSource);
        this.seasonService = new SeasonService(seasonRepository);
        Supplier<String> activeSeasonIdSupplier = seasonService::activeSeasonId;
        this.playerProfileRepository = new JdbcPlayerProfileRepository(dataSource);
        this.playerRatingRepository = new JdbcPlayerRatingRepository(dataSource, activeSeasonIdSupplier);
        this.matchSettlementRepository = new JdbcMatchSettlementRepository(dataSource, activeSeasonIdSupplier);
        this.playerRecordTransferRepository = new JdbcPlayerRecordTransferRepository(
                dataSource,
                activeSeasonIdSupplier,
                playerProfileRepository,
                playerRatingRepository,
                matchSettlementRepository);
        this.runtimeRecoveryRepository = new JdbcRuntimeRecoveryRepository(dataSource);
        this.auditRepository = new JdbcAuditRepository(dataSource);
    }

    public PlayerProfileRepository playerProfileRepository() {
        return playerProfileRepository;
    }

    public PlayerRatingRepository playerRatingRepository() {
        return playerRatingRepository;
    }

    public MatchSettlementRepository matchSettlementRepository() {
        return matchSettlementRepository;
    }

    public PlayerRecordTransferRepository playerRecordTransferRepository() {
        return playerRecordTransferRepository;
    }

    public SeasonRepository seasonRepository() {
        return seasonRepository;
    }

    public RuntimeRecoveryRepository runtimeRecoveryRepository() {
        return runtimeRecoveryRepository;
    }

    public SeasonService seasonService() {
        return seasonService;
    }

    public AuditRepository auditRepository() {
        return auditRepository;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
