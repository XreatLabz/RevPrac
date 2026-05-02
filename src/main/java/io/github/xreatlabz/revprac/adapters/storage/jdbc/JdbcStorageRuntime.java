package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.util.Objects;

public final class JdbcStorageRuntime implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerRatingRepository playerRatingRepository;

    JdbcStorageRuntime(HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.playerProfileRepository = new JdbcPlayerProfileRepository(dataSource);
        this.playerRatingRepository = new JdbcPlayerRatingRepository(dataSource);
    }

    public PlayerProfileRepository playerProfileRepository() {
        return playerProfileRepository;
    }

    public PlayerRatingRepository playerRatingRepository() {
        return playerRatingRepository;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
