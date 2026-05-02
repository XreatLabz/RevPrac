package io.github.xreatlabz.revprac.application.config;

import java.util.Locale;
import java.util.Objects;

public record StorageConfig(String backend, String sqlitePath, int poolMaximumSize) {

    public static final String SQLITE_BACKEND = "sqlite";
    public static final String DEFAULT_SQLITE_PATH = "data/revprac.db";
    public static final int DEFAULT_POOL_MAXIMUM_SIZE = 4;

    public StorageConfig {
        backend = Objects.requireNonNull(backend, "backend").trim().toLowerCase(Locale.ROOT);
        sqlitePath = Objects.requireNonNull(sqlitePath, "sqlitePath").trim();
        if (!SQLITE_BACKEND.equals(backend)) {
            throw new IllegalArgumentException("storage backend must be sqlite");
        }
        if (sqlitePath.isEmpty()) {
            throw new IllegalArgumentException("sqlitePath must not be blank");
        }
        if (poolMaximumSize <= 0) {
            throw new IllegalArgumentException("poolMaximumSize must be positive");
        }
    }

    public static StorageConfig defaults() {
        return new StorageConfig(SQLITE_BACKEND, DEFAULT_SQLITE_PATH, DEFAULT_POOL_MAXIMUM_SIZE);
    }
}
