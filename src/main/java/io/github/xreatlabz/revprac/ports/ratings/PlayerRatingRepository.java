package io.github.xreatlabz.revprac.ports.ratings;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import java.util.Optional;

public interface PlayerRatingRepository {

    Optional<PlayerRating> find(PlayerId playerId, KitId kitId);

    void upsert(PlayerRating rating);
}
