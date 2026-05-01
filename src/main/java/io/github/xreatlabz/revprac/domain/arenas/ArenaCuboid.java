package io.github.xreatlabz.revprac.domain.arenas;

import java.util.Objects;

public record ArenaCuboid(String worldKey, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public ArenaCuboid {
        worldKey = requireNonBlank(worldKey, "worldKey");
        if (minX > maxX) {
            throw new IllegalArgumentException("minX must be less than or equal to maxX");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must be less than or equal to maxY");
        }
        if (minZ > maxZ) {
            throw new IllegalArgumentException("minZ must be less than or equal to maxZ");
        }
    }

    public boolean contains(ArenaSpawnPoint spawnPoint) {
        Objects.requireNonNull(spawnPoint, "spawnPoint");
        if (!worldKey.equals(spawnPoint.worldKey())) {
            return false;
        }

        int blockX = (int) Math.floor(spawnPoint.x());
        int blockY = (int) Math.floor(spawnPoint.y());
        int blockZ = (int) Math.floor(spawnPoint.z());
        return blockX >= minX && blockX <= maxX
                && blockY >= minY && blockY <= maxY
                && blockZ >= minZ && blockZ <= maxZ;
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
