package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.application.config.StorageConfig;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresJdbcStorageFactoryTest {

    private static final String SCHEMA = "revprac_test";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetDatabaseSchema() throws Exception {
        resetSchema();
    }

    @Test
    void migrationsCreateExpectedTablesAndSeedDefaultSeason() throws Exception {
        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            assertTrue(tableExists("player_profiles"));
            assertTrue(tableExists("player_ratings"));
            assertTrue(tableExists("match_history"));
            assertTrue(tableExists("player_kit_stats"));
            assertTrue(tableExists("seasons"));
            assertTrue(tableExists("runtime_player_sessions"));
            assertTrue(tableExists("runtime_pending_restorations"));
            assertTrue(tableExists("runtime_queue_tickets"));
            assertTrue(tableExists("runtime_matches"));
            assertTrue(tableExists("audit_log"));
            assertEquals(1L, countSuccessfulMigrationRows("1"));
            assertEquals(1L, countSuccessfulMigrationRows("2"));
            assertEquals(1L, countSuccessfulMigrationRows("3"));
            assertEquals(1L, countSuccessfulMigrationRows("4"));
            assertEquals(1L, countSuccessfulMigrationRows("5"));
            assertEquals("default", activeSeasonId());
        }
    }

    @Test
    void profilesRatingsAndSettlementsSurviveCloseAndDuplicateSettlementRemainsIdempotent() throws Exception {
        PlayerId winnerId = player("pg-winner");
        PlayerId loserId = player("pg-loser");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile expectedProfile = new PlayerProfile(
                winnerId, Optional.of("WinnerName"), Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L));
        MatchSettlement settlement = settlement(
                "pg-settlement",
                winnerId,
                loserId,
                kitId,
                Instant.parse("2026-05-04T18:00:00Z"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.playerProfiles().upsert(expectedProfile);
            storage.matchSettlements().record(settlement);
            storage.matchSettlements().record(settlement);

            assertEquals(1L, countRows("match_history"));
            assertEquals(2L, countRows("player_kit_stats"));
            assertEquals(2L, countRows("player_ratings"));
        }

        try (StorageHandle reopened = openStorage(tempDir.resolve("plugin-data"))) {
            assertEquals(expectedProfile, reopened.playerProfiles().find(winnerId).orElseThrow());
            assertEquals(1016, reopened.playerRatings().find(winnerId, kitId).orElseThrow().rating());
            assertEquals(1L, reopened.matchSettlements().findStats(winnerId, kitId).orElseThrow().wins());
            assertEquals(
                    settlement.history().matchId(),
                    reopened.matchSettlements().findRecentHistory(winnerId, 10, 0).getFirst().matchId());
        }
    }

    @Test
    void ratingWriteFailureRollsBackHistoryAndStats() throws Exception {
        PlayerId winnerId = player("pg-rollback-winner");
        PlayerId loserId = player("pg-rollback-loser");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement settlement = settlement(
                "pg-rollback",
                winnerId,
                loserId,
                kitId,
                Instant.parse("2026-05-04T19:00:00Z"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            installFailingRatingTrigger();

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.matchSettlements().record(settlement));

            assertTrue(failure.getMessage().contains(settlement.history().matchId().value().toString()));
            assertEquals(0L, countRows("match_history"));
            assertEquals(0L, countRows("player_kit_stats"));
            assertEquals(0L, countRows("player_ratings"));
        }
    }

    @Test
    void playerRecordImportRollbackRestoresEarlierProfileAndRatingWritesWhenLaterStatsInsertFails() throws Exception {
        PlayerId playerId = player("pg-transfer-rollback-player");
        PlayerId opponentId = player("pg-transfer-rollback-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile existingProfile = new PlayerProfile(
                playerId,
                Optional.of("OldName"),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(2_000L));
        PlayerRating existingRating = new PlayerRating(playerId, kitId, 900, 1, 8, Instant.ofEpochMilli(2_500L));
        PlayerRecordBundle importedBundle = new PlayerRecordBundle(
                new PlayerProfile(
                        playerId,
                        Optional.of("NewName"),
                        Instant.ofEpochMilli(1_000L),
                        Instant.ofEpochMilli(5_000L)),
                List.of(new PlayerRating(playerId, kitId, 1240, 9, 4, Instant.ofEpochMilli(5_000L))),
                List.of(new PlayerKitStats(
                        playerId,
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        Instant.ofEpochMilli(5_000L))),
                List.of(settlement(
                                "pg-transfer-rollback-history",
                                playerId,
                                opponentId,
                                kitId,
                                Instant.parse("2026-05-05T10:00:00Z"))
                        .history()));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.playerProfiles().upsert(existingProfile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(existingRating));
            installFailingPlayerStatsTrigger();

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.playerRecordTransfers().importBundle(importedBundle));

            assertTrue(failure.getMessage().contains(playerId.value().toString()));
            assertEquals(existingProfile, storage.playerProfiles().find(playerId).orElseThrow());
            assertEquals(existingRating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertTrue(storage.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findAllHistory(playerId).isEmpty());
            assertEquals(0L, countRows("match_history"));
            assertEquals(0L, countRows("player_kit_stats"));
            assertEquals(1L, countRows("player_ratings"));
        }
    }

    @Test
    void recentHistoryOrdersByCompletedAtThenMatchIdTextDescending() throws Exception {
        PlayerId playerId = player("pg-history-player");
        PlayerId firstOpponent = player("pg-history-one");
        PlayerId secondOpponent = player("pg-history-two");
        KitId kitId = new KitId("nodebuff");
        Instant completedAt = Instant.parse("2026-05-04T20:00:00Z");
        MatchId textHigherButUuidLower = new MatchId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        MatchId textLowerButUuidHigher = new MatchId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.matchSettlements().record(settlement(
                    textHigherButUuidLower,
                    playerId,
                    firstOpponent,
                    kitId,
                    completedAt));
            storage.matchSettlements().record(settlement(
                    textLowerButUuidHigher,
                    playerId,
                    secondOpponent,
                    kitId,
                    completedAt));

            List<MatchHistoryEntry> recentHistory = storage.matchSettlements().findRecentHistory(playerId, 10, 0);

            assertEquals(List.of(textHigherButUuidLower, textLowerButUuidHigher), recentHistory.stream()
                    .map(MatchHistoryEntry::matchId)
                    .toList());
        }
    }

    @Test
    void activeSeasonScopesRatingsStatsAndHistory() throws Exception {
        PlayerId playerId = player("pg-season-player");
        PlayerId opponentId = player("pg-season-opponent");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement defaultSeasonSettlement = settlement(
                "pg-season-default",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-04T21:00:00Z"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.matchSettlements().record(defaultSeasonSettlement);
            assertEquals("default", activeSeasonId());
            insertSeason("beta", true);
        }

        try (StorageHandle reopened = openStorage(tempDir.resolve("plugin-data"))) {
            assertEquals("beta", activeSeasonId());
            assertTrue(reopened.playerRatings().find(playerId, kitId).isEmpty());
            assertTrue(reopened.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(reopened.matchSettlements().findRecentHistory(playerId, 10, 0).isEmpty());
            assertTrue(reopened.matchSettlements()
                    .findHistory(defaultSeasonSettlement.history().matchId())
                    .isEmpty());
        }
    }

    @Test
    void changingTheActiveSeasonAffectsSubsequentOperationsWithoutReopeningStorage() throws Exception {
        PlayerId playerId = player("pg-season-runtime-player");
        PlayerId opponentId = player("pg-season-runtime-opponent");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement defaultSeasonSettlement = settlement(
                "pg-season-runtime-default",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-04T22:00:00Z"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.matchSettlements().record(defaultSeasonSettlement);
            storage.playerRatings().upsert(new PlayerRating(playerId, kitId, 1200, 5, 2, Instant.ofEpochMilli(23_000L)));

            assertEquals("default", activeSeasonId());
            assertEquals(1200, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());

            insertSeason("beta", true);

            assertEquals("beta", activeSeasonId());
            assertTrue(storage.playerRatings().find(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findRecentHistory(playerId, 10, 0).isEmpty());
            assertTrue(storage.matchSettlements()
                    .findHistory(defaultSeasonSettlement.history().matchId())
                    .isEmpty());

            MatchSettlement betaSettlement = settlement(
                    "pg-season-runtime-beta",
                    playerId,
                    opponentId,
                    kitId,
                    Instant.parse("2026-05-04T23:00:00Z"));
            storage.matchSettlements().record(betaSettlement);
            storage.playerRatings().upsert(new PlayerRating(playerId, kitId, 1300, 7, 3, Instant.ofEpochMilli(24_000L)));

            assertEquals(1300, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());
            assertEquals(1L, storage.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    List.of(betaSettlement.history().matchId()),
                    storage.matchSettlements().findRecentHistory(playerId, 10, 0).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(2L, countRows("match_history"));
            assertEquals(4L, countRows("player_ratings"));
            assertEquals(4L, countRows("player_kit_stats"));
        }
    }

    @Test
    void playerRecordImportCanCopyHistoryIntoANewActiveSeason() throws Exception {
        PlayerId playerId = player("pg-transfer-season-copy-player");
        PlayerId opponentId = player("pg-transfer-season-copy-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile profile = new PlayerProfile(
                playerId,
                Optional.of("PgSeasonCopy"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"));
        PlayerRating rating = new PlayerRating(playerId, kitId, 1250, 8, 2, Instant.parse("2026-05-06T00:00:00Z"));
        MatchSettlement settlement = settlement(
                "pg-transfer-season-copy-history",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-06T12:00:00Z"));

        try (StorageHandle storage = openStorage(tempDir.resolve("plugin-data"))) {
            storage.playerProfiles().upsert(profile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(rating));
            storage.matchSettlements().record(settlement);
            PlayerRecordBundle bundle = storage.playerRecordTransfers().exportBundle(playerId);

            insertSeason("beta", true);
            assertEquals("beta", activeSeasonId());

            storage.playerRecordTransfers().importBundle(bundle);

            assertEquals(settlement.ratingUpdates().getFirst(), storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertEquals(1L, storage.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    Optional.of(settlement.history()),
                    storage.matchSettlements().findHistory(settlement.history().matchId()));
            assertEquals(1L, countRowsWhereSeason("match_history", "default"));
            assertEquals(1L, countRowsWhereSeason("match_history", "beta"));
        }
    }

    private StorageHandle openStorage(Path dataFolder) throws Exception {
        StorageConfig.PostgreSqlConfig postgresql = new StorageConfig.PostgreSqlConfig(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                SCHEMA);
        StorageConfig storageConfig = new StorageConfig(StorageConfig.POSTGRESQL_BACKEND, "data/revprac.db", postgresql, 4);
        Class<?> factoryClass = Class.forName("io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcStorageFactory");
        Method create = factoryClass.getMethod("create", Path.class, StorageConfig.class);
        Object runtime = invoke(create, null, dataFolder, storageConfig);
        return new StorageHandle(runtime);
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

    private static boolean tableExists(String tableName) throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement statement = connection.prepareStatement(
                        "select 1 from information_schema.tables where table_schema = ? and table_name = ?")) {
            statement.setString(1, SCHEMA);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long countSuccessfulMigrationRows(String version) throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement statement = connection.prepareStatement(
                        "select count(*) from " + SCHEMA + ".flyway_schema_history where version = ? and success")) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long countRows(String tableName) throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement statement =
                        connection.prepareStatement("select count(*) from " + SCHEMA + "." + tableName);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long countRowsWhereSeason(String tableName, String seasonId) throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement statement = connection.prepareStatement(
                        "select count(*) from " + SCHEMA + "." + tableName + " where season_id = ?")) {
            statement.setString(1, seasonId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String activeSeasonId() throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement statement = connection.prepareStatement(
                        "select season_id from " + SCHEMA + ".seasons where active")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "expected an active season row");
                return resultSet.getString(1);
            }
        }
    }

    private static void insertSeason(String seasonId, boolean active) throws Exception {
        try (Connection connection = openPostgres()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deactivate =
                            connection.prepareStatement("update " + SCHEMA + ".seasons set active = false");
                    PreparedStatement insert = connection.prepareStatement(
                            "insert into " + SCHEMA + ".seasons "
                                    + "(season_id, active, created_at, activated_at) values (?, ?, ?, ?)");
                    PreparedStatement activate = connection.prepareStatement(
                            "update " + SCHEMA + ".seasons set active = ? where season_id = ?")) {
                deactivate.executeUpdate();
                insert.setString(1, seasonId);
                insert.setBoolean(2, active);
                insert.setLong(3, 20_000L);
                if (active) {
                    insert.setLong(4, 20_000L);
                } else {
                    insert.setNull(4, Types.BIGINT);
                }
                insert.executeUpdate();
                if (active) {
                    activate.setBoolean(1, true);
                    activate.setString(2, seasonId);
                    activate.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void installFailingRatingTrigger() throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement function = connection.prepareStatement(
                        "create or replace function " + SCHEMA + ".fail_player_ratings_insert() returns trigger as $$ "
                                + "begin raise exception 'forced player_ratings failure'; end; $$ language plpgsql");
                PreparedStatement trigger = connection.prepareStatement(
                        "create trigger fail_player_ratings_insert before insert on " + SCHEMA + ".player_ratings "
                                + "for each row execute function " + SCHEMA + ".fail_player_ratings_insert()")) {
            function.executeUpdate();
            trigger.executeUpdate();
        }
    }

    private static void installFailingPlayerStatsTrigger() throws Exception {
        try (Connection connection = openPostgres();
                PreparedStatement function = connection.prepareStatement(
                        "create or replace function " + SCHEMA + ".fail_player_kit_stats_insert() returns trigger as $$ "
                                + "begin raise exception 'forced player_kit_stats failure'; end; $$ language plpgsql");
                PreparedStatement trigger = connection.prepareStatement(
                        "create trigger fail_player_kit_stats_insert before insert on " + SCHEMA + ".player_kit_stats "
                                + "for each row execute function " + SCHEMA + ".fail_player_kit_stats_insert()")) {
            function.executeUpdate();
            trigger.executeUpdate();
        }
    }

    private static Connection openPostgres() throws Exception {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (PreparedStatement statement = connection.prepareStatement("set search_path to " + SCHEMA)) {
            statement.execute();
        }
        return connection;
    }

    private static void resetSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement =
                        connection.prepareStatement("drop schema if exists " + SCHEMA + " cascade")) {
            statement.execute();
        }
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static MatchSettlement settlement(
            String seed,
            PlayerId winnerId,
            PlayerId loserId,
            KitId kitId,
            Instant completedAt) {
        return settlement(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))),
                winnerId,
                loserId,
                kitId,
                completedAt);
    }

    private static MatchSettlement settlement(
            MatchId matchId,
            PlayerId winnerId,
            PlayerId loserId,
            KitId kitId,
            Instant completedAt) {
        return new MatchSettlement(
                new MatchHistoryEntry(
                        matchId,
                        winnerId,
                        loserId,
                        new ArenaId("arena-ranked"),
                        kitId,
                        MatchOrigin.QUEUE_RANKED,
                        MatchEndReason.WIN,
                        Optional.of(winnerId),
                        Optional.of(loserId),
                        47,
                        completedAt),
                List.of(
                        new PlayerKitStatDelta(winnerId, kitId, 1, 1, 0, 0, 0, 0, completedAt),
                        new PlayerKitStatDelta(loserId, kitId, 1, 0, 1, 0, 0, 0, completedAt)),
                List.of(
                        new PlayerRating(winnerId, kitId, 1016, 1, 0, completedAt),
                        new PlayerRating(loserId, kitId, 984, 0, 1, completedAt)));
    }

    private record StorageHandle(
            AutoCloseable runtime,
            PlayerProfileRepository playerProfiles,
            PlayerRatingRepository playerRatings,
            MatchSettlementRepository matchSettlements,
            PlayerRecordTransferRepository playerRecordTransfers)
            implements AutoCloseable {

        private StorageHandle(Object runtime) throws Exception {
            this(
                    (AutoCloseable) runtime,
                    (PlayerProfileRepository) runtime.getClass().getMethod("playerProfileRepository").invoke(runtime),
                    (PlayerRatingRepository) runtime.getClass().getMethod("playerRatingRepository").invoke(runtime),
                    (MatchSettlementRepository) runtime.getClass().getMethod("matchSettlementRepository").invoke(runtime),
                    (PlayerRecordTransferRepository)
                            runtime.getClass().getMethod("playerRecordTransferRepository").invoke(runtime));
        }

        @Override
        public void close() throws Exception {
            runtime.close();
        }
    }
}
