package io.github.xreatlabz.revprac.ports.seasons;

import io.github.xreatlabz.revprac.domain.seasons.Season;
import io.github.xreatlabz.revprac.domain.seasons.SeasonId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeasonRepository {

    Optional<Season> findActive();

    List<Season> findAll();

    void create(SeasonId seasonId, Instant createdAt);

    Season activate(SeasonId seasonId, Instant activatedAt);
}
