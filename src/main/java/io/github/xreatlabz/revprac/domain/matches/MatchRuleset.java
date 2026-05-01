package io.github.xreatlabz.revprac.domain.matches;

public record MatchRuleset(int countdownTicks, int maxDurationTicks, boolean spectatorsEnabled) {

    public MatchRuleset {
        if (countdownTicks <= 0) {
            throw new IllegalArgumentException("countdownTicks must be positive");
        }
        if (maxDurationTicks <= 0) {
            throw new IllegalArgumentException("maxDurationTicks must be positive");
        }
    }
}
