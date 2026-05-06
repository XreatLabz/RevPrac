package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.application.ratings.RatingService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class MatchSettlementServiceTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-05-02T14:00:00Z");

    @Test
    void settlesWinAndForfeitOutcomesIntoHistoryAndPlayerStats() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match win = completedMatch("win", MatchOutcome.win(player("one"), player("two")), 45);
        Match forfeit = completedMatch(
                "forfeit",
                player("three"),
                player("four"),
                MatchOutcome.forfeit(player("three"), player("four")),
                12);

        service.settle(win);
        service.settle(forfeit);

        assertEquals(MatchEndReason.WIN, repository.findHistory(win.id()).orElseThrow().endReason());
        assertEquals(MatchOrigin.DIRECT_DUEL, repository.findHistory(win.id()).orElseThrow().origin());
        assertEquals(Optional.of(player("one")), repository.findHistory(win.id()).orElseThrow().winnerId());
        assertStats(repository.findStats(player("one"), new KitId("nodebuff")).orElseThrow(), 1, 1, 0, 0, 0, 0);
        assertStats(repository.findStats(player("two"), new KitId("nodebuff")).orElseThrow(), 1, 0, 1, 0, 0, 0);
        assertStats(repository.findStats(player("three"), new KitId("nodebuff")).orElseThrow(), 1, 1, 0, 0, 0, 0);
        assertStats(repository.findStats(player("four"), new KitId("nodebuff")).orElseThrow(), 1, 0, 1, 1, 0, 0);
    }

    @Test
    void settlesTimeoutAndShutdownOutcomesWithoutWinnerOrLoserStats() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match timeout = completedMatch("timeout", MatchOutcome.timeout(), 600);
        Match shutdown = completedMatch("shutdown", MatchOutcome.shutdown(), 20);

        service.settle(timeout);
        service.settle(shutdown);

        assertEquals(Optional.empty(), repository.findHistory(timeout.id()).orElseThrow().winnerId());
        assertStats(repository.findStats(player("one"), new KitId("nodebuff")).orElseThrow(), 2, 0, 0, 0, 1, 1);
        assertStats(repository.findStats(player("two"), new KitId("nodebuff")).orElseThrow(), 2, 0, 0, 0, 1, 1);
    }

    @Test
    void rejectsMatchesThatAreNotCompleted() {
        InMemoryMatchSettlementRepository repository = new InMemoryMatchSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository);
        Match active = Match.create(
                        new MatchId(UUID.nameUUIDFromBytes("active".getBytes())),
                        new MatchParticipants(player("one"), player("two")),
                        new ArenaId("arena-one"),
                        new KitId("nodebuff"),
                        new ArenaReservationId(UUID.nameUUIDFromBytes("reservation".getBytes())),
                        new MatchRuleset(1, 200, true))
                .tickCountdown();

        assertThrows(IllegalArgumentException.class, () -> service.settle(active));
    }

    @Test
    void rankedQueueWinsAndForfeitsCarryTwoRatingUpdates() {
        CapturingMatchSettlementRepository repository = new CapturingMatchSettlementRepository();
        MatchSettlementService service =
                new MatchSettlementService(repository, new RatingService(new FakePlayerRatingRepository()), 1000);
        Match rankedWin = completedMatch("ranked-win", MatchOrigin.QUEUE_RANKED, MatchOutcome.win(player("one"), player("two")), 45);
        Match rankedForfeit = completedMatch(
                "ranked-forfeit",
                MatchOrigin.QUEUE_RANKED,
                player("three"),
                player("four"),
                MatchOutcome.forfeit(player("three"), player("four")),
                12);

        service.settle(rankedWin);
        MatchSettlement winSettlement = repository.lastRecorded().orElseThrow();
        assertEquals(2, winSettlement.ratingUpdates().size());
        assertEquals(new PlayerRating(player("one"), new KitId("nodebuff"), 1016, 1, 0, COMPLETED_AT),
                winSettlement.ratingUpdates().getFirst());
        assertEquals(new PlayerRating(player("two"), new KitId("nodebuff"), 984, 0, 1, COMPLETED_AT),
                winSettlement.ratingUpdates().get(1));

        service.settle(rankedForfeit);
        MatchSettlement forfeitSettlement = repository.lastRecorded().orElseThrow();
        assertEquals(2, forfeitSettlement.ratingUpdates().size());
        assertEquals(Optional.of(player("three")), forfeitSettlement.history().winnerId());
        assertEquals(Optional.of(player("four")), forfeitSettlement.history().loserId());
    }

    @Test
    void directDuelAndUnrankedQueueSettlementsCarryNoRatingUpdates() {
        CapturingMatchSettlementRepository repository = new CapturingMatchSettlementRepository();
        MatchSettlementService service =
                new MatchSettlementService(repository, new RatingService(new FakePlayerRatingRepository()), 1000);

        service.settle(completedMatch("direct-duel", MatchOrigin.DIRECT_DUEL, MatchOutcome.win(player("one"), player("two")), 30));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());

        service.settle(
                completedMatch("queue-unranked", MatchOrigin.QUEUE_UNRANKED, MatchOutcome.forfeit(player("one"), player("two")), 20));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());
    }

    @Test
    void timeoutAndShutdownSettlementsCarryNoRatingUpdatesEvenForRankedQueueMatches() {
        CapturingMatchSettlementRepository repository = new CapturingMatchSettlementRepository();
        MatchSettlementService service =
                new MatchSettlementService(repository, new RatingService(new FakePlayerRatingRepository()), 1000);

        service.settle(completedMatch("ranked-timeout", MatchOrigin.QUEUE_RANKED, MatchOutcome.timeout(), 600));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());

        service.settle(completedMatch("ranked-shutdown", MatchOrigin.QUEUE_RANKED, MatchOutcome.shutdown(), 20));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());
    }

    @Test
    void queueBackedRatingServiceStillRecordsNoOpSettlementsWithoutRatingUpdates() {
        CapturingMatchSettlementRepository repository = new CapturingMatchSettlementRepository();
        MatchSettlementService service =
                new MatchSettlementService(repository, RatingService.fromQueueRatingRepository(new FakeQueueRatingRepository()), 1000);

        service.settle(completedMatch("queue-store-direct", MatchOrigin.DIRECT_DUEL, MatchOutcome.win(player("one"), player("two")), 30));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());

        service.settle(completedMatch(
                "queue-store-unranked",
                MatchOrigin.QUEUE_UNRANKED,
                MatchOutcome.forfeit(player("one"), player("two")),
                20));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());

        service.settle(completedMatch("queue-store-timeout", MatchOrigin.QUEUE_RANKED, MatchOutcome.timeout(), 600));
        assertEquals(0, repository.lastRecorded().orElseThrow().ratingUpdates().size());
    }

    @Test
    void overlappingRankedSettlementsForTheSamePlayersPreserveAccumulatedWinsAndLosses() throws Exception {
        BlockingRatingSettlementRepository repository = new BlockingRatingSettlementRepository();
        MatchSettlementService service = new MatchSettlementService(repository, new RatingService(repository), 1000);
        Match first = completedMatch("ranked-overlap-one", MatchOrigin.QUEUE_RANKED, MatchOutcome.win(player("one"), player("two")), 45);
        Match second = completedMatch("ranked-overlap-two", MatchOrigin.QUEUE_RANKED, MatchOutcome.win(player("one"), player("two")), 52);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstSettlement = executor.submit(() -> service.settle(first));
            repository.awaitFirstRecordAttempt();
            Future<?> secondSettlement = executor.submit(() -> service.settle(second));

            firstSettlement.get(5, TimeUnit.SECONDS);
            secondSettlement.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        PlayerRating winnerRating = repository.find(player("one"), new KitId("nodebuff")).orElseThrow();
        PlayerRating loserRating = repository.find(player("two"), new KitId("nodebuff")).orElseThrow();
        assertEquals(new PlayerRating(player("one"), new KitId("nodebuff"), 1031, 2, 0, COMPLETED_AT), winnerRating);
        assertEquals(new PlayerRating(player("two"), new KitId("nodebuff"), 969, 0, 2, COMPLETED_AT), loserRating);
    }

    private static Match completedMatch(String seed, MatchOutcome outcome, int activeTicks) {
        return completedMatch(seed, MatchOrigin.DIRECT_DUEL, outcome, activeTicks);
    }

    private static Match completedMatch(String seed, MatchOrigin origin, MatchOutcome outcome, int activeTicks) {
        return completedMatch(seed, origin, player("one"), player("two"), outcome, activeTicks);
    }

    private static Match completedMatch(
            String seed,
            PlayerId firstPlayer,
            PlayerId secondPlayer,
            MatchOutcome outcome,
            int activeTicks) {
        return completedMatch(seed, MatchOrigin.DIRECT_DUEL, firstPlayer, secondPlayer, outcome, activeTicks);
    }

    private static Match completedMatch(
            String seed,
            MatchOrigin origin,
            PlayerId firstPlayer,
            PlayerId secondPlayer,
            MatchOutcome outcome,
            int activeTicks) {
        return new Match(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                new MatchParticipants(firstPlayer, secondPlayer),
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                origin,
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                new MatchRuleset(1, 1_200, true),
                MatchState.COMPLETED,
                0,
                activeTicks,
                Set.of(),
                Optional.of(outcome),
                Optional.of(COMPLETED_AT));
    }

    private static void assertStats(
            PlayerKitStats stats,
            long matchesPlayed,
            long wins,
            long losses,
            long forfeits,
            long timeouts,
            long shutdowns) {
        assertEquals(matchesPlayed, stats.matchesPlayed());
        assertEquals(wins, stats.wins());
        assertEquals(losses, stats.losses());
        assertEquals(forfeits, stats.forfeits());
        assertEquals(timeouts, stats.timeouts());
        assertEquals(shutdowns, stats.shutdowns());
        assertEquals(COMPLETED_AT, stats.updatedAt());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static final class FakePlayerRatingRepository implements PlayerRatingRepository {
        private final Map<RatingKey, PlayerRating> ratings = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.ofNullable(ratings.get(new RatingKey(playerId, kitId)));
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }
    }

    private static final class CapturingMatchSettlementRepository implements MatchSettlementRepository {
        private MatchSettlement lastRecorded;

        @Override
        public void record(MatchSettlement settlement) {
            this.lastRecorded = settlement;
        }

        @Override
        public Optional<io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry> findHistory(io.github.xreatlabz.revprac.domain.matches.MatchId matchId) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
            return Optional.empty();
        }

        private Optional<MatchSettlement> lastRecorded() {
            return Optional.ofNullable(lastRecorded);
        }
    }

    private static final class BlockingRatingSettlementRepository
            implements MatchSettlementRepository, PlayerRatingRepository {

        private final Map<RatingKey, PlayerRating> ratings = new ConcurrentHashMap<>();
        private final CountDownLatch firstRecordAttempt = new CountDownLatch(1);
        private final CountDownLatch overlappingReadAttempt = new CountDownLatch(1);
        private volatile boolean firstRecordSeen;

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            if (firstRecordSeen) {
                overlappingReadAttempt.countDown();
            }
            return Optional.ofNullable(ratings.get(new RatingKey(playerId, kitId)));
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }

        @Override
        public void record(MatchSettlement settlement) {
            if (!firstRecordSeen) {
                firstRecordSeen = true;
                firstRecordAttempt.countDown();
                awaitAtMost(overlappingReadAttempt, 250L);
            }
            for (PlayerRating rating : settlement.ratingUpdates()) {
                upsert(rating);
            }
        }

        @Override
        public Optional<io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry> findHistory(
                io.github.xreatlabz.revprac.domain.matches.MatchId matchId) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
            return Optional.empty();
        }

        private void awaitFirstRecordAttempt() {
            await(firstRecordAttempt);
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for latch");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for latch", exception);
            }
        }

        private static void awaitAtMost(CountDownLatch latch, long timeoutMillis) {
            try {
                latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for latch", exception);
            }
        }
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
    }

    private static final class FakeQueueRatingRepository implements io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository {

        @Override
        public int rating(PlayerId playerId, KitId kitId, int defaultRating) {
            return defaultRating;
        }

        @Override
        public void save(PlayerId playerId, KitId kitId, int rating) {
            throw new AssertionError("no-op settlements should not write queue-backed ratings");
        }
    }
}
