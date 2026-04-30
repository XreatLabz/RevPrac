package io.github.xreatlabz.revprac.domain.kits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record KitDefinition(
        KitId id,
        String displayName,
        KitInventory inventory,
        List<KitPotionEffect> potionEffects,
        KitRules rules,
        boolean enabled) {

    public KitDefinition {
        Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(inventory, "inventory");
        potionEffects = immutableEffectCopy(potionEffects);
        Objects.requireNonNull(rules, "rules");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static List<KitPotionEffect> immutableEffectCopy(List<KitPotionEffect> effects) {
        Objects.requireNonNull(effects, "potionEffects");
        return Collections.unmodifiableList(new ArrayList<>(effects));
    }
}
