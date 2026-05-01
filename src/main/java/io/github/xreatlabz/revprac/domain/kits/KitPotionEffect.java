package io.github.xreatlabz.revprac.domain.kits;

import java.util.Objects;

public record KitPotionEffect(
        String effectKey,
        int durationTicks,
        int amplifier,
        boolean ambient,
        boolean particles,
        boolean icon) {

    public KitPotionEffect {
        effectKey = requireNonBlank(effectKey, "effectKey");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be greater than or equal to 0");
        }
        if (amplifier < 0) {
            throw new IllegalArgumentException("amplifier must be greater than or equal to 0");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
