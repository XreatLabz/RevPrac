package io.github.xreatlabz.revprac.domain.queues;

import java.util.Objects;
import java.util.UUID;

public record QueueTicketId(UUID value) {

    public QueueTicketId {
        Objects.requireNonNull(value, "value");
    }
}
