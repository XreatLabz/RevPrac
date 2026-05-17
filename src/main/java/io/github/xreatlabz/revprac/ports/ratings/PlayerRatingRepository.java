package io.github.xreatlabz.revprac.ports.ratings;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import java.util.List;
import java.util.Optional;

public interface PlayerRatingRepository {

    Optional<PlayerRating> find(PlayerId playerId, KitId kitId);

    List<PlayerRating> findByPlayer(PlayerId playerId);

    void replaceAllForPlayer(PlayerId playerId, List<PlayerRating> ratings);

    void upsert(PlayerRating rating);
}
