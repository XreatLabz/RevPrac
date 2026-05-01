package io.github.xreatlabz.revprac.application.config;

public record MatchConfig(
        int duelRequestExpirySeconds,
        int countdownTicks,
        int maxDurationTicks,
        boolean spectatorsEnabled) {

    public static final int DEFAULT_DUEL_REQUEST_EXPIRY_SECONDS = 30;
    public static final int DEFAULT_COUNTDOWN_TICKS = 100;
    public static final int DEFAULT_MAX_DURATION_TICKS = 12000;
    public static final boolean DEFAULT_SPECTATORS_ENABLED = true;

    public MatchConfig {
        if (duelRequestExpirySeconds <= 0) {
            throw new IllegalArgumentException("duelRequestExpirySeconds must be positive");
        }
        if (countdownTicks <= 0) {
            throw new IllegalArgumentException("countdownTicks must be positive");
        }
        if (maxDurationTicks <= 0) {
            throw new IllegalArgumentException("maxDurationTicks must be positive");
        }
    }

    public static MatchConfig defaults() {
        return new MatchConfig(
                DEFAULT_DUEL_REQUEST_EXPIRY_SECONDS,
                DEFAULT_COUNTDOWN_TICKS,
                DEFAULT_MAX_DURATION_TICKS,
                DEFAULT_SPECTATORS_ENABLED);
    }
}
