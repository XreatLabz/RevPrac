package io.github.xreatlabz.revprac.application.seasons;

import io.github.xreatlabz.revprac.domain.seasons.Season;
import io.github.xreatlabz.revprac.domain.seasons.SeasonId;
import io.github.xreatlabz.revprac.ports.seasons.SeasonRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class SeasonService {

    private final SeasonRepository seasonRepository;
    private final Clock clock;

    public SeasonService(SeasonRepository seasonRepository) {
        this(seasonRepository, Clock.systemUTC());
    }

    public SeasonService(SeasonRepository seasonRepository, Clock clock) {
        this.seasonRepository = Objects.requireNonNull(seasonRepository, "seasonRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Season activeSeason() {
        return seasonRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("No active season is configured"));
    }

    public String activeSeasonId() {
        return activeSeason().id().value();
    }

    public List<Season> seasons() {
        return seasonRepository.findAll();
    }

    public Season create(String seasonId) {
        SeasonId id = new SeasonId(seasonId);
        seasonRepository.create(id, clock.instant());
        return seasonRepository.findAll().stream()
                .filter(season -> season.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Season was not created: " + id.value()));
    }

    public Season activate(String seasonId) {
        return seasonRepository.activate(new SeasonId(seasonId), clock.instant());
    }
}
