package io.github.xreatlabz.revprac.application.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
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
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.kits.KitRegistryRepository;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerRecordQueryServiceTest {

    private static final QueueConfig QUEUE_CONFIG = QueueConfig.defaults();

    @Test
    void summaryReturnsZeroStatsAndBaseRatingWhenNoStatsExistForARankedKit() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("ranked-player");
        KitId rankedKitId = new KitId("nodebuff");
        harness.registerKit(rankedKitId, "NoDebuff", true, true);

        PlayerKitSummaryView summary = harness.service().summary(playerId, rankedKitId);

        assertEquals(rankedKitId, summary.kitId());
        assertEquals("NoDebuff", summary.displayName());
        assertEquals(0L, summary.matchesPlayed());
        assertEquals(0L, summary.wins());
        assertEquals(0L, summary.losses());
        assertEquals(0L, summary.forfeits());
        assertEquals(0L, summary.timeouts());
        assertEquals(0L, summary.shutdowns());
        assertEquals(Optional.of(new PlayerRatingView(QUEUE_CONFIG.rankedBaseRating())), summary.rating());
    }

    @Test
    void summaryUsesPersistedRatingsForRankedKitsAndOmitsRatingsForUnrankedKits() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("summary-player");
        KitId rankedKitId = new KitId("nodebuff");
        KitId unrankedKitId = new KitId("boxing");
        harness.registerKit(rankedKitId, "NoDebuff", true, true);
        harness.registerKit(unrankedKitId, "Boxing", true, false);
        harness.ratings.put(key(playerId, rankedKitId), new PlayerRating(playerId, rankedKitId, 1185, 12, 4, instant("2026-05-04T10:00:00Z")));

        PlayerKitSummaryView rankedSummary = harness.service().summary(playerId, rankedKitId);
        PlayerKitSummaryView unrankedSummary = harness.service().summary(playerId, unrankedKitId);

        assertEquals(Optional.of(new PlayerRatingView(1185)), rankedSummary.rating());
        assertEquals(Optional.empty(), unrankedSummary.rating());
    }

    @Test
    void summaryRejectsUnknownAndDisabledKits() {
        TestHarness harness = new TestHarness();
        KitId disabledKitId = new KitId("sumo");
        harness.registerKit(disabledKitId, "Sumo", false, true);

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().summary(player("missing-player"), new KitId("missing-kit")));
        IllegalArgumentException disabled = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().summary(player("disabled-player"), disabledKitId));

        assertEquals("unknown kit: missing-kit", missing.getMessage());
        assertEquals("unknown kit: sumo", disabled.getMessage());
    }

    @Test
    void summarySanitizesUnexpectedKitRegistryFailures() {
        PlayerRecordQueryService service = new PlayerRecordQueryService(
                new KitRegistryService(new FailingKitRegistryRepository()),
                new FakeMatchSettlementRepository(),
                new FakePlayerRatingRepository(),
                new FakePlayerProfileRepository(),
                QUEUE_CONFIG);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.summary(player("kit-registry-failure-player"), new KitId("nodebuff")));

        assertEquals("player records are temporarily unavailable", failure.getMessage());
    }

    @Test
    void recentHistoryReturnsNewestFirstAcrossBothParticipantsAndTracksHasNextPage() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("history-player");
        PlayerId opponentOne = player("history-opponent-one");
        PlayerId opponentTwo = player("history-opponent-two");
        PlayerId opponentThree = player("history-opponent-three");
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true, true);
        harness.history.add(history("history-1", playerId, opponentOne, kitId, instant("2026-05-04T10:00:00Z")));
        harness.history.add(history("history-2", opponentTwo, playerId, kitId, instant("2026-05-04T11:00:00Z")));
        harness.history.add(history("history-3", playerId, opponentThree, kitId, instant("2026-05-04T12:00:00Z")));
        harness.history.add(history("history-unrelated", player("other-one"), player("other-two"), kitId, instant("2026-05-04T13:00:00Z")));
        harness.profiles.put(opponentThree, new PlayerProfile(opponentThree, Optional.of("Gamma"), instant("2026-05-01T00:00:00Z"), instant("2026-05-04T12:00:00Z")));
        harness.profiles.put(opponentTwo, new PlayerProfile(opponentTwo, Optional.of("Beta"), instant("2026-05-01T00:00:00Z"), instant("2026-05-04T11:00:00Z")));

        PlayerMatchHistoryPage firstPage = harness.service().recentHistory(playerId, 1, 2);
        PlayerMatchHistoryPage secondPage = harness.service().recentHistory(playerId, 2, 2);

        assertEquals(1, firstPage.page());
        assertEquals(2, firstPage.pageSize());
        assertTrue(firstPage.hasNextPage());
        assertEquals(2, firstPage.items().size());
        assertEquals(opponentThree, firstPage.items().get(0).opponentId());
        assertEquals("Gamma", firstPage.items().get(0).opponentName());
        assertEquals(opponentTwo, firstPage.items().get(1).opponentId());
        assertEquals("Beta", firstPage.items().get(1).opponentName());
        assertEquals(List.of(
                        instant("2026-05-04T12:00:00Z"),
                        instant("2026-05-04T11:00:00Z")),
                firstPage.items().stream().map(PlayerMatchHistoryLineItem::completedAt).toList());

        assertFalse(secondPage.hasNextPage());
        assertEquals(1, secondPage.items().size());
        assertEquals(opponentOne, secondPage.items().getFirst().opponentId());
        assertEquals("Unknown player", secondPage.items().getFirst().opponentName());
    }

    @Test
    void recentHistoryUsesMatchIdTextOrderingForCompletedAtTies() {
        PlayerId playerId = player("history-tie-text-order-player");
        PlayerId firstOpponent = player("history-tie-text-order-opponent-one");
        PlayerId secondOpponent = player("history-tie-text-order-opponent-two");
        KitId kitId = new KitId("nodebuff");
        Instant completedAt = instant("2026-05-04T15:30:00Z");
        MatchId textHigherButUuidLower = new MatchId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        MatchId textLowerButUuidHigher = new MatchId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));
        InMemoryMatchSettlementRepository matchSettlements = new InMemoryMatchSettlementRepository();
        KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        registerKit(kitRegistryService, kitId, "NoDebuff", true, true);
        PlayerRecordQueryService service = new PlayerRecordQueryService(
                kitRegistryService,
                matchSettlements,
                new FakePlayerRatingRepository(),
                new FakePlayerProfileRepository(),
                QUEUE_CONFIG);
        matchSettlements.record(settlement(textHigherButUuidLower, playerId, firstOpponent, kitId, completedAt));
        matchSettlements.record(settlement(textLowerButUuidHigher, playerId, secondOpponent, kitId, completedAt));

        PlayerMatchHistoryPage page = service.recentHistory(playerId, 1, 2);

        assertEquals(List.of(textHigherButUuidLower, textLowerButUuidHigher), page.items().stream()
                .map(PlayerMatchHistoryLineItem::matchId)
                .toList());
    }

    @Test
    void recentHistoryBreaksCompletedAtTiesByMatchIdDescending() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("history-tie-player");
        PlayerId firstOpponent = player("history-tie-opponent-one");
        PlayerId secondOpponent = player("history-tie-opponent-two");
        KitId kitId = new KitId("nodebuff");
        Instant completedAt = instant("2026-05-04T15:00:00Z");
        MatchId lowerMatchId = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MatchId higherMatchId = new MatchId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        harness.registerKit(kitId, "NoDebuff", true, true);
        harness.history.add(history(lowerMatchId, playerId, firstOpponent, kitId, completedAt));
        harness.history.add(history(higherMatchId, playerId, secondOpponent, kitId, completedAt));

        PlayerMatchHistoryPage page = harness.service().recentHistory(playerId, 1, 2);

        assertEquals(List.of(higherMatchId, lowerMatchId), page.items().stream()
                .map(PlayerMatchHistoryLineItem::matchId)
                .toList());
    }

    @Test
    void recentHistoryRejectsOutOfRangePageSizesAndPagesOutsideStableRange() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("pagination-player");

        IllegalArgumentException zeroPage = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().recentHistory(playerId, 0, 5));
        IllegalArgumentException zeroPageSize = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().recentHistory(playerId, 1, 0));
        IllegalArgumentException tooLargePageSize = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().recentHistory(playerId, 1, PlayerRecordQueryService.MAX_PAGE_SIZE + 1));
        IllegalArgumentException tooLargePage = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service().recentHistory(
                        playerId,
                        PlayerRecordQueryService.MAX_HISTORY_PAGE + 1,
                        PlayerRecordQueryService.MAX_PAGE_SIZE));

        assertEquals("page must be between 1 and 100", zeroPage.getMessage());
        assertEquals("pageSize must be between 1 and 10", zeroPageSize.getMessage());
        assertEquals("pageSize must be between 1 and 10", tooLargePageSize.getMessage());
        assertEquals("page must be between 1 and 100", tooLargePage.getMessage());
    }

    @Test
    void summarySanitizesStatsAndRatingLookupFailures() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("failure-player");
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true, true);

        harness.failStatsLookup = true;
        IllegalStateException statsFailure =
                assertThrows(IllegalStateException.class, () -> harness.service().summary(playerId, kitId));
        assertEquals("player records are temporarily unavailable", statsFailure.getMessage());

        harness.failStatsLookup = false;
        harness.failRatingLookup = true;
        IllegalStateException ratingFailure =
                assertThrows(IllegalStateException.class, () -> harness.service().summary(playerId, kitId));
        assertEquals("player records are temporarily unavailable", ratingFailure.getMessage());
    }

    @Test
    void recentHistorySanitizesRepositoryFailures() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("history-repository-failure-player");
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true, true);
        harness.history.add(history(
                "failure-history-repository",
                playerId,
                player("failure-history-repository-opponent"),
                kitId,
                instant("2026-05-04T09:00:00Z")));
        harness.failHistoryLookup = true;

        IllegalStateException historyFailure =
                assertThrows(IllegalStateException.class, () -> harness.service().recentHistory(playerId, 1, 5));

        assertEquals("player records are temporarily unavailable", historyFailure.getMessage());
    }

    @Test
    void recentHistorySanitizesProfileLookupFailures() {
        TestHarness harness = new TestHarness();
        PlayerId playerId = player("history-profile-failure-player");
        KitId kitId = new KitId("nodebuff");
        harness.registerKit(kitId, "NoDebuff", true, true);
        harness.history.add(history(
                "failure-history-profile",
                playerId,
                player("failure-profile-opponent"),
                kitId,
                instant("2026-05-04T09:00:00Z")));
        harness.failProfileLookup = true;

        IllegalStateException historyFailure =
                assertThrows(IllegalStateException.class, () -> harness.service().recentHistory(playerId, 1, 5));

        assertEquals("player records are temporarily unavailable", historyFailure.getMessage());
    }

    private static MatchHistoryEntry history(
            String seed,
            PlayerId playerOneId,
            PlayerId playerTwoId,
            KitId kitId,
            Instant completedAt) {
        return new MatchHistoryEntry(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                playerOneId,
                playerTwoId,
                new ArenaId("arena-one"),
                kitId,
                MatchOrigin.QUEUE_RANKED,
                MatchEndReason.WIN,
                Optional.of(playerOneId),
                Optional.of(playerTwoId),
                100,
                completedAt);
    }

    private static MatchHistoryEntry history(
            MatchId matchId,
            PlayerId playerOneId,
            PlayerId playerTwoId,
            KitId kitId,
            Instant completedAt) {
        return new MatchHistoryEntry(
                matchId,
                playerOneId,
                playerTwoId,
                new ArenaId("arena-one"),
                kitId,
                MatchOrigin.QUEUE_RANKED,
                MatchEndReason.WIN,
                Optional.of(playerOneId),
                Optional.of(playerTwoId),
                100,
                completedAt);
    }

    private static io.github.xreatlabz.revprac.domain.stats.MatchSettlement settlement(
            MatchId matchId,
            PlayerId winnerId,
            PlayerId loserId,
            KitId kitId,
            Instant completedAt) {
        return new io.github.xreatlabz.revprac.domain.stats.MatchSettlement(
                new MatchHistoryEntry(
                        matchId,
                        winnerId,
                        loserId,
                        new ArenaId("arena-one"),
                        kitId,
                        MatchOrigin.QUEUE_RANKED,
                        MatchEndReason.WIN,
                        Optional.of(winnerId),
                        Optional.of(loserId),
                        100,
                        completedAt),
                List.of(
                        new io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta(
                                winnerId, kitId, 1, 1, 0, 0, 0, 0, completedAt),
                        new io.github.xreatlabz.revprac.domain.stats.PlayerKitStatDelta(
                                loserId, kitId, 1, 0, 1, 0, 0, 0, completedAt)),
                List.of());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static RatingKey key(PlayerId playerId, KitId kitId) {
        return new RatingKey(playerId, kitId);
    }

    private static final class TestHarness {
        private final FakeMatchSettlementRepository matchSettlements = new FakeMatchSettlementRepository();
        private final FakePlayerRatingRepository ratingsRepository = new FakePlayerRatingRepository();
        private final FakePlayerProfileRepository profilesRepository = new FakePlayerProfileRepository();
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final Map<RatingKey, PlayerRating> ratings = ratingsRepository.ratings;
        private final Map<PlayerId, PlayerProfile> profiles = profilesRepository.profiles;
        private final List<MatchHistoryEntry> history = matchSettlements.history;
        private boolean failStatsLookup;
        private boolean failHistoryLookup;
        private boolean failProfileLookup;
        private boolean failRatingLookup;

        private PlayerRecordQueryService service() {
            matchSettlements.failStatsLookup = failStatsLookup;
            matchSettlements.failHistoryLookup = failHistoryLookup;
            profilesRepository.failLookup = failProfileLookup;
            ratingsRepository.failLookup = failRatingLookup;
            return new PlayerRecordQueryService(
                    kitRegistryService,
                    matchSettlements,
                    ratingsRepository,
                    profilesRepository,
                    QUEUE_CONFIG);
        }

        private void registerKit(KitId kitId, String displayName, boolean enabled, boolean ranked) {
            PlayerRecordQueryServiceTest.registerKit(kitRegistryService, kitId, displayName, enabled, ranked);
        }
    }

    private static void registerKit(
            KitRegistryService kitRegistryService, KitId kitId, String displayName, boolean enabled, boolean ranked) {
        kitRegistryService.register(new KitDefinition(
                kitId,
                displayName,
                new KitInventory(List.of("sword"), List.of("helmet", "chestplate", "leggings", "boots"), List.of(), 0),
                List.of(),
                new KitRules(false, false, true, ranked),
                enabled));
    }

    private static final class FakeMatchSettlementRepository implements MatchSettlementRepository {
        private static final Comparator<MatchHistoryEntry> RECENT_HISTORY_ORDER = Comparator
                .comparing(MatchHistoryEntry::completedAt)
                .reversed()
                .thenComparing(entry -> entry.matchId().value().toString(), Comparator.reverseOrder());

        private final Map<StatsKey, PlayerKitStats> stats = new HashMap<>();
        private final List<MatchHistoryEntry> history = new ArrayList<>();
        private boolean failStatsLookup;
        private boolean failHistoryLookup;

        @Override
        public void record(io.github.xreatlabz.revprac.domain.stats.MatchSettlement settlement) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<MatchHistoryEntry> findHistory(MatchId matchId) {
            return history.stream().filter(entry -> entry.matchId().equals(matchId)).findFirst();
        }

        @Override
        public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
            if (failStatsLookup) {
                throw new IllegalStateException("stats unavailable");
            }
            return Optional.ofNullable(stats.get(new StatsKey(playerId, kitId)));
        }

        @Override
        public List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
            if (failHistoryLookup) {
                throw new IllegalStateException("history unavailable");
            }
            return history.stream()
                    .filter(entry -> entry.playerOneId().equals(playerId) || entry.playerTwoId().equals(playerId))
                    .sorted(RECENT_HISTORY_ORDER)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }
    }

    private static final class FakePlayerRatingRepository implements PlayerRatingRepository {
        private final Map<RatingKey, PlayerRating> ratings = new HashMap<>();
        private boolean failLookup;

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            if (failLookup) {
                throw new IllegalStateException("ratings unavailable");
            }
            return Optional.ofNullable(ratings.get(new RatingKey(playerId, kitId)));
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }
    }

    private static final class FakePlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new HashMap<>();
        private boolean failLookup;

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            if (failLookup) {
                throw new IllegalStateException("profiles unavailable");
            }
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }
    }

    private record StatsKey(PlayerId playerId, KitId kitId) {
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
    }

    private static final class FailingKitRegistryRepository implements KitRegistryRepository {
        @Override
        public Optional<KitDefinition> find(KitId kitId) {
            throw new IllegalStateException("kits unavailable");
        }

        @Override
        public boolean create(KitDefinition kitDefinition) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public java.util.Collection<KitDefinition> findAll() {
            throw new IllegalStateException("kits unavailable");
        }
    }
}
