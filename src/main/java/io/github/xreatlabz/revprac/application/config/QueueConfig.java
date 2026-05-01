package io.github.xreatlabz.revprac.application.config;

import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import java.util.List;
import java.util.Objects;

public record QueueConfig(
        int matchmakingPeriodTicks,
        int rankedBaseRating,
        int ticksPerSecond,
        List<MatchmakingWindowPolicy.WindowStep> rankedWindows) {

    public static final int DEFAULT_MATCHMAKING_PERIOD_TICKS = 20;
    public static final int DEFAULT_RANKED_BASE_RATING = 1000;
    public static final int DEFAULT_TICKS_PER_SECOND = 20;

    public QueueConfig {
        if (matchmakingPeriodTicks <= 0) {
            throw new IllegalArgumentException("matchmakingPeriodTicks must be positive");
        }
        if (rankedBaseRating <= 0) {
            throw new IllegalArgumentException("rankedBaseRating must be positive");
        }
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticksPerSecond must be positive");
        }
        Objects.requireNonNull(rankedWindows, "rankedWindows");
        rankedWindows = new MatchmakingWindowPolicy(rankedWindows).steps();
    }

    public static QueueConfig defaults() {
        return new QueueConfig(
                DEFAULT_MATCHMAKING_PERIOD_TICKS,
                DEFAULT_RANKED_BASE_RATING,
                DEFAULT_TICKS_PER_SECOND,
                MatchmakingWindowPolicy.defaults().steps());
    }
}
