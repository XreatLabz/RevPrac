package io.github.xreatlabz.revprac.adapters.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerRecordQueryService;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class RevPracStatsCommandTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void commandRequiresPlayerSenderAndStatsPermission() {
        Harness harness = new Harness();

        harness.command.onCommand(harness.server.getConsoleSender(), command(), "stats", new String[] {"history"});
        assertEquals("Only players can use /stats.", harness.server.getConsoleSender().nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history"});
        assertEquals("You do not have permission to use this command.", harness.player.nextMessage());
    }

    @Test
    void emptyArgsAndBadAritiesReturnUsage() {
        Harness harness = new Harness();
        harness.player.setOp(true);

        harness.command.onCommand(harness.player, command(), "stats", new String[0]);
        assertEquals("Usage: /stats summary <kit>|history [page]", harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"summary"});
        assertEquals("Usage: /stats summary <kit>|history [page]", harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history", "1", "extra"});
        assertEquals("Usage: /stats summary <kit>|history [page]", harness.player.nextMessage());
    }

    @Test
    void summaryPrintsStableLineWithMatchesWinsLossesAndRating() {
        Harness harness = new Harness();
        harness.player.setOp(true);
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);
        harness.recordWin("summary-win", harness.playerId(), playerId("summary-opponent"), new KitId("nodebuff"));
        harness.ratings.upsert(new PlayerRating(
                harness.playerId(),
                new KitId("nodebuff"),
                1185,
                7,
                3,
                instant("2026-05-04T15:00:00Z")));

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"summary", "nodebuff"});

        assertEquals(
                "NoDebuff (nodebuff): matches=1 wins=1 losses=0 rating=1185",
                harness.player.nextMessage());
    }

    @Test
    void summarySanitizesMalformedKitIdsToStableUnknownKitMessage() {
        Harness harness = new Harness();
        harness.player.setOp(true);

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"summary", "???"});

        assertEquals("unknown kit: ???", harness.player.nextMessage());
    }

    @Test
    void historyPrintsPersistedSelfEntriesAndHonorsExplicitPageNumbers() {
        Harness harness = new Harness();
        harness.player.setOp(true);
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true);

        PlayerId self = harness.playerId();
        PlayerId alpha = playerId("history-alpha");
        PlayerId beta = playerId("history-beta");
        PlayerId gamma = playerId("history-gamma");
        PlayerId delta = playerId("history-delta");
        PlayerId echo = playerId("history-echo");
        PlayerId foxtrot = playerId("history-foxtrot");
        harness.profile(alpha, "Alpha");
        harness.profile(beta, "Beta");
        harness.profile(gamma, "Gamma");
        harness.profile(delta, "Delta");
        harness.profile(echo, "Echo");
        harness.profile(foxtrot, "Foxtrot");
        harness.recordMatch(
                "history-1",
                self,
                alpha,
                self,
                alpha,
                kitId,
                instant("2026-05-04T10:00:00Z"));
        harness.recordMatch(
                "history-2",
                beta,
                self,
                beta,
                self,
                kitId,
                instant("2026-05-04T11:00:00Z"));
        harness.recordMatch(
                "history-3",
                self,
                gamma,
                self,
                gamma,
                kitId,
                instant("2026-05-04T12:00:00Z"));
        harness.recordMatch(
                "history-4",
                delta,
                self,
                delta,
                self,
                kitId,
                instant("2026-05-04T13:00:00Z"));
        harness.recordMatch(
                "history-5",
                self,
                echo,
                self,
                echo,
                kitId,
                instant("2026-05-04T14:00:00Z"));
        harness.recordMatch(
                "history-6",
                foxtrot,
                self,
                foxtrot,
                self,
                kitId,
                instant("2026-05-04T15:00:00Z"));

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history"});
        assertEquals(
                "2026-05-04T15:00:00Z kit=nodebuff opponent=Foxtrot result=loss origin=queue_ranked end=win",
                harness.player.nextMessage());
        assertEquals(
                "2026-05-04T14:00:00Z kit=nodebuff opponent=Echo result=win origin=queue_ranked end=win",
                harness.player.nextMessage());
        assertEquals(
                "2026-05-04T13:00:00Z kit=nodebuff opponent=Delta result=loss origin=queue_ranked end=win",
                harness.player.nextMessage());
        assertEquals(
                "2026-05-04T12:00:00Z kit=nodebuff opponent=Gamma result=win origin=queue_ranked end=win",
                harness.player.nextMessage());
        assertEquals(
                "2026-05-04T11:00:00Z kit=nodebuff opponent=Beta result=loss origin=queue_ranked end=win",
                harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history", "2"});
        assertEquals(
                "2026-05-04T10:00:00Z kit=nodebuff opponent=Alpha result=win origin=queue_ranked end=win",
                harness.player.nextMessage());
    }

    @Test
    void historyDefaultsToFirstPageAndPrintsEmptyState() {
        Harness harness = new Harness();
        harness.player.setOp(true);

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history"});

        assertEquals("No match history found.", harness.player.nextMessage());
    }

    @Test
    void historyRejectsPagesOutsideStableRange() {
        Harness harness = new Harness();
        harness.player.setOp(true);

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history", "0"});
        assertEquals("page must be between 1 and 100", harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history", "abc"});
        assertEquals("page must be between 1 and 100", harness.player.nextMessage());

        harness.command.onCommand(
                harness.player,
                command(),
                "stats",
                new String[] {"history", String.valueOf(PlayerRecordQueryService.MAX_HISTORY_PAGE + 1)});
        assertEquals("page must be between 1 and 100", harness.player.nextMessage());
    }

    @Test
    void historyPrintsUnknownPlayerWhenOpponentProfileIsMissing() {
        Harness harness = new Harness();
        harness.player.setOp(true);
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true);
        harness.recordMatch(
                "history-unknown-player",
                harness.playerId(),
                playerId("history-unknown-opponent"),
                harness.playerId(),
                playerId("history-unknown-opponent"),
                kitId,
                instant("2026-05-04T16:00:00Z"));

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history"});

        assertEquals(
                "2026-05-04T16:00:00Z kit=nodebuff opponent=Unknown player result=win origin=queue_ranked end=win",
                harness.player.nextMessage());
    }

    @Test
    void applicationErrorsAreCaughtAndSentBackToThePlayer() {
        Harness harness = new Harness(new FailingPlayerProfileRepository());
        harness.player.setOp(true);
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);
        harness.recordWin("history-failure", harness.playerId(), playerId("history-opponent"), new KitId("nodebuff"));

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"summary", "missing-kit"});
        assertEquals("unknown kit: missing-kit", harness.player.nextMessage());

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"history"});
        assertEquals("player records are temporarily unavailable", harness.player.nextMessage());
    }

    @Test
    void summaryRuntimeFailuresAreSanitizedThroughTheCommandAdapter() {
        Harness harness = new Harness(new MapPlayerProfileRepository(), new FailingPlayerRatingRepository());
        harness.player.setOp(true);
        harness.registerKit(new KitId("nodebuff"), "NoDebuff", true);

        harness.command.onCommand(harness.player, command(), "stats", new String[] {"summary", "nodebuff"});

        assertEquals("player records are temporarily unavailable", harness.player.nextMessage());
    }

    private static Command command() {
        return new Command("stats") {
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
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final InMemoryMatchSettlementRepository matchSettlements = new InMemoryMatchSettlementRepository();
        private final PlayerRatingRepository ratings;
        private final PlayerProfileRepository profiles;
        private final PlayerRecordQueryService queryService;
        private final PlayerMock player = server.addPlayer("requester");
        private final RevPracStatsCommand command;

        private Harness() {
            this(new MapPlayerProfileRepository(), new MapPlayerRatingRepository());
        }

        private Harness(PlayerProfileRepository profiles) {
            this(profiles, new MapPlayerRatingRepository());
        }

        private Harness(PlayerProfileRepository profiles, PlayerRatingRepository ratings) {
            this.profiles = profiles;
            this.ratings = ratings;
            this.queryService = new PlayerRecordQueryService(
                    kitRegistryService,
                    matchSettlements,
                    ratings,
                    profiles,
                    QueueConfig.defaults());
            this.command = new RevPracStatsCommand(queryService);
        }

        private PlayerId playerId() {
            return new PlayerId(player.getUniqueId());
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

        private void recordMatch(
                String seed,
                PlayerId playerOneId,
                PlayerId playerTwoId,
                PlayerId winnerId,
                PlayerId loserId,
                KitId kitId,
                Instant completedAt) {
            matchSettlements.record(new MatchSettlement(
                    new MatchHistoryEntry(
                            new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                            playerOneId,
                            playerTwoId,
                            new ArenaId("arena-one"),
                            kitId,
                            MatchOrigin.QUEUE_RANKED,
                            MatchEndReason.WIN,
                            Optional.of(winnerId),
                            Optional.of(loserId),
                            200,
                            completedAt),
                    List.of(
                            new PlayerKitStatDelta(
                                    playerOneId,
                                    kitId,
                                    1,
                                    playerOneId.equals(winnerId) ? 1 : 0,
                                    playerOneId.equals(loserId) ? 1 : 0,
                                    0,
                                    0,
                                    0,
                                    completedAt),
                            new PlayerKitStatDelta(
                                    playerTwoId,
                                    kitId,
                                    1,
                                    playerTwoId.equals(winnerId) ? 1 : 0,
                                    playerTwoId.equals(loserId) ? 1 : 0,
                                    0,
                                    0,
                                    0,
                                    completedAt))));
        }
    }

    private static final class MapPlayerRatingRepository implements PlayerRatingRepository {
        private final Map<Key, PlayerRating> ratings = new HashMap<>();

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.ofNullable(ratings.get(new Key(playerId, kitId)));
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
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }
    }

    private static final class FailingPlayerProfileRepository implements PlayerProfileRepository {
        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            throw new IllegalStateException("profile lookup failed");
        }

        @Override
        public void upsert(PlayerProfile profile) {
            throw new UnsupportedOperationException("unused");
        }
    }

    private static final class FailingPlayerRatingRepository implements PlayerRatingRepository {
        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            throw new IllegalStateException("rating lookup failed");
        }

        @Override
        public void upsert(PlayerRating rating) {
            throw new UnsupportedOperationException("unused");
        }
    }

    private record Key(PlayerId playerId, KitId kitId) {
    }
}
