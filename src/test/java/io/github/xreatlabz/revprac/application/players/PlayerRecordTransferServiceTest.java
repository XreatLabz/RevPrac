package io.github.xreatlabz.revprac.application.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerRecordTransferFiles;
import io.github.xreatlabz.revprac.adapters.storage.CompositePlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PlayerRecordTransferServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportWritesSchemaVersionedBundleToExpectedPath() {
        TestHarness harness = new TestHarness(tempDir);
        PlayerProfile profile = profile("export-player", "Exporter");
        KitId kitId = new KitId("nodebuff");
        harness.profiles.upsert(profile);
        harness.ratings.replaceAllForPlayer(profile.playerId(), List.of(new PlayerRating(
                profile.playerId(),
                kitId,
                1185,
                7,
                3,
                instant("2026-05-04T15:00:00Z"))));
        harness.matchSettlements.importPlayerRecords(
                profile.playerId(),
                List.of(new PlayerKitStats(
                        profile.playerId(),
                        kitId,
                        10,
                        7,
                        3,
                        1,
                        0,
                        0,
                        instant("2026-05-04T15:00:00Z"))),
                List.of(history("export-history", profile.playerId(), playerId("export-opponent"), kitId)));

        String relativePath = harness.service.export(profile.playerId());

        assertEquals("exports/player-records/" + profile.playerId().value() + ".yml", relativePath);
        Path exportedFile = tempDir.resolve(relativePath);
        assertTrue(Files.isRegularFile(exportedFile));

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(exportedFile.toFile());
        assertEquals(1, yaml.getInt("schema-version"));
        assertEquals(profile.playerId().value().toString(), yaml.getString("profile.player-id"));
        assertEquals("Exporter", yaml.getString("profile.last-known-name"));
        assertEquals(1, yaml.getMapList("ratings").size());
        assertEquals("nodebuff", yaml.getMapList("ratings").getFirst().get("kit-id"));
        assertEquals(1, yaml.getMapList("history").size());
    }

    @Test
    void importRejectsNonSimpleFilenamesAndBadSchemaVersions() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        Path importsDir = tempDir.resolve("imports/player-records");
        Files.createDirectories(importsDir);
        YamlConfiguration badSchema = new YamlConfiguration();
        badSchema.set("schema-version", 2);
        badSchema.set("profile", Map.of());
        badSchema.set("ratings", List.of());
        badSchema.set("stats", List.of());
        badSchema.set("history", List.of());
        badSchema.save(importsDir.resolve("bad.yml").toFile());

        IllegalArgumentException filenameFailure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("../bad.yml"));
        IllegalArgumentException schemaFailure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("bad.yml"));

        assertEquals("Import file must be a simple .yml filename.", filenameFailure.getMessage());
        assertEquals("Import file is invalid: schema-version must be 1.", schemaFailure.getMessage());
    }

    @Test
    void importRejectsOverRangeSchemaVersionThatWouldWrapIntValue() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        Path importsDir = tempDir.resolve("imports/player-records");
        Files.createDirectories(importsDir);
        YamlConfiguration badSchema = new YamlConfiguration();
        badSchema.set("schema-version", 4294967297L);
        badSchema.set("profile.player-id", playerId("wrapped-schema-player").value().toString());
        badSchema.set("profile.last-known-name", "WrappedSchema");
        badSchema.set("profile.first-seen-at", 1714521600000L);
        badSchema.set("profile.last-seen-at", 1714867200000L);
        badSchema.set("ratings", List.of());
        badSchema.set("stats", List.of());
        badSchema.set("history", List.of());
        badSchema.save(importsDir.resolve("wrapped-schema.yml").toFile());

        IllegalArgumentException schemaFailure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("wrapped-schema.yml"));

        assertEquals("Import file is invalid: schema-version must be 1.", schemaFailure.getMessage());
    }

    @Test
    void importRejectsDuplicateRatingKitIds() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        PlayerProfile profile = profile("duplicate-rating-player", "DuplicateRating");
        KitId kitId = new KitId("nodebuff");
        writeImportBundle(
                tempDir.resolve("imports/player-records/duplicate-ratings.yml"),
                profile,
                List.of(
                        ratingMap(kitId, 1200, 7, 3, instant("2026-05-05T10:00:00Z")),
                        ratingMap(kitId, 1250, 8, 3, instant("2026-05-05T11:00:00Z"))),
                List.of(statsMap(kitId, 10, 7, 3, 0, 0, 0, instant("2026-05-05T10:00:00Z"))),
                List.of(historyMap(history("duplicate-rating-history", profile.playerId(), playerId("duplicate-rating-opponent"), kitId))));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("duplicate-ratings.yml"));

        assertEquals("Import file is invalid: ratings contains duplicate kit-id: nodebuff.", failure.getMessage());
    }

    @Test
    void importRejectsDuplicateStatsKitIds() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        PlayerProfile profile = profile("duplicate-stats-player", "DuplicateStats");
        KitId kitId = new KitId("nodebuff");
        writeImportBundle(
                tempDir.resolve("imports/player-records/duplicate-stats.yml"),
                profile,
                List.of(ratingMap(kitId, 1200, 7, 3, instant("2026-05-05T10:00:00Z"))),
                List.of(
                        statsMap(kitId, 10, 7, 3, 0, 0, 0, instant("2026-05-05T10:00:00Z")),
                        statsMap(kitId, 11, 8, 3, 0, 0, 0, instant("2026-05-05T11:00:00Z"))),
                List.of(historyMap(history("duplicate-stats-history", profile.playerId(), playerId("duplicate-stats-opponent"), kitId))));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("duplicate-stats.yml"));

        assertEquals("Import file is invalid: stats contains duplicate kit-id: nodebuff.", failure.getMessage());
    }

    @Test
    void importRejectsDuplicateHistoryMatchIds() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        PlayerProfile profile = profile("duplicate-history-player", "DuplicateHistory");
        KitId kitId = new KitId("nodebuff");
        MatchHistoryEntry duplicateHistory =
                history("duplicate-history-match", profile.playerId(), playerId("duplicate-history-opponent"), kitId);
        writeImportBundle(
                tempDir.resolve("imports/player-records/duplicate-history.yml"),
                profile,
                List.of(ratingMap(kitId, 1200, 7, 3, instant("2026-05-05T10:00:00Z"))),
                List.of(statsMap(kitId, 10, 7, 3, 0, 0, 0, instant("2026-05-05T10:00:00Z"))),
                List.of(historyMap(duplicateHistory), historyMap(duplicateHistory)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("duplicate-history.yml"));

        assertEquals(
                "Import file is invalid: history contains duplicate match-id: "
                        + duplicateHistory.matchId().value()
                        + ".",
                failure.getMessage());
    }

    @Test
    void importRejectsOverRangeIntegerFields() throws Exception {
        TestHarness harness = new TestHarness(tempDir);
        Path importFile = tempDir.resolve("imports/player-records/overflow.yml");
        Files.createDirectories(importFile.getParent());
        Files.writeString(
                importFile,
                """
                schema-version: 1
                profile:
                  player-id: %s
                  last-known-name: Overflow
                  first-seen-at: 9223372036854775808
                  last-seen-at: 1714867200000
                ratings: []
                stats: []
                history: []
                """
                        .formatted(playerId("overflow-player").value()));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.importFromFile("overflow.yml"));

        assertEquals("Import file is invalid: profile.first-seen-at must be an integer.", failure.getMessage());
    }

    @Test
    void importReplacesRatingsAndStatsAndStaysIdempotentAcrossRepeats() throws Exception {
        TestHarness source = new TestHarness(tempDir.resolve("source"));
        PlayerProfile profile = profile("import-player", "Importer");
        KitId kitId = new KitId("nodebuff");
        source.profiles.upsert(profile);
        source.ratings.replaceAllForPlayer(profile.playerId(), List.of(new PlayerRating(
                profile.playerId(),
                kitId,
                1240,
                9,
                4,
                instant("2026-05-05T10:00:00Z"))));
        source.matchSettlements.importPlayerRecords(
                profile.playerId(),
                List.of(new PlayerKitStats(
                        profile.playerId(),
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        instant("2026-05-05T10:00:00Z"))),
                List.of(history("import-history", profile.playerId(), playerId("import-opponent"), kitId)));
        String exportPath = source.service.export(profile.playerId());

        TestHarness target = new TestHarness(tempDir.resolve("target"));
        target.profiles.upsert(new PlayerProfile(
                profile.playerId(),
                Optional.of("OldName"),
                instant("2026-05-01T00:00:00Z"),
                instant("2026-05-01T00:00:01Z")));
        target.ratings.replaceAllForPlayer(profile.playerId(), List.of(new PlayerRating(
                profile.playerId(),
                kitId,
                900,
                1,
                8,
                instant("2026-05-01T00:00:02Z"))));
        target.matchSettlements.importPlayerRecords(
                profile.playerId(),
                List.of(new PlayerKitStats(
                        profile.playerId(),
                        kitId,
                        9,
                        1,
                        8,
                        0,
                        0,
                        0,
                        instant("2026-05-01T00:00:02Z"))),
                List.of());

        Path importDir = tempDir.resolve("target/imports/player-records");
        Files.createDirectories(importDir);
        Files.copy(
                tempDir.resolve("source").resolve(exportPath),
                importDir.resolve("bundle.yml"));

        PlayerRecordBundle firstImport = target.service.importFromFile("bundle.yml");
        PlayerRecordBundle secondImport = target.service.importFromFile("bundle.yml");

        assertEquals(profile, firstImport.profile());
        assertEquals(profile, secondImport.profile());
        assertEquals(Optional.of(profile), target.profiles.find(profile.playerId()));
        assertEquals(1, target.ratings.findByPlayer(profile.playerId()).size());
        assertEquals(1240, target.ratings.find(profile.playerId(), kitId).orElseThrow().rating());
        assertEquals(13, target.matchSettlements.findStats(profile.playerId(), kitId).orElseThrow().matchesPlayed());
        assertEquals(1, target.matchSettlements.findAllHistory(profile.playerId()).size());
        assertEquals(1, target.matchSettlements.findRecentHistory(profile.playerId(), 10, 0).size());
    }

    @Test
    void importFailsClosedWhenCurrentSeasonHistoryContainsRowsMissingFromBundle() throws Exception {
        TestHarness source = new TestHarness(tempDir.resolve("source-conflict-missing"));
        PlayerProfile importedProfile = profile("reconcile-player", "Reconcile");
        KitId kitId = new KitId("nodebuff");
        MatchHistoryEntry importedOlder =
                history("reconcile-import-older", importedProfile.playerId(), playerId("reconcile-opponent-one"), kitId);
        MatchHistoryEntry importedNewer = new MatchHistoryEntry(
                new MatchId(UUID.nameUUIDFromBytes("reconcile-import-newer".getBytes())),
                playerId("reconcile-opponent-two"),
                importedProfile.playerId(),
                new ArenaId("arena-one"),
                kitId,
                MatchOrigin.QUEUE_RANKED,
                MatchEndReason.WIN,
                Optional.of(playerId("reconcile-opponent-two")),
                Optional.of(importedProfile.playerId()),
                240,
                instant("2026-05-05T12:00:00Z"));
        source.profiles.upsert(importedProfile);
        source.matchSettlements.importPlayerRecords(
                importedProfile.playerId(),
                List.of(new PlayerKitStats(
                        importedProfile.playerId(),
                        kitId,
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        instant("2026-05-05T12:00:00Z"))),
                List.of(importedOlder, importedNewer));
        String exportPath = source.service.export(importedProfile.playerId());

        TestHarness target = new TestHarness(tempDir.resolve("target-conflict-missing"));
        PlayerProfile existingProfile = new PlayerProfile(
                importedProfile.playerId(),
                Optional.of("ExistingName"),
                instant("2026-05-01T00:00:00Z"),
                instant("2026-05-02T00:00:00Z"));
        PlayerRating existingRating = new PlayerRating(
                importedProfile.playerId(),
                kitId,
                910,
                2,
                7,
                instant("2026-05-02T00:00:01Z"));
        PlayerKitStats existingStats = new PlayerKitStats(
                importedProfile.playerId(),
                kitId,
                9,
                2,
                7,
                0,
                0,
                0,
                instant("2026-05-02T00:00:02Z"));
        MatchHistoryEntry staleHistory =
                history("reconcile-stale", importedProfile.playerId(), playerId("reconcile-stale-opponent"), kitId);
        MatchHistoryEntry unrelatedHistory =
                history("reconcile-unrelated", playerId("reconcile-other-one"), playerId("reconcile-other-two"), kitId);
        target.profiles.upsert(existingProfile);
        target.ratings.replaceAllForPlayer(importedProfile.playerId(), List.of(existingRating));
        target.matchSettlements.importPlayerRecords(importedProfile.playerId(), List.of(existingStats), List.of(staleHistory));
        target.matchSettlements.importPlayerRecords(playerId("reconcile-other-one"), List.of(), List.of(unrelatedHistory));

        Path importDir = tempDir.resolve("target-conflict-missing/imports/player-records");
        Files.createDirectories(importDir);
        Files.copy(
                tempDir.resolve("source-conflict-missing").resolve(exportPath),
                importDir.resolve("bundle.yml"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> target.service.importFromFile("bundle.yml"));

        assertEquals(
                "Player record import history conflict for "
                        + importedProfile.playerId().value()
                        + ": existing current-season history contains rows not present in the imported bundle.",
                failure.getMessage());
        assertEquals(Optional.of(existingProfile), target.profiles.find(importedProfile.playerId()));
        assertEquals(existingRating, target.ratings.find(importedProfile.playerId(), kitId).orElseThrow());
        assertEquals(existingStats, target.matchSettlements.findStats(importedProfile.playerId(), kitId).orElseThrow());
        assertEquals(
                List.of(staleHistory.matchId()),
                target.matchSettlements.findAllHistory(importedProfile.playerId()).stream()
                        .map(MatchHistoryEntry::matchId)
                        .toList());
        assertEquals(
                List.of(staleHistory.matchId()),
                target.matchSettlements.findRecentHistory(importedProfile.playerId(), 10, 0).stream()
                        .map(MatchHistoryEntry::matchId)
                        .toList());
        assertEquals(
                List.of(unrelatedHistory.matchId()),
                target.matchSettlements.findAllHistory(playerId("reconcile-other-one")).stream()
                        .map(MatchHistoryEntry::matchId)
                        .toList());
    }

    @Test
    void importFailsClosedWhenExistingMatchIdRowDiffersFromBundle() throws Exception {
        TestHarness source = new TestHarness(tempDir.resolve("source-conflict-match-id"));
        PlayerProfile importedProfile = profile("conflict-player", "Conflict");
        KitId kitId = new KitId("nodebuff");
        MatchHistoryEntry importedHistory =
                history("conflict-history", importedProfile.playerId(), playerId("conflict-opponent"), kitId);
        source.profiles.upsert(importedProfile);
        source.ratings.replaceAllForPlayer(importedProfile.playerId(), List.of(new PlayerRating(
                importedProfile.playerId(),
                kitId,
                1240,
                9,
                4,
                instant("2026-05-05T10:00:00Z"))));
        source.matchSettlements.importPlayerRecords(
                importedProfile.playerId(),
                List.of(new PlayerKitStats(
                        importedProfile.playerId(),
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        instant("2026-05-05T10:00:00Z"))),
                List.of(importedHistory));
        String exportPath = source.service.export(importedProfile.playerId());

        TestHarness target = new TestHarness(tempDir.resolve("target-conflict-match-id"));
        PlayerProfile existingProfile = new PlayerProfile(
                importedProfile.playerId(),
                Optional.of("ExistingConflictName"),
                instant("2026-05-01T00:00:00Z"),
                instant("2026-05-02T00:00:00Z"));
        PlayerRating existingRating = new PlayerRating(
                importedProfile.playerId(),
                kitId,
                905,
                1,
                8,
                instant("2026-05-02T00:00:01Z"));
        PlayerKitStats existingStats = new PlayerKitStats(
                importedProfile.playerId(),
                kitId,
                9,
                1,
                8,
                0,
                0,
                0,
                instant("2026-05-02T00:00:02Z"));
        MatchHistoryEntry conflictingExistingHistory = new MatchHistoryEntry(
                importedHistory.matchId(),
                importedProfile.playerId(),
                playerId("conflict-opponent"),
                new ArenaId("arena-two"),
                kitId,
                MatchOrigin.QUEUE_UNRANKED,
                MatchEndReason.FORFEIT,
                Optional.of(importedProfile.playerId()),
                Optional.of(playerId("conflict-opponent")),
                150,
                instant("2026-05-04T12:00:00Z"));
        target.profiles.upsert(existingProfile);
        target.ratings.replaceAllForPlayer(importedProfile.playerId(), List.of(existingRating));
        target.matchSettlements.importPlayerRecords(
                importedProfile.playerId(),
                List.of(existingStats),
                List.of(conflictingExistingHistory));

        Path importDir = tempDir.resolve("target-conflict-match-id/imports/player-records");
        Files.createDirectories(importDir);
        Files.copy(
                tempDir.resolve("source-conflict-match-id").resolve(exportPath),
                importDir.resolve("bundle.yml"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> target.service.importFromFile("bundle.yml"));

        assertEquals(
                "Player record import history conflict for "
                        + importedProfile.playerId().value()
                        + ": existing match-id "
                        + importedHistory.matchId().value()
                        + " differs from the imported history entry.",
                failure.getMessage());
        assertEquals(Optional.of(existingProfile), target.profiles.find(importedProfile.playerId()));
        assertEquals(existingRating, target.ratings.find(importedProfile.playerId(), kitId).orElseThrow());
        assertEquals(existingStats, target.matchSettlements.findStats(importedProfile.playerId(), kitId).orElseThrow());
        assertEquals(
                Optional.of(conflictingExistingHistory),
                target.matchSettlements.findHistory(importedHistory.matchId()));
        assertEquals(
                List.of(conflictingExistingHistory.matchId()),
                target.matchSettlements.findAllHistory(importedProfile.playerId()).stream()
                        .map(MatchHistoryEntry::matchId)
                        .toList());
    }

    @Test
    void compositeImportFailsClosedWhenMatchSettlementStepThrowsAfterMutatingState() {
        PlayerId playerId = playerId("composite-rollback-player");
        PlayerId existingOpponentId = playerId("composite-rollback-existing-opponent");
        PlayerId importedOpponentId = playerId("composite-rollback-imported-opponent");
        PlayerId unrelatedPlayerId = playerId("composite-rollback-unrelated-player");
        KitId kitId = new KitId("nodebuff");
        PlayerProfile existingProfile = new PlayerProfile(
                playerId,
                Optional.of("ExistingComposite"),
                instant("2026-05-01T00:00:00Z"),
                instant("2026-05-02T00:00:00Z"));
        PlayerRating existingRating = new PlayerRating(
                playerId,
                kitId,
                915,
                2,
                7,
                instant("2026-05-02T00:00:01Z"));
        PlayerKitStats existingStats = new PlayerKitStats(
                playerId,
                kitId,
                9,
                2,
                7,
                0,
                0,
                0,
                instant("2026-05-02T00:00:02Z"));
        MatchHistoryEntry existingHistory =
                history("composite-rollback-existing-history", playerId, existingOpponentId, kitId);
        MatchHistoryEntry importedHistory =
                history("composite-rollback-imported-history", playerId, importedOpponentId, kitId);
        MatchHistoryEntry unrelatedHistory =
                history("composite-rollback-unrelated-history", unrelatedPlayerId, playerId("composite-rollback-other"), kitId);
        PlayerRecordBundle importedBundle = new PlayerRecordBundle(
                new PlayerProfile(
                        playerId,
                        Optional.of("ImportedComposite"),
                        instant("2026-05-01T00:00:00Z"),
                        instant("2026-05-06T00:00:00Z")),
                List.of(new PlayerRating(
                        playerId,
                        kitId,
                        1240,
                        9,
                        4,
                        instant("2026-05-06T00:00:01Z"))),
                List.of(new PlayerKitStats(
                        playerId,
                        kitId,
                        13,
                        9,
                        4,
                        1,
                        0,
                        0,
                        instant("2026-05-06T00:00:02Z"))),
                List.of(existingHistory, importedHistory));
        MonotonicPlayerProfileRepository profiles = new MonotonicPlayerProfileRepository();
        MapPlayerRatingRepository ratings = new MapPlayerRatingRepository();
        InMemoryMatchSettlementRepository delegate = new InMemoryMatchSettlementRepository();
        MatchSettlementRepository matchSettlements =
                new FailingAfterImportMatchSettlementRepository(delegate, "forced transfer import failure");
        CompositePlayerRecordTransferRepository repository =
                new CompositePlayerRecordTransferRepository(profiles, ratings, matchSettlements);

        profiles.upsert(existingProfile);
        ratings.replaceAllForPlayer(playerId, List.of(existingRating));
        delegate.importPlayerRecords(playerId, List.of(existingStats), List.of(existingHistory));
        delegate.importPlayerRecords(unrelatedPlayerId, List.of(), List.of(unrelatedHistory));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> repository.importBundle(importedBundle));

        assertEquals("forced transfer import failure", failure.getMessage());
        assertEquals(Optional.of(existingProfile), profiles.find(playerId));
        assertEquals(existingRating, ratings.find(playerId, kitId).orElseThrow());
        assertEquals(existingStats, delegate.findStats(playerId, kitId).orElseThrow());
        assertEquals(
                List.of(existingHistory.matchId()),
                delegate.findAllHistory(playerId).stream().map(MatchHistoryEntry::matchId).toList());
        assertEquals(Optional.empty(), delegate.findHistory(importedHistory.matchId()));
        assertEquals(
                List.of(unrelatedHistory.matchId()),
                delegate.findAllHistory(unrelatedPlayerId).stream()
                        .map(MatchHistoryEntry::matchId)
                        .toList());
    }

    private static PlayerProfile profile(String seed, String name) {
        return new PlayerProfile(
                playerId(seed),
                Optional.of(name),
                instant("2026-05-01T00:00:00Z"),
                instant("2026-05-04T00:00:00Z"));
    }

    private static MatchHistoryEntry history(String seed, PlayerId playerId, PlayerId opponentId, KitId kitId) {
        return new MatchHistoryEntry(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                playerId,
                opponentId,
                new ArenaId("arena-one"),
                kitId,
                MatchOrigin.QUEUE_RANKED,
                MatchEndReason.WIN,
                Optional.of(playerId),
                Optional.of(opponentId),
                200,
                instant("2026-05-04T12:00:00Z"));
    }

    private static PlayerId playerId(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static void writeImportBundle(
            Path file,
            PlayerProfile profile,
            List<Map<String, Object>> ratings,
            List<Map<String, Object>> stats,
            List<Map<String, Object>> history)
            throws Exception {
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("profile.player-id", profile.playerId().value().toString());
        yaml.set("profile.last-known-name", profile.lastKnownName().orElse(null));
        yaml.set("profile.first-seen-at", profile.firstSeenAt().toEpochMilli());
        yaml.set("profile.last-seen-at", profile.lastSeenAt().toEpochMilli());
        yaml.set("ratings", ratings);
        yaml.set("stats", stats);
        yaml.set("history", history);
        yaml.save(file.toFile());
    }

    private static Map<String, Object> ratingMap(KitId kitId, int rating, int wins, int losses, Instant updatedAt) {
        return Map.of(
                "kit-id", kitId.value(),
                "rating", rating,
                "wins", wins,
                "losses", losses,
                "updated-at", updatedAt.toEpochMilli());
    }

    private static Map<String, Object> statsMap(
            KitId kitId,
            long matchesPlayed,
            long wins,
            long losses,
            long forfeits,
            long timeouts,
            long shutdowns,
            Instant updatedAt) {
        return Map.of(
                "kit-id", kitId.value(),
                "matches-played", matchesPlayed,
                "wins", wins,
                "losses", losses,
                "forfeits", forfeits,
                "timeouts", timeouts,
                "shutdowns", shutdowns,
                "updated-at", updatedAt.toEpochMilli());
    }

    private static Map<String, Object> historyMap(MatchHistoryEntry historyEntry) {
        return Map.ofEntries(
                Map.entry("match-id", historyEntry.matchId().value().toString()),
                Map.entry("player-one-id", historyEntry.playerOneId().value().toString()),
                Map.entry("player-two-id", historyEntry.playerTwoId().value().toString()),
                Map.entry("arena-id", historyEntry.arenaId().value()),
                Map.entry("kit-id", historyEntry.kitId().value()),
                Map.entry("origin", historyEntry.origin().name()),
                Map.entry("end-reason", historyEntry.endReason().name()),
                Map.entry("winner-id", historyEntry.winnerId().orElseThrow().value().toString()),
                Map.entry("loser-id", historyEntry.loserId().orElseThrow().value().toString()),
                Map.entry("active-ticks", historyEntry.activeTicks()),
                Map.entry("completed-at", historyEntry.completedAt().toEpochMilli()));
    }

    private static final class TestHarness {
        private final MapPlayerProfileRepository profiles = new MapPlayerProfileRepository();
        private final MapPlayerRatingRepository ratings = new MapPlayerRatingRepository();
        private final InMemoryMatchSettlementRepository matchSettlements = new InMemoryMatchSettlementRepository();
        private final PlayerRecordTransferService service;

        private TestHarness(Path dataDirectory) {
            this.service = new PlayerRecordTransferService(
                    new CompositePlayerRecordTransferRepository(profiles, ratings, matchSettlements),
                    new PaperPlayerRecordTransferFiles(dataDirectory));
        }
    }

    private static final class MapPlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new HashMap<>();

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public List<PlayerProfile> findByLastKnownNameIgnoreCase(String lastKnownName) {
            return profiles.values().stream()
                    .filter(profile -> profile.lastKnownName()
                            .map(name -> name.equalsIgnoreCase(lastKnownName))
                            .orElse(false))
                    .toList();
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }

        @Override
        public void restoreExact(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }

        @Override
        public void delete(PlayerId playerId) {
            profiles.remove(playerId);
        }
    }

    private static final class MonotonicPlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new HashMap<>();

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public List<PlayerProfile> findByLastKnownNameIgnoreCase(String lastKnownName) {
            return profiles.values().stream()
                    .filter(profile -> profile.lastKnownName()
                            .map(name -> name.equalsIgnoreCase(lastKnownName))
                            .orElse(false))
                    .toList();
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.compute(profile.playerId(), (playerId, existingProfile) -> {
                if (existingProfile == null) {
                    return profile;
                }
                Instant storedLastSeenAt = existingProfile.lastSeenAt();
                Instant incomingLastSeenAt = profile.lastSeenAt();
                Optional<String> storedName = existingProfile.lastKnownName();
                Optional<String> nextName =
                        !incomingLastSeenAt.isBefore(storedLastSeenAt) ? profile.lastKnownName() : storedName;
                Instant nextLastSeenAt =
                        incomingLastSeenAt.isAfter(storedLastSeenAt) ? incomingLastSeenAt : storedLastSeenAt;
                return new PlayerProfile(playerId, nextName, existingProfile.firstSeenAt(), nextLastSeenAt);
            });
        }

        @Override
        public void restoreExact(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }

        @Override
        public void delete(PlayerId playerId) {
            profiles.remove(playerId);
        }
    }

    private static final class MapPlayerRatingRepository implements PlayerRatingRepository {
        private final Map<RatingKey, PlayerRating> ratings = new HashMap<>();

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.ofNullable(ratings.get(new RatingKey(playerId, kitId)));
        }

        @Override
        public List<PlayerRating> findByPlayer(PlayerId playerId) {
            return ratings.values().stream()
                    .filter(rating -> rating.playerId().equals(playerId))
                    .sorted(Comparator.comparing(rating -> rating.kitId().value()))
                    .toList();
        }

        @Override
        public void replaceAllForPlayer(PlayerId playerId, List<PlayerRating> replacementRatings) {
            ratings.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
            for (PlayerRating rating : replacementRatings) {
                ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
            }
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
    }

    private record StatsKey(PlayerId playerId, KitId kitId) {
    }

    private record FailingAfterImportMatchSettlementRepository(
            InMemoryMatchSettlementRepository delegate,
            String failureMessage)
            implements MatchSettlementRepository {

        @Override
        public boolean record(io.github.xreatlabz.revprac.domain.stats.MatchSettlement settlement) {
            return delegate.record(settlement);
        }

        @Override
        public Optional<MatchHistoryEntry> findHistory(MatchId matchId) {
            return delegate.findHistory(matchId);
        }

        @Override
        public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
            return delegate.findStats(playerId, kitId);
        }

        @Override
        public List<PlayerKitStats> findStatsByPlayer(PlayerId playerId) {
            return delegate.findStatsByPlayer(playerId);
        }

        @Override
        public List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
            return delegate.findRecentHistory(playerId, limit, offset);
        }

        @Override
        public List<MatchHistoryEntry> findAllHistory(PlayerId playerId) {
            return delegate.findAllHistory(playerId);
        }

        @Override
        public void validateImportHistoryCompatibility(PlayerId playerId, List<MatchHistoryEntry> history) {
            delegate.validateImportHistoryCompatibility(playerId, history);
        }

        @Override
        public void importPlayerRecords(
                PlayerId playerId,
                List<PlayerKitStats> stats,
                List<MatchHistoryEntry> history) {
            delegate.importPlayerRecords(playerId, stats, history);
            throw new IllegalStateException(failureMessage);
        }

        @Override
        public void restoreImportedPlayerRecords(
                PlayerId playerId,
                List<PlayerKitStats> stats,
                List<MatchHistoryEntry> history) {
            delegate.restoreImportedPlayerRecords(playerId, stats, history);
        }
    }
}
