package io.github.xreatlabz.revprac.domain.queues;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MatchmakingWindowPolicy(List<WindowStep> steps) {

    public MatchmakingWindowPolicy {
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }

        List<WindowStep> validated = new ArrayList<>(steps.size());
        long previousWaitSeconds = Long.MIN_VALUE;
        for (int index = 0; index < steps.size(); index++) {
            WindowStep step = Objects.requireNonNull(steps.get(index), "steps[" + index + "]");
            if (step.waitSeconds() < 0) {
                throw new IllegalArgumentException("waitSeconds must be non-negative");
            }
            if (step.ratingWindow() <= 0) {
                throw new IllegalArgumentException("ratingWindow must be positive");
            }
            if (step.waitSeconds() <= previousWaitSeconds) {
                throw new IllegalArgumentException("waitSeconds must be strictly increasing");
            }
            previousWaitSeconds = step.waitSeconds();
            validated.add(step);
        }
        steps = List.copyOf(validated);
    }

    public static MatchmakingWindowPolicy defaults() {
        return new MatchmakingWindowPolicy(List.of(
                new WindowStep(0, 50),
                new WindowStep(10, 100),
                new WindowStep(20, 150),
                new WindowStep(30, 250),
                new WindowStep(45, 400)));
    }

    public int windowForWaitSeconds(long waitSeconds) {
        if (waitSeconds < 0) {
            throw new IllegalArgumentException("waitSeconds must be non-negative");
        }

        int window = steps.getFirst().ratingWindow();
        for (WindowStep step : steps) {
            if (waitSeconds < step.waitSeconds()) {
                break;
            }
            window = step.ratingWindow();
        }
        return window;
    }

    public boolean isCompatible(QueueTicket anchor, QueueTicket candidate, long currentTick, long ticksPerSecond) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(candidate, "candidate");
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticksPerSecond must be positive");
        }
        if (!anchor.key().equals(candidate.key())) {
            return false;
        }
        if (anchor.playerId().equals(candidate.playerId())) {
            return false;
        }
        if (anchor.key().mode() == QueueMode.UNRANKED) {
            return true;
        }

        long waitedTicks = Math.max(0L, currentTick - anchor.joinedAtTick());
        long waitedSeconds = waitedTicks / ticksPerSecond;
        int ratingWindow = windowForWaitSeconds(waitedSeconds);
        int ratingDelta = Math.abs(anchor.searchRating() - candidate.searchRating());
        return ratingDelta <= ratingWindow;
    }

    public record WindowStep(long waitSeconds, int ratingWindow) {
    }
}
