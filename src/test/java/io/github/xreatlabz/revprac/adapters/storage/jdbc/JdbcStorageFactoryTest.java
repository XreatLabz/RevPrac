package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.application.config.StorageConfig;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JdbcStorageFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyDatabaseRunsMigrationAndCreatesExpectedTables() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            assertTrue(storage.databasePath().toFile().isFile(), "sqlite database file should be created on open");
            assertTrue(tableExists(storage.databasePath(), "flyway_schema_history"));
            assertTrue(tableExists(storage.databasePath(), "player_profiles"));
            assertTrue(tableExists(storage.databasePath(), "player_ratings"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "1"));
        }
    }

    @Test
    void playerProfilesAndRatingsSurviveCloseAndReopen() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("profile-persist");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile expectedProfile = new PlayerProfile(
                playerId, Optional.of("PersistedName"), Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L));
        PlayerRating expectedRating =
                new PlayerRating(playerId, kitId, 1185, 12, 4, Instant.ofEpochMilli(3_000L));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(expectedProfile);
            storage.playerRatings().upsert(expectedRating);
        }

        try (StorageHandle reopened = openStorage(dataFolder, "storage/revprac.db")) {
            assertEquals(expectedProfile, reopened.playerProfiles().find(playerId).orElseThrow());
            assertEquals(expectedRating, reopened.playerRatings().find(playerId, kitId).orElseThrow());
        }
    }

    @Test
    void reopeningExistingDatabaseDoesNotReplayCurrentSchemaMigration() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");

        try (StorageHandle ignored = openStorage(dataFolder, "storage/revprac.db")) {
            // Initial open creates the database and applies V1.
        }

        try (StorageHandle reopened = openStorage(dataFolder, "storage/revprac.db")) {
            assertTrue(reopened.databasePath().toFile().isFile());
            assertEquals(1L, countSuccessfulMigrationRows(reopened.databasePath(), "1"));
        }
    }

    @Test
    void duplicateUpsertsUpdateExistingRowsInsteadOfCreatingDuplicates() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("duplicate-upsert");
        KitId kitId = new KitId("sumo");
        PlayerProfile firstProfile = new PlayerProfile(
                playerId, Optional.of("FirstName"), Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L));
        PlayerProfile updatedProfile = new PlayerProfile(
                playerId, Optional.of("UpdatedName"), Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(4_000L));
        PlayerRating firstRating =
                new PlayerRating(playerId, kitId, 1000, 1, 0, Instant.ofEpochMilli(2_500L));
        PlayerRating updatedRating =
                new PlayerRating(playerId, kitId, 1234, 9, 3, Instant.ofEpochMilli(5_000L));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(firstProfile);
            storage.playerProfiles().upsert(updatedProfile);
            storage.playerRatings().upsert(firstRating);
            storage.playerRatings().upsert(updatedRating);

            assertEquals(updatedProfile, storage.playerProfiles().find(playerId).orElseThrow());
            assertEquals(updatedRating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertEquals(1L, countRows(storage.databasePath(), "player_profiles"));
            assertEquals(1L, countRows(storage.databasePath(), "player_ratings"));
        }
    }

    @Test
    void duplicateProfileUpsertsPreserveTheOriginalFirstSeenInstant() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("stable-first-seen");
        Instant originalFirstSeenAt = Instant.ofEpochMilli(1_000L);
        PlayerProfile firstProfile = new PlayerProfile(
                playerId, Optional.of("FirstName"), originalFirstSeenAt, Instant.ofEpochMilli(2_000L));
        PlayerProfile conflictingProfile = new PlayerProfile(
                playerId, Optional.of("UpdatedName"), Instant.ofEpochMilli(500L), Instant.ofEpochMilli(4_000L));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(firstProfile);
            storage.playerProfiles().upsert(conflictingProfile);

            PlayerProfile storedProfile = storage.playerProfiles().find(playerId).orElseThrow();
            assertEquals(Optional.of("UpdatedName"), storedProfile.lastKnownName());
            assertEquals(originalFirstSeenAt, storedProfile.firstSeenAt());
            assertEquals(conflictingProfile.lastSeenAt(), storedProfile.lastSeenAt());
            assertEquals(1L, countRows(storage.databasePath(), "player_profiles"));
        }
    }

    @Test
    void closedRuntimeCausesRepositoryOperationsToSurfaceIllegalState() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("closed-runtime");
        KitId kitId = new KitId("boxing");
        PlayerProfileRepository playerProfiles;
        PlayerRatingRepository playerRatings;
        StorageHandle storage = openStorage(dataFolder, "storage/revprac.db");
        playerProfiles = storage.playerProfiles();
        playerRatings = storage.playerRatings();
        storage.close();

        IllegalStateException profileFailure =
                assertThrows(IllegalStateException.class, () -> playerProfiles.find(playerId));
        IllegalStateException ratingFailure = assertThrows(
                IllegalStateException.class,
                () -> playerRatings.upsert(new PlayerRating(playerId, kitId, 1100, 0, 0, Instant.ofEpochMilli(7_000L))));

        assertFalse(profileFailure.getMessage().isBlank());
        assertFalse(ratingFailure.getMessage().isBlank());
    }

    @Test
    void relativeSqlitePathsThatEscapeThePluginDataFolderAreRejected() {
        Path dataFolder = tempDir.resolve("plugin-data");
        Path escapedPath = dataFolder.resolve("../outside.db").normalize();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> openStorage(dataFolder, "../outside.db"));

        assertTrue(failure.getMessage().contains("../outside.db"));
        assertFalse(Files.exists(escapedPath));
    }

    @Test
    void absoluteSqlitePathsRemainAllowed() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        Path absoluteDatabasePath = tempDir.resolve("external/revprac.db").toAbsolutePath();

        try (StorageHandle storage = openStorage(dataFolder, absoluteDatabasePath.toString())) {
            assertEquals(absoluteDatabasePath.normalize(), storage.databasePath().normalize());
            assertTrue(Files.isRegularFile(absoluteDatabasePath));
        }
    }

    private StorageHandle openStorage(Path dataFolder, String sqlitePath) throws Exception {
        StorageConfig storageConfig = new StorageConfig(StorageConfig.SQLITE_BACKEND, sqlitePath, 4);
        Class<?> factoryClass = Class.forName("io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcStorageFactory");
        Method create = factoryClass.getMethod("create", Path.class, StorageConfig.class);
        Object runtime = invoke(create, null, dataFolder, storageConfig);
        return new StorageHandle(runtime, Path.of(sqlitePath).isAbsolute() ? Path.of(sqlitePath) : dataFolder.resolve(sqlitePath));
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static boolean tableExists(Path databasePath, String tableName) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement =
                        connection.prepareStatement("select 1 from sqlite_master where type = 'table' and name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long countSuccessfulMigrationRows(Path databasePath, String version) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement(
                        "select count(*) from flyway_schema_history where version = ? and success = 1")) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long countRows(Path databasePath, String tableName) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement("select count(*) from " + tableName);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static Connection openSqlite(Path databasePath) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class StorageHandle implements AutoCloseable {
        private final Object runtime;
        private final Path databasePath;

        private StorageHandle(Object runtime, Path databasePath) {
            this.runtime = runtime;
            this.databasePath = databasePath;
        }

        private Path databasePath() {
            return databasePath;
        }

        private PlayerProfileRepository playerProfiles() throws Exception {
            Method method = runtime.getClass().getMethod("playerProfileRepository");
            return (PlayerProfileRepository) invoke(method, runtime);
        }

        private PlayerRatingRepository playerRatings() throws Exception {
            Method method = runtime.getClass().getMethod("playerRatingRepository");
            return (PlayerRatingRepository) invoke(method, runtime);
        }

        @Override
        public void close() throws Exception {
            Method method = runtime.getClass().getMethod("close");
            invoke(method, runtime);
        }
    }
}
