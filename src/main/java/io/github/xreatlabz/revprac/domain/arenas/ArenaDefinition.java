package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Objects;

public record ArenaDefinition(
        ArenaId id,
        String displayName,
        ArenaCuboid bounds,
        ArenaSpawnPoint spawnOne,
        ArenaSpawnPoint spawnTwo,
        boolean enabled) {

    public ArenaDefinition {
        Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(spawnOne, "spawnOne");
        Objects.requireNonNull(spawnTwo, "spawnTwo");
        requireMatchingWorld(bounds, spawnOne, "spawnOne");
        requireMatchingWorld(bounds, spawnTwo, "spawnTwo");
        requireContained(bounds, spawnOne, "spawnOne");
        requireContained(bounds, spawnTwo, "spawnTwo");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static void requireMatchingWorld(ArenaCuboid bounds, ArenaSpawnPoint spawnPoint, String fieldName) {
        if (!bounds.worldKey().equals(spawnPoint.worldKey())) {
            throw new IllegalArgumentException(fieldName + " world must match arena world");
        }
    }

    private static void requireContained(ArenaCuboid bounds, ArenaSpawnPoint spawnPoint, String fieldName) {
        if (!bounds.contains(spawnPoint)) {
            throw new IllegalArgumentException(fieldName + " must be contained within arena bounds");
        }
    }
}
