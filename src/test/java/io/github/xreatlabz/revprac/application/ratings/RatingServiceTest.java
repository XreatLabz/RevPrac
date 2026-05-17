package io.github.xreatlabz.revprac.application.ratings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class RatingServiceTest {

    private static final KitId NODEBUFF = new KitId("nodebuff");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-04T12:00:00Z");

    @Test
    void ratingForQueueFallsBackToTheDefaultRatingWhenNoPersistentRatingExists() {
        RatingService service = new RatingService(new FakePlayerRatingRepository());

        int rating = service.ratingForQueue(player("missing-rating"), NODEBUFF, 1000);

        assertEquals(1000, rating);
    }

    @Test
    void ratingForQueueReturnsThePersistedPlayerKitRatingWhenItExists() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId playerId = player("persisted-rating");
        repository.upsert(new PlayerRating(playerId, NODEBUFF, 1285, 4, 1, Instant.parse("2026-05-02T12:00:00Z")));

        int rating = service.ratingForQueue(playerId, NODEBUFF, 1000);

        assertEquals(1285, rating);
    }

    @Test
    void saveSeedPersistsAZeroRecordRatingSeedForThePlayerAndKit() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId playerId = player("seed-rating");
        Instant updatedAt = Instant.parse("2026-05-02T12:30:00Z");

        PlayerRating saved = service.saveSeed(playerId, NODEBUFF, 1000, updatedAt);

        assertEquals(new PlayerRating(playerId, NODEBUFF, 1000, 0, 0, updatedAt), saved);
        assertEquals(saved, repository.find(playerId, NODEBUFF).orElseThrow());
    }

    @Test
    void ratingForQueueRejectsInvalidDefaultRatingsBeforeRepositoryAccess() {
        ThrowingPlayerRatingRepository repository = new ThrowingPlayerRatingRepository();
        RatingService service = new RatingService(repository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ratingForQueue(player("invalid-default"), NODEBUFF, 0));
    }

    @Test
    void rankedDecisiveWinAtEqualRatingsUpdatesWinnerAndLoserWithEloDelta() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId winnerId = player("equal-winner");
        PlayerId loserId = player("equal-loser");
        repository.upsert(new PlayerRating(winnerId, NODEBUFF, 1000, 0, 0, Instant.parse("2026-05-01T10:00:00Z")));
        repository.upsert(new PlayerRating(loserId, NODEBUFF, 1000, 0, 0, Instant.parse("2026-05-01T10:00:00Z")));

        RatingProgression progression = service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.win(winnerId, loserId),
                        winnerId,
                        loserId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .orElseThrow();

        assertEquals(new PlayerRating(winnerId, NODEBUFF, 1016, 1, 0, UPDATED_AT), progression.winner());
        assertEquals(new PlayerRating(loserId, NODEBUFF, 984, 0, 1, UPDATED_AT), progression.loser());
    }

    @Test
    void largeUnderdogRankedWinProducesALargerGainThanAnEvenMatch() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId underdogId = player("underdog");
        PlayerId favoriteId = player("favorite");
        repository.upsert(new PlayerRating(underdogId, NODEBUFF, 1000, 0, 0, Instant.parse("2026-05-01T10:00:00Z")));
        repository.upsert(new PlayerRating(favoriteId, NODEBUFF, 1400, 0, 0, Instant.parse("2026-05-01T10:00:00Z")));

        RatingProgression progression = service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.win(underdogId, favoriteId),
                        underdogId,
                        favoriteId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .orElseThrow();

        assertEquals(1029, progression.winner().rating());
        assertEquals(1371, progression.loser().rating());
    }

    @Test
    void firstRankedSettlementWithoutExistingRowsFallsBackToConfiguredBaseRating() {
        RatingService service = new RatingService(new FakePlayerRatingRepository());
        PlayerId winnerId = player("new-ranked-winner");
        PlayerId loserId = player("new-ranked-loser");

        RatingProgression progression = service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.forfeit(winnerId, loserId),
                        winnerId,
                        loserId,
                        NODEBUFF,
                        1200,
                        UPDATED_AT)
                .orElseThrow();

        assertEquals(new PlayerRating(winnerId, NODEBUFF, 1216, 1, 0, UPDATED_AT), progression.winner());
        assertEquals(new PlayerRating(loserId, NODEBUFF, 1184, 0, 1, UPDATED_AT), progression.loser());
    }

    @Test
    void nonDecisiveRankedOutcomesProduceNoRatingUpdates() {
        RatingService service = new RatingService(new FakePlayerRatingRepository());

        assertFalse(service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.timeout(),
                        player("timeout-one"),
                        player("timeout-two"),
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .isPresent());
        assertFalse(service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.shutdown(),
                        player("shutdown-one"),
                        player("shutdown-two"),
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .isPresent());
    }

    @Test
    void decisiveRankedUpdatesStillApplyMinimumOnePointSwingAndRatingFloor() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId favoriteId = player("favorite-floor");
        PlayerId challengerId = player("challenger-floor");
        repository.upsert(new PlayerRating(favoriteId, NODEBUFF, 3000, 100, 0, Instant.parse("2026-05-01T10:00:00Z")));
        repository.upsert(new PlayerRating(challengerId, NODEBUFF, 2, 0, 100, Instant.parse("2026-05-01T10:00:00Z")));

        RatingProgression progression = service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.win(favoriteId, challengerId),
                        favoriteId,
                        challengerId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .orElseThrow();

        assertEquals(3001, progression.winner().rating());
        assertEquals(1, progression.loser().rating());
    }

    @Test
    void progressionFailsFastWhenUsingQueueOnlyRatingStore() {
        RatingService service = RatingService.fromQueueRatingRepository(new FakeQueueRatingRepository());
        PlayerId winnerId = player("queue-store-winner");
        PlayerId loserId = player("queue-store-loser");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.win(winnerId, loserId),
                        winnerId,
                        loserId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT));

        assertEquals("queue-only rating store does not support rating progression", failure.getMessage());
    }

    @Test
    void queueOnlyRatingStoreTreatsNonProgressionSettlementsAsNoOps() {
        RatingService service = RatingService.fromQueueRatingRepository(new FakeQueueRatingRepository());
        PlayerId playerOneId = player("queue-noop-one");
        PlayerId playerTwoId = player("queue-noop-two");

        assertFalse(service.progression(
                        MatchOrigin.DIRECT_DUEL,
                        MatchOutcome.win(playerOneId, playerTwoId),
                        playerOneId,
                        playerTwoId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .isPresent());
        assertFalse(service.progression(
                        MatchOrigin.QUEUE_UNRANKED,
                        MatchOutcome.forfeit(playerOneId, playerTwoId),
                        playerOneId,
                        playerTwoId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .isPresent());
        assertFalse(service.progression(
                        MatchOrigin.QUEUE_RANKED,
                        MatchOutcome.timeout(),
                        playerOneId,
                        playerTwoId,
                        NODEBUFF,
                        1000,
                        UPDATED_AT)
                .isPresent());
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
        public java.util.List<PlayerRating> findByPlayer(PlayerId playerId) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void replaceAllForPlayer(PlayerId playerId, java.util.List<PlayerRating> replacementRatings) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }
    }

    private static final class ThrowingPlayerRatingRepository implements PlayerRatingRepository {

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }

        @Override
        public java.util.List<PlayerRating> findByPlayer(PlayerId playerId) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }

        @Override
        public void replaceAllForPlayer(PlayerId playerId, java.util.List<PlayerRating> replacementRatings) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }

        @Override
        public void upsert(PlayerRating rating) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }
    }

    private static final class FakeQueueRatingRepository implements QueueRatingRepository {

        @Override
        public int rating(PlayerId playerId, KitId kitId, int defaultRating) {
            return defaultRating;
        }

        @Override
        public void save(PlayerId playerId, KitId kitId, int rating) {
            throw new AssertionError("progression should fail before queue rating writes");
        }
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
        private RatingKey {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(kitId, "kitId");
        }
    }
}
