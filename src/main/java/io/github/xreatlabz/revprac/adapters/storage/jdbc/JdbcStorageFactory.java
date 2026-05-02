package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.xreatlabz.revprac.application.config.StorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import org.flywaydb.core.Flyway;

public final class JdbcStorageFactory {

    private JdbcStorageFactory() {
    }

    public static JdbcStorageRuntime create(Path dataFolder, StorageConfig storageConfig) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(storageConfig, "storageConfig");

        Path databasePath = resolveDatabasePath(dataFolder, storageConfig.sqlitePath());
        prepareDirectories(dataFolder, databasePath);

        HikariDataSource dataSource = createSqliteDataSource(databasePath, storageConfig.poolMaximumSize());
        try {
            migrate(dataSource, databasePath);
            return new JdbcStorageRuntime(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    private static Path resolveDatabasePath(Path dataFolder, String sqlitePath) {
        try {
            Path configuredPath = Path.of(sqlitePath);
            if (configuredPath.isAbsolute()) {
                return configuredPath.normalize();
            }
            Path normalizedDataFolder = dataFolder.toAbsolutePath().normalize();
            Path resolvedPath = normalizedDataFolder.resolve(configuredPath).normalize();
            if (!resolvedPath.startsWith(normalizedDataFolder)) {
                throw new IllegalStateException("Relative sqlite path escapes plugin data folder: " + sqlitePath);
            }
            return resolvedPath;
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("Failed to resolve sqlite path: " + sqlitePath, exception);
        }
    }

    private static void prepareDirectories(Path dataFolder, Path databasePath) {
        try {
            Files.createDirectories(dataFolder);
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare sqlite storage path: " + databasePath, exception);
        }
    }

    private static HikariDataSource createSqliteDataSource(Path databasePath, int maximumPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("revprac-sqlite");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionInitSql("PRAGMA busy_timeout = 5000");
        return new HikariDataSource(hikariConfig);
    }

    private static void migrate(HikariDataSource dataSource, Path databasePath) {
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to migrate sqlite storage at " + databasePath, exception);
        }
    }
}
