package io.github.xreatlabz.revprac.application.ratings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class RatingServiceTest {

    @Test
    void ratingForQueueFallsBackToTheDefaultRatingWhenNoPersistentRatingExists() {
        RatingService service = new RatingService(new FakePlayerRatingRepository());

        int rating = service.ratingForQueue(player("missing-rating"), new KitId("nodebuff"), 1000);

        assertEquals(1000, rating);
    }

    @Test
    void ratingForQueueReturnsThePersistedPlayerKitRatingWhenItExists() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId playerId = player("persisted-rating");
        KitId kitId = new KitId("nodebuff");
        repository.upsert(new PlayerRating(playerId, kitId, 1285, 4, 1, Instant.parse("2026-05-02T12:00:00Z")));

        int rating = service.ratingForQueue(playerId, kitId, 1000);

        assertEquals(1285, rating);
    }

    @Test
    void saveSeedPersistsAZeroRecordRatingSeedForThePlayerAndKit() {
        FakePlayerRatingRepository repository = new FakePlayerRatingRepository();
        RatingService service = new RatingService(repository);
        PlayerId playerId = player("seed-rating");
        KitId kitId = new KitId("nodebuff");
        Instant updatedAt = Instant.parse("2026-05-02T12:30:00Z");

        PlayerRating saved = service.saveSeed(playerId, kitId, 1000, updatedAt);

        assertEquals(new PlayerRating(playerId, kitId, 1000, 0, 0, updatedAt), saved);
        assertEquals(saved, repository.find(playerId, kitId).orElseThrow());
    }

    @Test
    void ratingForQueueRejectsInvalidDefaultRatingsBeforeRepositoryAccess() {
        ThrowingPlayerRatingRepository repository = new ThrowingPlayerRatingRepository();
        RatingService service = new RatingService(repository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ratingForQueue(player("invalid-default"), new KitId("nodebuff"), 0));
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

    private static final class ThrowingPlayerRatingRepository implements PlayerRatingRepository {

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }

        @Override
        public void upsert(PlayerRating rating) {
            throw new AssertionError("repository should not be called for invalid defaults");
        }
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
        private RatingKey {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(kitId, "kitId");
        }
    }
}
