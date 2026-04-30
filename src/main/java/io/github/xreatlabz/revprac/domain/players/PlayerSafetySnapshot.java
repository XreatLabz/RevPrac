package io.github.xreatlabz.revprac.domain.players;

import java.util.Objects;

public record PlayerSafetySnapshot(
        LocationSnapshot location,
        InventorySnapshot inventory,
        PlayerStatusSnapshot status) {

    public PlayerSafetySnapshot {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(status, "status");
    }
}
