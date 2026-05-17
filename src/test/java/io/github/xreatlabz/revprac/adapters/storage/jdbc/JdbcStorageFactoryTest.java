package io.github.xreatlabz.revprac.adapters.storage.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.xreatlabz.revprac.application.config.StorageConfig;
import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.PotionEffectSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.seasons.SeasonId;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.application.operations.AuditEntry;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.operations.AuditRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import io.github.xreatlabz.revprac.ports.seasons.SeasonRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;

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
            assertTrue(tableExists(storage.databasePath(), "match_history"));
            assertTrue(tableExists(storage.databasePath(), "player_kit_stats"));
            assertTrue(tableExists(storage.databasePath(), "seasons"));
            assertTrue(tableExists(storage.databasePath(), "runtime_player_sessions"));
            assertTrue(tableExists(storage.databasePath(), "runtime_pending_restorations"));
            assertTrue(tableExists(storage.databasePath(), "runtime_queue_tickets"));
            assertTrue(tableExists(storage.databasePath(), "runtime_matches"));
            assertTrue(tableExists(storage.databasePath(), "audit_log"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "1"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "2"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "3"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "4"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "5"));
            assertEquals("default", activeSeasonId(storage.databasePath()));
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
    void matchSettlementsSurviveCloseAndDuplicateRecordsAreIdempotent() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId winnerId = player("settlement-winner");
        PlayerId loserId = player("settlement-loser");
        KitId kitId = new KitId("nodebuff");
        MatchId matchId = new MatchId(UUID.nameUUIDFromBytes("settlement-match".getBytes(StandardCharsets.UTF_8)));
        Instant completedAt = Instant.ofEpochMilli(8_000L);
        MatchSettlement settlement = new MatchSettlement(
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

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.matchSettlements().record(settlement);
            storage.matchSettlements().record(settlement);

            assertEquals(1L, countRows(storage.databasePath(), "match_history"));
            assertEquals(2L, countRows(storage.databasePath(), "player_kit_stats"));
            assertEquals(2L, countRows(storage.databasePath(), "player_ratings"));
        }

        try (StorageHandle reopened = openStorage(dataFolder, "storage/revprac.db")) {
            MatchHistoryEntry history = reopened.matchSettlements().findHistory(matchId).orElseThrow();
            assertEquals(MatchOrigin.QUEUE_RANKED, history.origin());
            assertEquals(MatchEndReason.WIN, history.endReason());
            assertEquals(Optional.of(winnerId), history.winnerId());
            assertEquals(47, history.activeTicks());

            PlayerKitStats winnerStats = reopened.matchSettlements().findStats(winnerId, kitId).orElseThrow();
            PlayerKitStats loserStats = reopened.matchSettlements().findStats(loserId, kitId).orElseThrow();
            assertEquals(1L, winnerStats.matchesPlayed());
            assertEquals(1L, winnerStats.wins());
            assertEquals(0L, winnerStats.losses());
            assertEquals(1L, loserStats.matchesPlayed());
            assertEquals(0L, loserStats.wins());
            assertEquals(1L, loserStats.losses());

            PlayerRating winnerRating = reopened.playerRatings().find(winnerId, kitId).orElseThrow();
            PlayerRating loserRating = reopened.playerRatings().find(loserId, kitId).orElseThrow();
            assertEquals(1016, winnerRating.rating());
            assertEquals(1, winnerRating.wins());
            assertEquals(0, winnerRating.losses());
            assertEquals(984, loserRating.rating());
            assertEquals(0, loserRating.wins());
            assertEquals(1, loserRating.losses());
        }
    }

    @Test
    void recentHistoryQueriesMatchBothParticipantsOrderNewestFirstAndPaginate() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("history-player");
        PlayerId opponentOne = player("history-opponent-one");
        PlayerId opponentTwo = player("history-opponent-two");
        PlayerId opponentThree = player("history-opponent-three");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement first = settlement(
                "history-one",
                playerId,
                opponentOne,
                kitId,
                Instant.parse("2026-05-04T10:00:00Z"));
        MatchSettlement second = settlement(
                "history-two",
                opponentTwo,
                playerId,
                kitId,
                Instant.parse("2026-05-04T11:00:00Z"));
        MatchSettlement third = settlement(
                "history-three",
                playerId,
                opponentThree,
                kitId,
                Instant.parse("2026-05-04T12:00:00Z"));
        MatchSettlement unrelated = settlement(
                "history-unrelated",
                player("other-one"),
                player("other-two"),
                kitId,
                Instant.parse("2026-05-04T13:00:00Z"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.matchSettlements().record(first);
            storage.matchSettlements().record(second);
            storage.matchSettlements().record(third);
            storage.matchSettlements().record(unrelated);

            List<MatchHistoryEntry> firstPage = storage.matchSettlements().findRecentHistory(playerId, 2, 0);
            List<MatchHistoryEntry> secondPage = storage.matchSettlements().findRecentHistory(playerId, 2, 2);

            assertEquals(List.of(
                            third.history().matchId(),
                            second.history().matchId()),
                    firstPage.stream().map(MatchHistoryEntry::matchId).toList());
            assertEquals(List.of(
                            Instant.parse("2026-05-04T12:00:00Z"),
                            Instant.parse("2026-05-04T11:00:00Z")),
                    firstPage.stream().map(MatchHistoryEntry::completedAt).toList());
            assertEquals(List.of(first.history().matchId()), secondPage.stream().map(MatchHistoryEntry::matchId).toList());
            assertEquals(playerId, secondPage.getFirst().playerOneId());

            assertThrows(IllegalArgumentException.class, () -> storage.matchSettlements().findRecentHistory(playerId, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> storage.matchSettlements().findRecentHistory(playerId, 1, -1));
        }
    }

    @Test
    void recentHistoryBreaksCompletedAtTiesByMatchIdDescending() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("history-tie-player");
        PlayerId firstOpponent = player("history-tie-opponent-one");
        PlayerId secondOpponent = player("history-tie-opponent-two");
        KitId kitId = new KitId("nodebuff");
        Instant completedAt = Instant.parse("2026-05-04T15:00:00Z");
        MatchId lowerMatchId = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MatchId higherMatchId = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.matchSettlements().record(settlement(
                    lowerMatchId,
                    playerId,
                    firstOpponent,
                    kitId,
                    completedAt));
            storage.matchSettlements().record(settlement(
                    higherMatchId,
                    playerId,
                    secondOpponent,
                    kitId,
                    completedAt));

            List<MatchHistoryEntry> recentHistory = storage.matchSettlements().findRecentHistory(playerId, 2, 0);

            assertEquals(List.of(higherMatchId, lowerMatchId), recentHistory.stream()
                    .map(MatchHistoryEntry::matchId)
                    .toList());
        }
    }

    @Test
    void recentHistoryBreaksCompletedAtTiesByMatchIdTextDescending() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("history-tie-text-order-player");
        PlayerId firstOpponent = player("history-tie-text-order-opponent-one");
        PlayerId secondOpponent = player("history-tie-text-order-opponent-two");
        KitId kitId = new KitId("nodebuff");
        Instant completedAt = Instant.parse("2026-05-04T15:30:00Z");
        MatchId textHigherButUuidLower = new MatchId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        MatchId textLowerButUuidHigher = new MatchId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
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

            List<MatchHistoryEntry> recentHistory = storage.matchSettlements().findRecentHistory(playerId, 2, 0);

            assertEquals(List.of(textHigherButUuidLower, textLowerButUuidHigher), recentHistory.stream()
                    .map(MatchHistoryEntry::matchId)
                    .toList());
        }
    }

    @Test
    void ratingsStatsAndHistoryAreScopedToTheActiveSeason() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("season-scope-player");
        PlayerId opponentId = player("season-scope-opponent");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement defaultSeasonSettlement = settlement(
                "season-default",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-04T16:00:00Z"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.matchSettlements().record(defaultSeasonSettlement);
            storage.playerRatings().upsert(new PlayerRating(playerId, kitId, 1200, 5, 2, Instant.ofEpochMilli(10_000L)));

            assertEquals(1L, countRows(storage.databasePath(), "match_history"));
            assertEquals(1L, countRows(storage.databasePath(), "player_ratings"));
            assertEquals("default", activeSeasonId(storage.databasePath()));

            insertSeason(storage.databasePath(), "beta", true);
        }

        try (StorageHandle reopened = openStorage(dataFolder, "storage/revprac.db")) {
            assertEquals("beta", activeSeasonId(reopened.databasePath()));
            assertTrue(reopened.playerRatings().find(playerId, kitId).isEmpty());
            assertTrue(reopened.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(reopened.matchSettlements().findRecentHistory(playerId, 10, 0).isEmpty());
            assertTrue(reopened.matchSettlements()
                    .findHistory(defaultSeasonSettlement.history().matchId())
                    .isEmpty());

            MatchSettlement betaSettlement = settlement(
                    "season-beta",
                    playerId,
                    opponentId,
                    kitId,
                    Instant.parse("2026-05-04T17:00:00Z"));
            reopened.matchSettlements().record(betaSettlement);
            reopened.playerRatings().upsert(new PlayerRating(playerId, kitId, 1300, 7, 3, Instant.ofEpochMilli(11_000L)));

            assertEquals(1300, reopened.playerRatings().find(playerId, kitId).orElseThrow().rating());
            assertEquals(1L, reopened.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    List.of(betaSettlement.history().matchId()),
                    reopened.matchSettlements().findRecentHistory(playerId, 10, 0).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(2L, countRows(reopened.databasePath(), "match_history"));
            assertEquals(2L, countRows(reopened.databasePath(), "player_ratings"));
            assertEquals(4L, countRows(reopened.databasePath(), "player_kit_stats"));
        }
    }

    @Test
    void changingTheActiveSeasonAffectsSubsequentOperationsWithoutReopeningStorage() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("season-runtime-player");
        PlayerId opponentId = player("season-runtime-opponent");
        KitId kitId = new KitId("nodebuff");
        MatchSettlement defaultSeasonSettlement = settlement(
                "season-runtime-default",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-04T18:00:00Z"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.matchSettlements().record(defaultSeasonSettlement);
            storage.playerRatings().upsert(new PlayerRating(playerId, kitId, 1200, 5, 2, Instant.ofEpochMilli(12_000L)));

            assertEquals("default", activeSeasonId(storage.databasePath()));
            assertEquals(1200, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());

            insertSeason(storage.databasePath(), "beta", true);

            assertEquals("beta", activeSeasonId(storage.databasePath()));
            assertTrue(storage.playerRatings().find(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findRecentHistory(playerId, 10, 0).isEmpty());
            assertTrue(storage.matchSettlements()
                    .findHistory(defaultSeasonSettlement.history().matchId())
                    .isEmpty());

            MatchSettlement betaSettlement = settlement(
                    "season-runtime-beta",
                    playerId,
                    opponentId,
                    kitId,
                    Instant.parse("2026-05-04T19:00:00Z"));
            storage.matchSettlements().record(betaSettlement);
            storage.playerRatings().upsert(new PlayerRating(playerId, kitId, 1300, 7, 3, Instant.ofEpochMilli(13_000L)));

            assertEquals(1300, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());
            assertEquals(1L, storage.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    List.of(betaSettlement.history().matchId()),
                    storage.matchSettlements().findRecentHistory(playerId, 10, 0).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(2L, countRows(storage.databasePath(), "match_history"));
            assertEquals(2L, countRows(storage.databasePath(), "player_ratings"));
            assertEquals(4L, countRows(storage.databasePath(), "player_kit_stats"));
        }
    }

    @Test
    void migratingAV2DatabaseToV3PreservesLegacyRatingsHistoryAndStatsUnderDefaultSeason() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        Path databasePath = dataFolder.resolve("storage/revprac.db");
        PlayerId playerId = player("legacy-player");
        PlayerId opponentId = player("legacy-opponent");
        KitId kitId = new KitId("nodebuff");
        MatchId matchId = new MatchId(UUID.nameUUIDFromBytes("legacy-match".getBytes(StandardCharsets.UTF_8)));
        Instant updatedAt = Instant.ofEpochMilli(21_000L);
        Instant completedAt = Instant.ofEpochMilli(22_000L);

        seedLegacyV2Database(databasePath, playerId, opponentId, kitId, matchId, updatedAt, completedAt);

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            assertEquals("default", activeSeasonId(storage.databasePath()));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "1"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "2"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "3"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "4"));
            assertEquals(1L, countSuccessfulMigrationRows(storage.databasePath(), "5"));
            assertEquals(1188, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());
            assertEquals(matchId, storage.matchSettlements().findHistory(matchId).orElseThrow().matchId());
            assertEquals(1L, storage.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    List.of(matchId),
                    storage.matchSettlements().findRecentHistory(playerId, 10, 0).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "player_ratings", "default"));
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "match_history", "default"));
            assertEquals(2L, countRowsWhereSeason(storage.databasePath(), "player_kit_stats", "default"));
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
            assertEquals(1L, countSuccessfulMigrationRows(reopened.databasePath(), "2"));
            assertEquals(1L, countSuccessfulMigrationRows(reopened.databasePath(), "3"));
            assertEquals(1L, countSuccessfulMigrationRows(reopened.databasePath(), "4"));
            assertEquals(1L, countSuccessfulMigrationRows(reopened.databasePath(), "5"));
        }
    }

    @Test
    void ratingWriteFailureRollsBackHistoryAndStats() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId winnerId = player("rollback-winner");
        PlayerId loserId = player("rollback-loser");
        KitId kitId = new KitId("nodebuff");
        MatchId matchId = new MatchId(UUID.nameUUIDFromBytes("rollback-match".getBytes(StandardCharsets.UTF_8)));
        Instant completedAt = Instant.ofEpochMilli(12_000L);
        MatchSettlement settlement = new MatchSettlement(
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
                        31,
                        completedAt),
                List.of(
                        new PlayerKitStatDelta(winnerId, kitId, 1, 1, 0, 0, 0, 0, completedAt),
                        new PlayerKitStatDelta(loserId, kitId, 1, 0, 1, 0, 0, 0, completedAt)),
                List.of(
                        new PlayerRating(winnerId, kitId, 1016, 1, 0, completedAt),
                        new PlayerRating(loserId, kitId, 984, 0, 1, completedAt)));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            installFailingRatingTrigger(storage.databasePath());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.matchSettlements().record(settlement));

            assertTrue(failure.getMessage().contains(matchId.value().toString()));
            assertEquals(0L, countRows(storage.databasePath(), "match_history"));
            assertEquals(0L, countRows(storage.databasePath(), "player_kit_stats"));
            assertEquals(0L, countRows(storage.databasePath(), "player_ratings"));
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
    void staleDuplicateProfileUpsertsDoNotRegressLastSeenOrOverwriteTheNewerName() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("stale-profile-upsert");
        Instant originalFirstSeenAt = Instant.ofEpochMilli(1_000L);
        PlayerProfile currentProfile = new PlayerProfile(
                playerId, Optional.of("CurrentName"), originalFirstSeenAt, Instant.ofEpochMilli(5_000L));
        PlayerProfile staleProfile = new PlayerProfile(
                playerId, Optional.of("StaleName"), Instant.ofEpochMilli(500L), Instant.ofEpochMilli(4_000L));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(currentProfile);
            storage.playerProfiles().upsert(staleProfile);

            PlayerProfile storedProfile = storage.playerProfiles().find(playerId).orElseThrow();
            assertEquals(Optional.of("CurrentName"), storedProfile.lastKnownName());
            assertEquals(originalFirstSeenAt, storedProfile.firstSeenAt());
            assertEquals(currentProfile.lastSeenAt(), storedProfile.lastSeenAt());
            assertEquals(1L, countRows(storage.databasePath(), "player_profiles"));
        }
    }

    @Test
    void playerRecordImportRollbackRestoresEarlierProfileAndRatingWritesWhenLaterStatsInsertFails() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("transfer-rollback-player");
        PlayerId opponentId = player("transfer-rollback-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile existingProfile = new PlayerProfile(
                playerId,
                Optional.of("OldName"),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(2_000L));
        PlayerRating existingRating =
                new PlayerRating(playerId, kitId, 900, 1, 8, Instant.ofEpochMilli(2_500L));
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
                                "transfer-rollback-history",
                                playerId,
                                opponentId,
                                kitId,
                                Instant.parse("2026-05-05T10:00:00Z"))
                        .history()));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(existingProfile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(existingRating));
            installFailingPlayerStatsTrigger(storage.databasePath());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.playerRecordTransfers().importBundle(importedBundle));

            assertTrue(failure.getMessage().contains(playerId.value().toString()));
            assertEquals(existingProfile, storage.playerProfiles().find(playerId).orElseThrow());
            assertEquals(existingRating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertTrue(storage.matchSettlements().findStats(playerId, kitId).isEmpty());
            assertTrue(storage.matchSettlements().findAllHistory(playerId).isEmpty());
        }
    }

    @Test
    void playerRecordImportDoesNotRegressANewerStoredProfileWhenTheBundleProfileIsOlder() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("transfer-stale-profile-player");
        PlayerId opponentId = player("transfer-stale-profile-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile currentProfile = new PlayerProfile(
                playerId,
                Optional.of("CurrentName"),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(5_000L));
        PlayerRecordBundle importedBundle = new PlayerRecordBundle(
                new PlayerProfile(
                        playerId,
                        Optional.of("StaleImportedName"),
                        Instant.ofEpochMilli(500L),
                        Instant.ofEpochMilli(4_000L)),
                List.of(new PlayerRating(playerId, kitId, 1240, 9, 4, Instant.ofEpochMilli(4_000L))),
                List.of(new PlayerKitStats(
                        playerId,
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        Instant.ofEpochMilli(4_000L))),
                List.of(settlement(
                                "transfer-stale-profile-history",
                                playerId,
                                opponentId,
                                kitId,
                                Instant.parse("2026-05-05T10:00:00Z"))
                        .history()));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(currentProfile);

            storage.playerRecordTransfers().importBundle(importedBundle);

            PlayerProfile storedProfile = storage.playerProfiles().find(playerId).orElseThrow();
            assertEquals(Optional.of("CurrentName"), storedProfile.lastKnownName());
            assertEquals(currentProfile.firstSeenAt(), storedProfile.firstSeenAt());
            assertEquals(currentProfile.lastSeenAt(), storedProfile.lastSeenAt());
            assertEquals(1240, storage.playerRatings().find(playerId, kitId).orElseThrow().rating());
            assertEquals(13, storage.matchSettlements()
                    .findStats(playerId, kitId)
                    .orElseThrow()
                    .matchesPlayed());
            assertEquals(1, storage.matchSettlements().findAllHistory(playerId).size());
        }
    }

    @Test
    void playerRecordImportFailsClosedWhenCurrentSeasonHistoryContainsRowsMissingFromBundle() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("transfer-history-reconcile-player");
        PlayerId staleOpponentId = player("transfer-history-reconcile-stale-opponent");
        PlayerId unrelatedPlayerOne = player("transfer-history-reconcile-other-one");
        PlayerId unrelatedPlayerTwo = player("transfer-history-reconcile-other-two");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile existingProfile = new PlayerProfile(
                playerId,
                Optional.of("OldTransferReconcile"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-02T00:00:00Z"));
        PlayerRating existingRating =
                new PlayerRating(playerId, kitId, 910, 2, 7, Instant.parse("2026-05-02T00:00:01Z"));
        PlayerKitStats existingStats = new PlayerKitStats(
                playerId,
                kitId,
                9,
                2,
                7,
                0,
                0,
                0,
                Instant.parse("2026-05-02T00:00:02Z"));
        PlayerProfile importedProfile = new PlayerProfile(
                playerId,
                Optional.of("TransferReconcile"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"));
        MatchHistoryEntry staleHistory = settlement(
                        "transfer-history-reconcile-stale",
                        playerId,
                        staleOpponentId,
                        kitId,
                        Instant.parse("2026-05-04T09:00:00Z"))
                .history();
        MatchHistoryEntry unrelatedHistory = settlement(
                        "transfer-history-reconcile-unrelated",
                        unrelatedPlayerOne,
                        unrelatedPlayerTwo,
                        kitId,
                        Instant.parse("2026-05-04T08:00:00Z"))
                .history();
        MatchHistoryEntry inactiveSeasonHistory = settlement(
                        "transfer-history-reconcile-inactive",
                        playerId,
                        player("transfer-history-reconcile-inactive-opponent"),
                        kitId,
                        Instant.parse("2026-05-03T08:00:00Z"))
                .history();
        MatchHistoryEntry importedOlder = settlement(
                        "transfer-history-reconcile-import-older",
                        playerId,
                        player("transfer-history-reconcile-import-opponent-one"),
                        kitId,
                        Instant.parse("2026-05-05T10:00:00Z"))
                .history();
        MatchHistoryEntry importedNewer = settlement(
                        "transfer-history-reconcile-import-newer",
                        player("transfer-history-reconcile-import-opponent-two"),
                        playerId,
                        kitId,
                        Instant.parse("2026-05-05T11:00:00Z"))
                .history();
        PlayerRecordBundle importedBundle = new PlayerRecordBundle(
                importedProfile,
                List.of(new PlayerRating(playerId, kitId, 1240, 9, 4, Instant.parse("2026-05-05T11:00:00Z"))),
                List.of(new PlayerKitStats(
                        playerId,
                        kitId,
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        Instant.parse("2026-05-05T11:00:00Z"))),
                List.of(importedOlder, importedNewer));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(existingProfile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(existingRating));
            storage.matchSettlements().importPlayerRecords(playerId, List.of(existingStats), List.of(staleHistory));
            storage.matchSettlements().importPlayerRecords(unrelatedPlayerOne, List.of(), List.of(unrelatedHistory));
            insertSeason(storage.databasePath(), "inactive", false);
            seedMatchHistory(storage.databasePath(), "inactive", inactiveSeasonHistory);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.playerRecordTransfers().importBundle(importedBundle));

            assertEquals(
                    "Player record import history conflict for "
                            + playerId.value()
                            + ": existing current-season history contains rows not present in the imported bundle.",
                    failure.getMessage());
            assertEquals(existingProfile, storage.playerProfiles().find(playerId).orElseThrow());
            assertEquals(existingRating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertEquals(existingStats, storage.matchSettlements().findStats(playerId, kitId).orElseThrow());
            assertEquals(
                    List.of(staleHistory.matchId()),
                    storage.matchSettlements().findAllHistory(playerId).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(
                    List.of(staleHistory.matchId()),
                    storage.matchSettlements().findRecentHistory(playerId, 10, 0).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(
                    List.of(staleHistory.matchId()),
                    storage.matchSettlements().findAllHistory(staleOpponentId).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(Optional.of(staleHistory), storage.matchSettlements().findHistory(staleHistory.matchId()));
            assertEquals(Optional.of(unrelatedHistory), storage.matchSettlements().findHistory(unrelatedHistory.matchId()));
            assertEquals(2L, countRowsWhereSeason(storage.databasePath(), "match_history", "default"));
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "match_history", "inactive"));
        }
    }

    @Test
    void playerRecordImportFailsClosedWhenExistingMatchIdRowDiffersFromBundle() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("transfer-history-conflict-player");
        PlayerId opponentId = player("transfer-history-conflict-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile existingProfile = new PlayerProfile(
                playerId,
                Optional.of("ExistingConflict"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-02T00:00:00Z"));
        PlayerRating existingRating =
                new PlayerRating(playerId, kitId, 905, 1, 8, Instant.parse("2026-05-02T00:00:01Z"));
        PlayerKitStats existingStats = new PlayerKitStats(
                playerId,
                kitId,
                9,
                1,
                8,
                0,
                0,
                0,
                Instant.parse("2026-05-02T00:00:02Z"));
        MatchHistoryEntry importedHistory = settlement(
                        "transfer-history-conflict-imported",
                        playerId,
                        opponentId,
                        kitId,
                        Instant.parse("2026-05-05T10:00:00Z"))
                .history();
        MatchHistoryEntry conflictingStoredHistory = new MatchHistoryEntry(
                importedHistory.matchId(),
                playerId,
                opponentId,
                new ArenaId("arena-two"),
                kitId,
                MatchOrigin.QUEUE_UNRANKED,
                MatchEndReason.FORFEIT,
                Optional.of(playerId),
                Optional.of(opponentId),
                125,
                Instant.parse("2026-05-04T09:00:00Z"));
        PlayerRecordBundle importedBundle = new PlayerRecordBundle(
                new PlayerProfile(
                        playerId,
                        Optional.of("ImportedConflict"),
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-06T00:00:00Z")),
                List.of(new PlayerRating(playerId, kitId, 1240, 9, 4, Instant.parse("2026-05-05T11:00:00Z"))),
                List.of(new PlayerKitStats(
                        playerId,
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        Instant.parse("2026-05-05T11:00:00Z"))),
                List.of(importedHistory));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(existingProfile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(existingRating));
            storage.matchSettlements().importPlayerRecords(playerId, List.of(existingStats), List.of(conflictingStoredHistory));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> storage.playerRecordTransfers().importBundle(importedBundle));

            assertEquals(
                    "Player record import history conflict for "
                            + playerId.value()
                            + ": existing match-id "
                            + importedHistory.matchId().value()
                            + " differs from the imported history entry.",
                    failure.getMessage());
            assertEquals(existingProfile, storage.playerProfiles().find(playerId).orElseThrow());
            assertEquals(existingRating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertEquals(existingStats, storage.matchSettlements().findStats(playerId, kitId).orElseThrow());
            assertEquals(
                    Optional.of(conflictingStoredHistory),
                    storage.matchSettlements().findHistory(importedHistory.matchId()));
            assertEquals(
                    List.of(conflictingStoredHistory.matchId()),
                    storage.matchSettlements().findAllHistory(opponentId).stream()
                            .map(MatchHistoryEntry::matchId)
                            .toList());
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "match_history", "default"));
        }
    }

    @Test
    void playerRecordExportUsesOneConnectionAndOneCapturedActiveSeason() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        Path databasePath;
        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            databasePath = storage.databasePath();
        }

        PlayerId playerId = player("transfer-export-player");
        PlayerId opponentId = player("transfer-export-opponent");
        KitId activeKitId = new KitId("nodebuff");
        KitId inactiveKitId = new KitId("sumo");
        PlayerProfile profile = new PlayerProfile(
                playerId,
                Optional.of("TransferExport"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"));
        MatchHistoryEntry activeHistory = settlement(
                        "transfer-export-active-history",
                        playerId,
                        opponentId,
                        activeKitId,
                        Instant.parse("2026-05-06T10:00:00Z"))
                .history();
        MatchHistoryEntry inactiveHistory = settlement(
                        "transfer-export-inactive-history",
                        playerId,
                        opponentId,
                        inactiveKitId,
                        Instant.parse("2026-05-05T10:00:00Z"))
                .history();

        insertSeason(databasePath, "inactive", false);
        seedPlayerProfile(databasePath, profile);
        seedPlayerRating(
                databasePath,
                "default",
                new PlayerRating(playerId, activeKitId, 1216, 8, 3, Instant.parse("2026-05-06T10:00:00Z")));
        seedPlayerRating(
                databasePath,
                "inactive",
                new PlayerRating(playerId, inactiveKitId, 999, 1, 9, Instant.parse("2026-05-05T10:00:00Z")));
        seedPlayerStats(
                databasePath,
                "default",
                new PlayerKitStats(playerId, activeKitId, 11, 8, 3, 1, 0, 0, Instant.parse("2026-05-06T10:00:00Z")));
        seedPlayerStats(
                databasePath,
                "inactive",
                new PlayerKitStats(playerId, inactiveKitId, 10, 1, 9, 0, 0, 0, Instant.parse("2026-05-05T10:00:00Z")));
        seedMatchHistory(databasePath, "default", activeHistory);
        seedMatchHistory(databasePath, "inactive", inactiveHistory);

        try (CountingJdbcStorageRuntime runtime = openCountingRuntime(databasePath)) {
            PlayerRecordBundle bundle = runtime.playerRecordTransferRepository().exportBundle(playerId);

            assertEquals(profile, bundle.profile());
            assertEquals(List.of(new PlayerRating(
                            playerId,
                            activeKitId,
                            1216,
                            8,
                            3,
                            Instant.parse("2026-05-06T10:00:00Z"))),
                    bundle.ratings());
            assertEquals(List.of(new PlayerKitStats(
                            playerId,
                            activeKitId,
                            11,
                            8,
                            3,
                            1,
                            0,
                            0,
                            Instant.parse("2026-05-06T10:00:00Z"))),
                    bundle.stats());
            assertEquals(List.of(activeHistory), bundle.history());
            assertEquals(1, runtime.dataSource().borrowCount());
            assertEquals(1, runtime.dataSource().activeSeasonSelectCount());
        }
    }

    @Test
    void playerRecordImportCanCopyHistoryIntoANewActiveSeason() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("transfer-season-copy-player");
        PlayerId opponentId = player("transfer-season-copy-opponent");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile profile = new PlayerProfile(
                playerId,
                Optional.of("SeasonCopy"),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"));
        PlayerRating rating = new PlayerRating(playerId, kitId, 1250, 8, 2, Instant.parse("2026-05-06T00:00:00Z"));
        MatchSettlement settlement = settlement(
                "transfer-season-copy-history",
                playerId,
                opponentId,
                kitId,
                Instant.parse("2026-05-06T12:00:00Z"));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(profile);
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(rating));
            storage.matchSettlements().record(settlement);
            PlayerRecordBundle bundle = storage.playerRecordTransfers().exportBundle(playerId);

            insertSeason(storage.databasePath(), "beta", true);
            assertEquals("beta", activeSeasonId(storage.databasePath()));

            storage.playerRecordTransfers().importBundle(bundle);

            assertEquals(rating, storage.playerRatings().find(playerId, kitId).orElseThrow());
            assertEquals(1L, storage.matchSettlements().findStats(playerId, kitId).orElseThrow().wins());
            assertEquals(
                    Optional.of(settlement.history()),
                    storage.matchSettlements().findHistory(settlement.history().matchId()));
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "match_history", "default"));
            assertEquals(1L, countRowsWhereSeason(storage.databasePath(), "match_history", "beta"));
        }
    }

    @Test
    void runtimeRecoveryRepositoryPersistsAndReloadsRuntimeState() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId sessionPlayer = player("runtime-session-player");
        PlayerId pendingPlayer = player("runtime-pending-player");
        PlayerId queuePlayer = player("runtime-queue-player");
        PlayerId first = player("runtime-match-first");
        PlayerId second = player("runtime-match-second");
        PlayerId spectator = player("runtime-match-spectator");
        PlayerSafetySnapshot sessionSnapshot = snapshot("session");
        PlayerSafetySnapshot pendingSnapshot = snapshot("pending");
        PlayerSession session = new PlayerSession(sessionPlayer, PlayerContext.MATCH, sessionSnapshot);
        PendingRestoration pending =
                new PendingRestoration(pendingPlayer, pendingSnapshot, TransitionReason.QUIT);
        QueueTicket ticket = new QueueTicket(
                new QueueTicketId(UUID.nameUUIDFromBytes("runtime-ticket".getBytes(StandardCharsets.UTF_8))),
                queuePlayer,
                new QueueKey(QueueMode.RANKED, new KitId("nodebuff")),
                42L,
                1210,
                QueueTicketState.PAIRING);
        Match activeMatch = activeMatch("runtime-active-match", first, second, Set.of(spectator));

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            RuntimeRecoveryRepository recovery = storage.runtimeRecovery();

            recovery.savePlayerSession(session);
            recovery.savePendingRestoration(pending);
            recovery.saveQueueTicket(ticket, Instant.parse("2026-05-07T10:00:00Z"));
            recovery.saveMatch(activeMatch);

            assertEquals(List.of(session), recovery.playerSessions());
            assertEquals(List.of(pending), recovery.pendingRestorations());
            assertEquals(List.of(ticket), recovery.queueTickets());
            assertEquals(Optional.of(ticket), recovery.queueTicket(queuePlayer));
            Match loadedMatch = recovery.matches().getFirst();
            assertEquals(activeMatch.id(), loadedMatch.id());
            assertEquals(activeMatch.participants(), loadedMatch.participants());
            assertEquals(activeMatch.ruleset(), loadedMatch.ruleset());
            assertEquals(activeMatch.state(), loadedMatch.state());
            assertEquals(activeMatch.activeTicksElapsed(), loadedMatch.activeTicksElapsed());
            assertTrue(loadedMatch.spectators().isEmpty(), "runtime match recovery intentionally drops spectators");

            recovery.deletePlayerSession(sessionPlayer);
            recovery.deletePendingRestoration(pendingPlayer);
            recovery.deleteQueueTicketByPlayer(queuePlayer);
            recovery.deleteMatch(activeMatch.id());

            assertTrue(recovery.playerSessions().isEmpty());
            assertTrue(recovery.pendingRestorations().isEmpty());
            assertTrue(recovery.queueTickets().isEmpty());
            assertTrue(recovery.matches().isEmpty());
        }
    }

    @Test
    void auditAndSeasonRepositoriesPersistOperationalState() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        AuditEntry older = new AuditEntry(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-05-07T10:00:00Z"),
                "console",
                "registry.reload",
                "arenas=1, kits=1");
        AuditEntry newer = new AuditEntry(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Instant.parse("2026-05-07T11:00:00Z"),
                "console",
                "season.activate",
                "season=beta");

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.audit().append(older);
            storage.audit().append(newer);
            storage.seasons().create(new SeasonId("beta"), Instant.parse("2026-05-07T10:30:00Z"));
            storage.seasons().activate(new SeasonId("beta"), Instant.parse("2026-05-07T11:00:00Z"));
        }

        try (StorageHandle reopened = openStorage(dataFolder, "storage/revprac.db")) {
            assertEquals(List.of(newer, older), reopened.audit().recent(10));
            assertEquals("beta", reopened.seasons().findActive().orElseThrow().id().value());
            assertEquals(2, reopened.seasons().findAll().size());
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
        StorageConfig storageConfig = new StorageConfig(StorageConfig.SQLITE_BACKEND, sqlitePath, null, 4);
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

    private static long countRowsWhereSeason(Path databasePath, String tableName, String seasonId) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement(
                        "select count(*) from " + tableName + " where season_id = ?")) {
            statement.setString(1, seasonId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String activeSeasonId(Path databasePath) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement(
                        "select season_id from seasons where active = 1")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "expected an active season row");
                return resultSet.getString(1);
            }
        }
    }

    private static void insertSeason(Path databasePath, String seasonId, boolean active) throws Exception {
        try (Connection connection = openSqlite(databasePath)) {
            connection.setAutoCommit(false);
            try (PreparedStatement deactivate = connection.prepareStatement("update seasons set active = 0");
                    PreparedStatement insert = connection.prepareStatement(
                            "insert into seasons (season_id, active, created_at, activated_at) values (?, ?, ?, ?)");
                    PreparedStatement activate = connection.prepareStatement(
                            "update seasons set active = ? where season_id = ?")) {
                if (active) {
                    deactivate.executeUpdate();
                }
                insert.setString(1, seasonId);
                insert.setInt(2, active ? 1 : 0);
                insert.setLong(3, 20_000L);
                if (active) {
                    insert.setLong(4, 20_000L);
                } else {
                    insert.setNull(4, Types.BIGINT);
                }
                insert.executeUpdate();
                if (active) {
                    activate.setInt(1, 1);
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

    private static void installFailingRatingTrigger(Path databasePath) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement(
                        "create trigger fail_player_ratings_insert "
                                + "before insert on player_ratings "
                                + "begin "
                                + "select raise(fail, 'forced player_ratings failure'); "
                                + "end")) {
            statement.executeUpdate();
        }
    }

    private static void installFailingPlayerStatsTrigger(Path databasePath) throws Exception {
        try (Connection connection = openSqlite(databasePath);
                PreparedStatement statement = connection.prepareStatement(
                        "create trigger fail_player_kit_stats_insert "
                                + "before insert on player_kit_stats "
                                + "begin "
                                + "select raise(fail, 'forced player_kit_stats failure'); "
                                + "end")) {
            statement.executeUpdate();
        }
    }

    private static void seedPlayerProfile(Path databasePath, PlayerProfile profile) throws Exception {
        try (Connection connection = openSqlite(databasePath)) {
            JdbcPlayerProfileRepository.upsert(connection, profile);
        }
    }

    private static void seedPlayerRating(Path databasePath, String seasonId, PlayerRating rating) throws Exception {
        try (Connection connection = openSqlite(databasePath)) {
            JdbcPlayerRatingRepository.insertOrReplaceRating(connection, seasonId, rating);
        }
    }

    private static void seedPlayerStats(Path databasePath, String seasonId, PlayerKitStats stats) throws Exception {
        try (Connection connection = openSqlite(databasePath)) {
            JdbcMatchSettlementRepository.insertOrReplaceStats(connection, seasonId, stats);
        }
    }

    private static void seedMatchHistory(Path databasePath, String seasonId, MatchHistoryEntry history) throws Exception {
        try (Connection connection = openSqlite(databasePath)) {
            JdbcMatchSettlementRepository.insertHistory(connection, seasonId, history);
        }
    }

    private static CountingJdbcStorageRuntime openCountingRuntime(Path databasePath) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("revprac-test-counting");
        config.setJdbcUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionInitSql("PRAGMA busy_timeout = 5000");
        CountingHikariDataSource dataSource = new CountingHikariDataSource(config);
        return new CountingJdbcStorageRuntime(new JdbcStorageRuntime(dataSource), dataSource);
    }

    private static void seedLegacyV2Database(
            Path databasePath,
            PlayerId playerId,
            PlayerId opponentId,
            KitId kitId,
            MatchId matchId,
            Instant updatedAt,
            Instant completedAt)
            throws Exception {
        Files.createDirectories(databasePath.getParent());
        Flyway.configure()
                .dataSource("jdbc:sqlite:" + databasePath.toAbsolutePath(), "", "")
                .locations("classpath:db/migration/sqlite")
                .target("2")
                .load()
                .migrate();

        try (Connection connection = openSqlite(databasePath);
                PreparedStatement insertProfile = connection.prepareStatement(
                        "insert into player_profiles (player_id, last_known_name, first_seen_at, last_seen_at) "
                                + "values (?, ?, ?, ?)");
                PreparedStatement insertRating = connection.prepareStatement(
                        "insert into player_ratings (player_id, kit_id, rating, wins, losses, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?)");
                PreparedStatement insertHistory = connection.prepareStatement(
                        "insert into match_history (match_id, player_one_id, player_two_id, arena_id, kit_id, "
                                + "match_origin, end_reason, winner_id, loser_id, active_ticks, completed_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement insertStats = connection.prepareStatement(
                        "insert into player_kit_stats (player_id, kit_id, matches_played, wins, losses, forfeits, "
                                + "timeouts, shutdowns, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insertProfile.setString(1, playerId.value().toString());
            insertProfile.setString(2, "LegacyPlayer");
            insertProfile.setLong(3, 10_000L);
            insertProfile.setLong(4, 11_000L);
            insertProfile.executeUpdate();

            insertRating.setString(1, playerId.value().toString());
            insertRating.setString(2, kitId.value());
            insertRating.setInt(3, 1188);
            insertRating.setInt(4, 3);
            insertRating.setInt(5, 1);
            insertRating.setLong(6, updatedAt.toEpochMilli());
            insertRating.executeUpdate();

            insertHistory.setString(1, matchId.value().toString());
            insertHistory.setString(2, playerId.value().toString());
            insertHistory.setString(3, opponentId.value().toString());
            insertHistory.setString(4, "arena-ranked");
            insertHistory.setString(5, kitId.value());
            insertHistory.setString(6, MatchOrigin.QUEUE_RANKED.name());
            insertHistory.setString(7, MatchEndReason.WIN.name());
            insertHistory.setString(8, playerId.value().toString());
            insertHistory.setString(9, opponentId.value().toString());
            insertHistory.setInt(10, 42);
            insertHistory.setLong(11, completedAt.toEpochMilli());
            insertHistory.executeUpdate();

            insertStats.setString(1, playerId.value().toString());
            insertStats.setString(2, kitId.value());
            insertStats.setLong(3, 1L);
            insertStats.setLong(4, 1L);
            insertStats.setLong(5, 0L);
            insertStats.setLong(6, 0L);
            insertStats.setLong(7, 0L);
            insertStats.setLong(8, 0L);
            insertStats.setLong(9, completedAt.toEpochMilli());
            insertStats.executeUpdate();

            insertStats.setString(1, opponentId.value().toString());
            insertStats.setString(2, kitId.value());
            insertStats.setLong(3, 1L);
            insertStats.setLong(4, 0L);
            insertStats.setLong(5, 1L);
            insertStats.setLong(6, 0L);
            insertStats.setLong(7, 0L);
            insertStats.setLong(8, 0L);
            insertStats.setLong(9, completedAt.toEpochMilli());
            insertStats.executeUpdate();
        }
    }

    @Test
    void playerLookupAndTransferQueriesAreAvailableForCurrentSeason() throws Exception {
        Path dataFolder = tempDir.resolve("plugin-data");
        PlayerId playerId = player("records-player");
        PlayerId opponentId = player("records-opponent");
        KitId firstKit = new KitId("boxing");
        KitId secondKit = new KitId("nodebuff");

        try (StorageHandle storage = openStorage(dataFolder, "storage/revprac.db")) {
            storage.playerProfiles().upsert(new PlayerProfile(
                    playerId,
                    Optional.of("LookupName"),
                    Instant.ofEpochMilli(1_000L),
                    Instant.ofEpochMilli(2_000L)));
            storage.playerProfiles().upsert(new PlayerProfile(
                    opponentId,
                    Optional.of("lookupname"),
                    Instant.ofEpochMilli(1_500L),
                    Instant.ofEpochMilli(2_500L)));
            storage.playerRatings().replaceAllForPlayer(playerId, List.of(
                    new PlayerRating(playerId, secondKit, 1200, 5, 2, Instant.ofEpochMilli(3_000L)),
                    new PlayerRating(playerId, firstKit, 950, 1, 4, Instant.ofEpochMilli(2_500L))));
            storage.matchSettlements().importPlayerRecords(
                    playerId,
                    List.of(
                            new PlayerKitStats(
                                    playerId,
                                    firstKit,
                                    5,
                                    1,
                                    4,
                                    0,
                                    0,
                                    0,
                                    Instant.ofEpochMilli(2_500L)),
                            new PlayerKitStats(
                                    playerId,
                                    secondKit,
                                    7,
                                    5,
                                    2,
                                    1,
                                    0,
                                    0,
                                    Instant.ofEpochMilli(3_000L))),
                    List.of(
                            settlement(
                                            "records-history-one",
                                            playerId,
                                            opponentId,
                                            secondKit,
                                            Instant.parse("2026-05-04T10:00:00Z"))
                                    .history(),
                            settlement(
                                            "records-history-two",
                                            opponentId,
                                            playerId,
                                            firstKit,
                                            Instant.parse("2026-05-04T11:00:00Z"))
                                    .history()));

            assertEquals(2, storage.playerProfiles().findByLastKnownNameIgnoreCase("LOOKUPNAME").size());
            assertEquals(
                    List.of(firstKit, secondKit),
                    storage.playerRatings().findByPlayer(playerId).stream()
                            .map(PlayerRating::kitId)
                            .toList());
            assertEquals(
                    List.of(firstKit, secondKit),
                    storage.matchSettlements().findStatsByPlayer(playerId).stream()
                            .map(PlayerKitStats::kitId)
                            .toList());
            assertEquals(
                    List.of(
                            UUID.nameUUIDFromBytes("records-history-two".getBytes(StandardCharsets.UTF_8)),
                            UUID.nameUUIDFromBytes("records-history-one".getBytes(StandardCharsets.UTF_8))),
                    storage.matchSettlements().findAllHistory(playerId).stream()
                            .map(entry -> entry.matchId().value())
                            .toList());
        }
    }

    private static Connection openSqlite(Path databasePath) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static PlayerSafetySnapshot snapshot(String seed) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:overworld", 5.0d, 65.0d, -10.0d, 45.0f, 10.0f),
                new InventorySnapshot(
                        List.of(seed + "-sword"),
                        List.of(seed + "-helmet"),
                        List.of(seed + "-trinket"),
                        List.of(seed + "-pearl"),
                        null,
                        1),
                new PlayerStatusSnapshot(
                        "SURVIVAL",
                        18.5d,
                        19,
                        3.5f,
                        0.5f,
                        7,
                        true,
                        false,
                        List.of(new PotionEffectSnapshot("minecraft:speed", 200, 1, false, true, true))));
    }

    private static Match activeMatch(String seed, PlayerId first, PlayerId second, Set<PlayerId> spectators) {
        return new Match(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))),
                new MatchParticipants(first, second),
                new ArenaId("arena-runtime"),
                new KitId("nodebuff"),
                MatchOrigin.QUEUE_RANKED,
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes(StandardCharsets.UTF_8))),
                new MatchRuleset(5, 240, true),
                MatchState.ACTIVE,
                0,
                18,
                spectators,
                Optional.empty(),
                Optional.empty());
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
                        new ArenaId("arena-history"),
                        kitId,
                        MatchOrigin.DIRECT_DUEL,
                        MatchEndReason.WIN,
                        Optional.of(winnerId),
                        Optional.of(loserId),
                        32,
                        completedAt),
                List.of(
                        new PlayerKitStatDelta(winnerId, kitId, 1, 1, 0, 0, 0, 0, completedAt),
                        new PlayerKitStatDelta(loserId, kitId, 1, 0, 1, 0, 0, 0, completedAt)),
                List.of());
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

        private MatchSettlementRepository matchSettlements() throws Exception {
            Method method = runtime.getClass().getMethod("matchSettlementRepository");
            return (MatchSettlementRepository) invoke(method, runtime);
        }

        private PlayerRecordTransferRepository playerRecordTransfers() throws Exception {
            Method method = runtime.getClass().getMethod("playerRecordTransferRepository");
            return (PlayerRecordTransferRepository) invoke(method, runtime);
        }

        private RuntimeRecoveryRepository runtimeRecovery() throws Exception {
            Method method = runtime.getClass().getMethod("runtimeRecoveryRepository");
            return (RuntimeRecoveryRepository) invoke(method, runtime);
        }

        private AuditRepository audit() throws Exception {
            Method method = runtime.getClass().getMethod("auditRepository");
            return (AuditRepository) invoke(method, runtime);
        }

        private SeasonRepository seasons() throws Exception {
            Method method = runtime.getClass().getMethod("seasonRepository");
            return (SeasonRepository) invoke(method, runtime);
        }

        @Override
        public void close() throws Exception {
            Method method = runtime.getClass().getMethod("close");
            invoke(method, runtime);
        }
    }

    private record CountingJdbcStorageRuntime(JdbcStorageRuntime runtime, CountingHikariDataSource dataSource)
            implements AutoCloseable {

        private PlayerRecordTransferRepository playerRecordTransferRepository() {
            return runtime.playerRecordTransferRepository();
        }

        @Override
        public void close() {
            runtime.close();
        }
    }

    private static final class CountingHikariDataSource extends HikariDataSource {
        private static final String ACTIVE_SEASON_SQL =
                "select season_id, active, created_at, activated_at from seasons where active = ?";

        private int borrowCount;
        private int activeSeasonSelectCount;

        private CountingHikariDataSource(HikariConfig configuration) {
            super(configuration);
        }

        @Override
        public Connection getConnection() throws SQLException {
            borrowCount++;
            return wrap(super.getConnection());
        }

        private int borrowCount() {
            return borrowCount;
        }

        private int activeSeasonSelectCount() {
            return activeSeasonSelectCount;
        }

        private Connection wrap(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())
                                && args != null
                                && args.length > 0
                                && ACTIVE_SEASON_SQL.equals(args[0])) {
                            activeSeasonSelectCount++;
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }
}
