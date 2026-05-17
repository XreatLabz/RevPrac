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
import org.flywaydb.core.api.configuration.FluentConfiguration;

public final class JdbcStorageFactory {

    private JdbcStorageFactory() {
    }

    public static JdbcStorageRuntime create(Path dataFolder, StorageConfig storageConfig) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(storageConfig, "storageConfig");

        try {
            Files.createDirectories(dataFolder);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare plugin data folder: " + dataFolder, exception);
        }

        if (StorageConfig.SQLITE_BACKEND.equals(storageConfig.backend())) {
            return createSqliteStorage(dataFolder, storageConfig);
        }
        if (StorageConfig.POSTGRESQL_BACKEND.equals(storageConfig.backend())) {
            return createPostgresqlStorage(dataFolder, storageConfig);
        }
        throw new IllegalStateException("Unsupported storage backend: " + storageConfig.backend());
    }

    private static JdbcStorageRuntime createSqliteStorage(Path dataFolder, StorageConfig storageConfig) {
        Path databasePath = resolveDatabasePath(dataFolder, storageConfig.sqlitePath());
        prepareSqliteDirectories(databasePath);

        HikariDataSource dataSource = createSqliteDataSource(databasePath, storageConfig.poolMaximumSize());
        try {
            migrateSqlite(dataSource, databasePath);
            return new JdbcStorageRuntime(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    private static JdbcStorageRuntime createPostgresqlStorage(Path dataFolder, StorageConfig storageConfig) {
        StorageConfig.PostgreSqlConfig postgresql = Objects.requireNonNull(storageConfig.postgresql(), "postgresql");
        HikariDataSource dataSource = createPostgresqlDataSource(postgresql, storageConfig.poolMaximumSize());
        try {
            migratePostgresql(dataSource, postgresql);
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

    private static void prepareSqliteDirectories(Path databasePath) {
        try {
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

    private static HikariDataSource createPostgresqlDataSource(
            StorageConfig.PostgreSqlConfig postgresql,
            int maximumPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("revprac-postgresql");
        hikariConfig.setJdbcUrl(postgresql.jdbcUrl());
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setUsername(postgresql.username());
        hikariConfig.setPassword(postgresql.password());
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setMinimumIdle(1);
        if (postgresql.schema() != null) {
            hikariConfig.setSchema(postgresql.schema());
            hikariConfig.addDataSourceProperty("currentSchema", postgresql.schema());
        }
        return new HikariDataSource(hikariConfig);
    }

    private static void migrateSqlite(HikariDataSource dataSource, Path databasePath) {
        try {
            Flyway.configure(JdbcStorageFactory.class.getClassLoader())
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/sqlite")
                    .load()
                    .migrate();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to migrate sqlite storage at " + databasePath, exception);
        }
    }

    private static void migratePostgresql(
            HikariDataSource dataSource,
            StorageConfig.PostgreSqlConfig postgresql) {
        try {
            FluentConfiguration configuration = Flyway.configure(JdbcStorageFactory.class.getClassLoader())
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/postgresql")
                    .createSchemas(true);
            if (postgresql.schema() != null) {
                configuration.schemas(postgresql.schema());
                configuration.defaultSchema(postgresql.schema());
            }
            configuration.load().migrate();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Failed to migrate postgresql storage at " + postgresql.jdbcUrl(),
                    exception);
        }
    }
}
