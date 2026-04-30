package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public record PotionEffectSnapshot(
        String effectKey,
        int durationTicks,
        int amplifier,
        boolean ambient,
        boolean particles,
        boolean icon) {

    public PotionEffectSnapshot {
        Objects.requireNonNull(effectKey, "effectKey");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be non-negative");
        }
        if (amplifier < 0) {
            throw new IllegalArgumentException("amplifier must be non-negative");
        }
    }
}
