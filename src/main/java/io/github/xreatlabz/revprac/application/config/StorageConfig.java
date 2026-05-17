package io.github.xreatlabz.revprac.application.config;

import java.util.Locale;
import java.util.Objects;

public record StorageConfig(String backend, String sqlitePath, PostgreSqlConfig postgresql, int poolMaximumSize) {

    public static final String SQLITE_BACKEND = "sqlite";
    public static final String POSTGRESQL_BACKEND = "postgresql";
    public static final String DEFAULT_SQLITE_PATH = "data/revprac.db";
    public static final int DEFAULT_POOL_MAXIMUM_SIZE = 4;

    public StorageConfig {
        backend = normalizeRequired(backend, "backend");
        if (!SQLITE_BACKEND.equals(backend) && !POSTGRESQL_BACKEND.equals(backend)) {
            throw new IllegalArgumentException("storage backend must be sqlite or postgresql");
        }
        if (SQLITE_BACKEND.equals(backend)) {
            sqlitePath = requireTrimmed(sqlitePath, "sqlitePath");
            if (postgresql != null) {
                throw new IllegalArgumentException("postgresql config must be absent for sqlite backend");
            }
        } else {
            sqlitePath = normalizeOptional(sqlitePath);
            if (postgresql == null) {
                throw new IllegalArgumentException("postgresql config is required for postgresql backend");
            }
        }
        if (poolMaximumSize <= 0) {
            throw new IllegalArgumentException("poolMaximumSize must be positive");
        }
    }

    public static StorageConfig defaults() {
        return new StorageConfig(SQLITE_BACKEND, DEFAULT_SQLITE_PATH, null, DEFAULT_POOL_MAXIMUM_SIZE);
    }

    public record PostgreSqlConfig(String jdbcUrl, String username, String password, String schema) {

        public PostgreSqlConfig {
            jdbcUrl = requireTrimmed(jdbcUrl, "jdbcUrl");
            username = requireTrimmed(username, "username");
            password = requireTrimmed(password, "password");
            schema = normalizeOptional(schema);
        }
    }

    private static String normalizeRequired(String value, String fieldName) {
        return requireTrimmed(Objects.requireNonNull(value, fieldName), fieldName).toLowerCase(Locale.ROOT);
    }

    private static String requireTrimmed(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
