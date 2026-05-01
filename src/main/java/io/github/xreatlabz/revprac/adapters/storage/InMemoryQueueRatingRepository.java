package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.queues.QueueRatingRepository;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryQueueRatingRepository implements QueueRatingRepository {

    private final ConcurrentMap<RatingKey, Integer> ratings = new ConcurrentHashMap<>();

    @Override
    public int rating(PlayerId playerId, KitId kitId, int defaultRating) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        return ratings.getOrDefault(new RatingKey(playerId, kitId), defaultRating);
    }

    @Override
    public void save(PlayerId playerId, KitId kitId, int rating) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        if (rating <= 0) {
            throw new IllegalArgumentException("rating must be positive");
        }
        ratings.put(new RatingKey(playerId, kitId), rating);
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
    }
}
