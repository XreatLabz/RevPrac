package io.github.xreatlabz.revprac.domain.players;

import java.util.List;
import java.util.Objects;

public record PlayerStatusSnapshot(
        String gameMode,
        double health,
        int foodLevel,
        float saturation,
        float expProgress,
        int level,
        boolean allowFlight,
        boolean flying,
        List<PotionEffectSnapshot> potionEffects) {

    public PlayerStatusSnapshot {
        Objects.requireNonNull(gameMode, "gameMode");
        if (health < 0.0d) {
            throw new IllegalArgumentException("health must be non-negative");
        }
        if (foodLevel < 0 || foodLevel > 20) {
            throw new IllegalArgumentException("foodLevel must be between 0 and 20");
        }
        if (saturation < 0.0f) {
            throw new IllegalArgumentException("saturation must be non-negative");
        }
        if (expProgress < 0.0f || expProgress > 1.0f) {
            throw new IllegalArgumentException("expProgress must be between 0.0 and 1.0");
        }
        if (level < 0) {
            throw new IllegalArgumentException("level must be non-negative");
        }
        potionEffects = List.copyOf(Objects.requireNonNull(potionEffects, "potionEffects"));
    }
}
