package io.github.xreatlabz.revprac.application.ratings;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RatingService {

    private final RatingStore ratingStore;

    public RatingService(PlayerRatingRepository playerRatingRepository) {
        this(new PersistentRatingStore(playerRatingRepository));
    }

    private RatingService(RatingStore ratingStore) {
        this.ratingStore = Objects.requireNonNull(ratingStore, "ratingStore");
    }

    public static RatingService fromQueueRatingRepository(QueueRatingRepository queueRatingRepository) {
        return new RatingService(new QueueRatingStore(queueRatingRepository));
    }

    public int ratingForQueue(PlayerId playerId, KitId kitId, int defaultRating) {
        return queueJoinRating(playerId, kitId, defaultRating).rating();
    }

    public QueueJoinRating queueJoinRating(PlayerId playerId, KitId kitId, int defaultRating) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        if (defaultRating <= 0) {
            throw new IllegalArgumentException("defaultRating must be positive");
        }
        return ratingStore.queueJoinRating(playerId, kitId, defaultRating);
    }

    public PlayerRating saveSeed(PlayerId playerId, KitId kitId, int rating, Instant updatedAt) {
        PlayerRating playerRating = new PlayerRating(playerId, kitId, rating, 0, 0, updatedAt);
        ratingStore.save(playerRating);
        return playerRating;
    }

    public record QueueJoinRating(int rating, boolean durableSeedRequired) {
        public QueueJoinRating {
            if (rating <= 0) {
                throw new IllegalArgumentException("rating must be positive");
            }
        }
    }

    private interface RatingStore {

        QueueJoinRating queueJoinRating(PlayerId playerId, KitId kitId, int defaultRating);

        void save(PlayerRating playerRating);
    }

    private static final class PersistentRatingStore implements RatingStore {
        private final PlayerRatingRepository playerRatingRepository;

        private PersistentRatingStore(PlayerRatingRepository playerRatingRepository) {
            this.playerRatingRepository = Objects.requireNonNull(playerRatingRepository, "playerRatingRepository");
        }

        @Override
        public QueueJoinRating queueJoinRating(PlayerId playerId, KitId kitId, int defaultRating) {
            Optional<PlayerRating> rating = playerRatingRepository.find(playerId, kitId);
            return rating.map(value -> new QueueJoinRating(value.rating(), false))
                    .orElseGet(() -> new QueueJoinRating(defaultRating, true));
        }

        @Override
        public void save(PlayerRating playerRating) {
            playerRatingRepository.upsert(playerRating);
        }
    }

    private static final class QueueRatingStore implements RatingStore {
        private final QueueRatingRepository queueRatingRepository;

        private QueueRatingStore(QueueRatingRepository queueRatingRepository) {
            this.queueRatingRepository = Objects.requireNonNull(queueRatingRepository, "queueRatingRepository");
        }

        @Override
        public QueueJoinRating queueJoinRating(PlayerId playerId, KitId kitId, int defaultRating) {
            return new QueueJoinRating(queueRatingRepository.rating(playerId, kitId, defaultRating), false);
        }

        @Override
        public void save(PlayerRating playerRating) {
            queueRatingRepository.save(playerRating.playerId(), playerRating.kitId(), playerRating.rating());
        }
    }
}
