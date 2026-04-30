package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public record LocationSnapshot(String worldKey, double x, double y, double z, float yaw, float pitch) {

    public LocationSnapshot {
        Objects.requireNonNull(worldKey, "worldKey");
    }
}
