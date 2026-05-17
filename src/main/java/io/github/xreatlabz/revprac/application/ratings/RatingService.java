package io.github.xreatlabz.revprac.application.ratings;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class RatingService {

    private static final int ELO_K_FACTOR = 32;
    private static final int MINIMUM_RATING = 1;
    private static final String QUEUE_ONLY_PROGRESSION_MESSAGE =
            "queue-only rating store does not support rating progression";

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

    public Optional<RatingProgression> progression(
            MatchOrigin origin,
            MatchOutcome outcome,
            PlayerId playerOneId,
            PlayerId playerTwoId,
            KitId kitId,
            int defaultRating,
            Instant updatedAt) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(playerOneId, "playerOneId");
        Objects.requireNonNull(playerTwoId, "playerTwoId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (defaultRating <= 0) {
            throw new IllegalArgumentException("defaultRating must be positive");
        }
        if (origin != MatchOrigin.QUEUE_RANKED) {
            return Optional.empty();
        }
        if (outcome.reason() != MatchEndReason.WIN && outcome.reason() != MatchEndReason.FORFEIT) {
            return Optional.empty();
        }
        ratingStore.requireProgressionSupport();

        PlayerId winnerId = outcome.winnerId().orElseThrow();
        PlayerId loserId = outcome.loserId().orElseThrow();
        if (!isParticipant(playerOneId, playerTwoId, winnerId) || !isParticipant(playerOneId, playerTwoId, loserId)) {
            throw new IllegalArgumentException("winner and loser must be match participants");
        }

        RatingSnapshot winnerCurrent = ratingStore.currentRating(winnerId, kitId, defaultRating);
        RatingSnapshot loserCurrent = ratingStore.currentRating(loserId, kitId, defaultRating);
        int winnerDelta = ratingDelta(winnerCurrent.rating(), loserCurrent.rating());
        int loserDelta = -winnerDelta;
        PlayerRating winnerBefore = new PlayerRating(
                winnerId,
                kitId,
                winnerCurrent.rating(),
                winnerCurrent.wins(),
                winnerCurrent.losses(),
                updatedAt);
        PlayerRating winner = new PlayerRating(
                winnerId,
                kitId,
                winnerCurrent.rating() + winnerDelta,
                winnerCurrent.wins() + 1,
                winnerCurrent.losses(),
                updatedAt);
        PlayerRating loserBefore = new PlayerRating(
                loserId,
                kitId,
                loserCurrent.rating(),
                loserCurrent.wins(),
                loserCurrent.losses(),
                updatedAt);
        PlayerRating loser = new PlayerRating(
                loserId,
                kitId,
                Math.max(MINIMUM_RATING, loserCurrent.rating() + loserDelta),
                loserCurrent.wins(),
                loserCurrent.losses() + 1,
                updatedAt);
        return Optional.of(new RatingProgression(winnerBefore, winner, loserBefore, loser));
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

        RatingSnapshot currentRating(PlayerId playerId, KitId kitId, int defaultRating);

        void save(PlayerRating playerRating);

        default void requireProgressionSupport() {
        }
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
        public RatingSnapshot currentRating(PlayerId playerId, KitId kitId, int defaultRating) {
            return playerRatingRepository.find(playerId, kitId)
                    .map(value -> new RatingSnapshot(value.rating(), value.wins(), value.losses()))
                    .orElseGet(() -> new RatingSnapshot(defaultRating, 0, 0));
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
        public RatingSnapshot currentRating(PlayerId playerId, KitId kitId, int defaultRating) {
            return new RatingSnapshot(queueRatingRepository.rating(playerId, kitId, defaultRating), 0, 0);
        }

        @Override
        public void save(PlayerRating playerRating) {
            queueRatingRepository.save(playerRating.playerId(), playerRating.kitId(), playerRating.rating());
        }

        @Override
        public void requireProgressionSupport() {
            throw new IllegalStateException(QUEUE_ONLY_PROGRESSION_MESSAGE);
        }
    }

    private static int ratingDelta(int currentRating, int opponentRating) {
        double expected = 1.0d / (1.0d + Math.pow(10.0d, (opponentRating - currentRating) / 400.0d));
        int delta = (int) Math.round(ELO_K_FACTOR * (1.0d - expected));
        return Math.max(1, delta);
    }

    private static boolean isParticipant(PlayerId playerOneId, PlayerId playerTwoId, PlayerId playerId) {
        return playerOneId.equals(playerId) || playerTwoId.equals(playerId);
    }

    private record RatingSnapshot(int rating, int wins, int losses) {
        private RatingSnapshot {
            if (rating <= 0) {
                throw new IllegalArgumentException("rating must be positive");
            }
            if (wins < 0) {
                throw new IllegalArgumentException("wins must be non-negative");
            }
            if (losses < 0) {
                throw new IllegalArgumentException("losses must be non-negative");
            }
        }
    }
}
