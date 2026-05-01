package io.github.xreatlabz.revprac.domain.queues;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import java.util.Objects;

public record QueueKey(QueueMode mode, KitId kitId) {

    public QueueKey {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(kitId, "kitId");
    }
}
