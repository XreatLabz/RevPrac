package io.github.xreatlabz.revprac.domain.kits;

public record KitRules(
        boolean allowBuilding,
        boolean allowHunger,
        boolean allowNaturalRegeneration,
        boolean ranked) {
}
