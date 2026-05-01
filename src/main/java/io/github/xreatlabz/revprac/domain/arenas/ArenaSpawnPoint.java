package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Objects;

public record ArenaSpawnPoint(String worldKey, double x, double y, double z, float yaw, float pitch) {

    public ArenaSpawnPoint {
        worldKey = requireNonBlank(worldKey, "worldKey");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }

    private static void requireFinite(float value, String fieldName) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
