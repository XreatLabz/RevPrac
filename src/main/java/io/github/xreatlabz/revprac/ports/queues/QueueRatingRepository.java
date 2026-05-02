package io.github.xreatlabz.revprac.ports.queues;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;

public interface QueueRatingRepository {

    int rating(PlayerId playerId, KitId kitId, int defaultRating);

    void save(PlayerId playerId, KitId kitId, int rating);
}
