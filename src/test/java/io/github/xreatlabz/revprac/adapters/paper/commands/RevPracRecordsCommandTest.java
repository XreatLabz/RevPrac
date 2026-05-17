package io.github.xreatlabz.revprac.adapters.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerRecordTransferFiles;
import io.github.xreatlabz.revprac.adapters.storage.CompositePlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerDirectoryService;
import io.github.xreatlabz.revprac.application.players.PlayerRecordQueryService;
import io.github.xreatlabz.revprac.application.players.PlayerRecordTransferService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RevPracRecordsCommandTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void commandEnforcesRootAndSubPermissionsAndAllowsConsole() {
        Harness harness = new Harness(tempDir);
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);
        harness.player.setOp(false);

        harness.command.onCommand(harness.player, command(), "records", new String[] {"summary", "Target", "nodebuff"});
        assertEquals("You do not have permission to use this command.", harness.player.nextMessage());

        grant(harness, RevPracRecordsCommand.RECORDS_PERMISSION, true);
        harness.command.onCommand(harness.player, command(), "records", new String[] {"summary", "Target", "nodebuff"});
        assertEquals("You do not have permission to use this command.", harness.player.nextMessage());

        grant(harness, RevPracRecordsCommand.RECORDS_LOOKUP_PERMISSION, true);
        harness.profile(harness.targetId(), "Target");
        harness.recordWin("console-summary", harness.targetId(), playerId("console-opponent"), new KitId("nodebuff"));
        harness.command.onCommand(harness.server.getConsoleSender(), command(), "records", new String[] {"history", "Target"});

        assertTrue(harness.server.getConsoleSender().nextMessage().contains("kit=nodebuff"));
    }

    @Test
    void summaryUsesExactNameLookupAndPrintsTargetLabel() {
        Harness harness = new Harness(tempDir);
        harness.allowLookup();
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);
        harness.profile(harness.targetId(), "Target");
        harness.recordWin("summary-win", harness.targetId(), playerId("summary-opponent"), new KitId("nodebuff"));
        harness.ratings.upsert(new PlayerRating(
                harness.targetId(),
                new KitId("nodebuff"),
                1185,
                7,
                3,
                instant("2026-05-04T15:00:00Z")));

        harness.command.onCommand(harness.player, command(), "records", new String[] {"summary", "target", "nodebuff"});

        assertEquals(
                "Target (" + harness.targetId().value() + "): NoDebuff (nodebuff): matches=1 wins=1 losses=0 rating=1185",
                harness.player.nextMessage());
    }

    @Test
    void historyReportsAmbiguousAndUnknownSelectorsWithStableMessages() {
        Harness harness = new Harness(tempDir);
        harness.allowLookup();
        harness.profile(playerId("ambiguous-one"), "Dup");
        harness.profile(playerId("ambiguous-two"), "dup");

        harness.command.onCommand(harness.player, command(), "records", new String[] {"history", "Dup"});
        assertEquals("Player name is ambiguous; use UUID: Dup.", harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "records", new String[] {"history", "Missing"});
        assertEquals("Unknown player: Missing.", harness.player.nextMessage());
    }

    @Test
    void exportAndImportUseBoundedPathsAndTransferPermission() {
        Harness harness = new Harness(tempDir);
        harness.allowLookup();
        harness.allowTransfer();
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);
        harness.profile(harness.targetId(), "Target");
        harness.recordWin("export-win", harness.targetId(), playerId("export-opponent"), new KitId("nodebuff"));

        harness.command.onCommand(
                harness.player,
                command(),
                "records",
                new String[] {"export", harness.targetId().value().toString()});
        assertEquals(
                "Exported player records for Target (" + harness.targetId().value() + ") to exports/player-records/"
                        + harness.targetId().value() + ".yml",
                harness.player.nextMessage());

        Path importsDir = tempDir.resolve("imports/player-records");
        try {
            Files.createDirectories(importsDir);
            Files.copy(
                    tempDir.resolve("exports/player-records/" + harness.targetId().value() + ".yml"),
                    importsDir.resolve("bundle.yml"));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }

        harness.command.onCommand(harness.player, command(), "records", new String[] {"import", "bundle.yml"});
        assertEquals(
                "Imported player records for Target (" + harness.targetId().value() + ") from imports/player-records/bundle.yml",
                harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "records", new String[] {"import", "../bundle.yml"});
        assertEquals("Import file must be a simple .yml filename.", harness.player.nextMessage());
    }

    private static void grant(Harness harness, String permission, boolean value) {
        harness.player.addAttachment(harness.plugin, permission, value);
    }

    private static Command command() {
        return new Command("records") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    private static PlayerId playerId(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static MatchSettlement settlement(String seed, PlayerId winnerId, PlayerId loserId, KitId kitId) {
        Instant completedAt = instant("2026-05-04T12:00:00Z");
        return new MatchSettlement(
                new MatchHistoryEntry(
                        new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                        winnerId,
                        loserId,
                        new ArenaId("arena-one"),
                        kitId,
                        MatchOrigin.QUEUE_RANKED,
                        MatchEndReason.WIN,
                        Optional.of(winnerId),
                        Optional.of(loserId),
                        200,
                        completedAt),
                List.of(
                        new PlayerKitStatDelta(winnerId, kitId, 1, 1, 0, 0, 0, 0, completedAt),
                        new PlayerKitStatDelta(loserId, kitId, 1, 0, 1, 0, 0, 0, completedAt)));
    }

    private static final class Harness {
        private final ServerMock server = MockBukkit.mock();
        private final JavaPlugin plugin = MockBukkit.createMockPlugin();
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryMatchSettlementRepository matchSettlements = new InMemoryMatchSettlementRepository();
        private final MapPlayerRatingRepository ratings = new MapPlayerRatingRepository();
        private final MapPlayerProfileRepository profiles = new MapPlayerProfileRepository();
        private final PlayerRecordQueryService queryService = new PlayerRecordQueryService(
                kitRegistryService,
                matchSettlements,
                ratings,
                profiles,
                QueueConfig.defaults());
        private final PlayerDirectoryService directoryService = new PlayerDirectoryService(profiles);
        private final PlayerRecordTransferService transferService;
        private final PlayerMock player = server.addPlayer("requester");
        private final RevPracRecordsCommand command;

        private Harness(Path dataDirectory) {
            this.transferService = new PlayerRecordTransferService(
                    new CompositePlayerRecordTransferRepository(profiles, ratings, matchSettlements),
                    new PaperPlayerRecordTransferFiles(dataDirectory));
            this.command = new RevPracRecordsCommand(directoryService, queryService, transferService);
        }

        private PlayerId targetId() {
            return playerId("records-target");
        }

        private void allowLookup() {
            grant(this, RevPracRecordsCommand.RECORDS_PERMISSION, true);
            grant(this, RevPracRecordsCommand.RECORDS_LOOKUP_PERMISSION, true);
        }

        private void allowTransfer() {
            grant(this, RevPracRecordsCommand.RECORDS_PERMISSION, true);
            grant(this, RevPracRecordsCommand.RECORDS_TRANSFER_PERMISSION, true);
        }

        private void registerKit(KitId kitId, String displayName, boolean ranked) {
            kitRegistryService.register(new KitDefinition(
                    kitId,
                    displayName,
                    new KitInventory(List.of(), List.of(), List.of(), 0),
                    List.of(),
                    new KitRules(false, false, false, ranked),
                    true));
        }

        private void recordWin(String seed, PlayerId winnerId, PlayerId loserId, KitId kitId) {
            matchSettlements.record(settlement(seed, winnerId, loserId, kitId));
        }

        private void profile(PlayerId playerId, String name) {
            profiles.upsert(new PlayerProfile(
                    playerId,
                    Optional.of(name),
                    instant("2026-05-01T00:00:00Z"),
                    instant("2026-05-04T00:00:00Z")));
        }
    }

    private static final class MapPlayerRatingRepository implements PlayerRatingRepository {
        private final Map<Key, PlayerRating> ratings = new HashMap<>();

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.ofNullable(ratings.get(new Key(playerId, kitId)));
        }

        @Override
        public List<PlayerRating> findByPlayer(PlayerId playerId) {
            return ratings.values().stream()
                    .filter(rating -> rating.playerId().equals(playerId))
                    .toList();
        }

        @Override
        public void replaceAllForPlayer(PlayerId playerId, List<PlayerRating> replacementRatings) {
            ratings.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
            for (PlayerRating rating : replacementRatings) {
                ratings.put(new Key(rating.playerId(), rating.kitId()), rating);
            }
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new Key(rating.playerId(), rating.kitId()), rating);
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

    private record Key(PlayerId playerId, KitId kitId) {
    }
}
