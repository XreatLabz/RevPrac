package io.github.xreatlabz.revprac.domain.queues;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import java.util.Objects;

public record QueuedMatchAssignment(
        QueueTicket first,
        QueueTicket second,
        QueueMode mode,
        KitId kitId,
        int ratingDelta) {

    public QueuedMatchAssignment {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(kitId, "kitId");
        if (ratingDelta < 0) {
            throw new IllegalArgumentException("ratingDelta must be non-negative");
        }
        if (first.playerId().equals(second.playerId())) {
            throw new IllegalArgumentException("queued match assignment requires distinct players");
        }
        if (!first.key().equals(second.key())) {
            throw new IllegalArgumentException("queued match assignment requires matching queue keys");
        }
        if (first.state() != QueueTicketState.PAIRING || second.state() != QueueTicketState.PAIRING) {
            throw new IllegalArgumentException("queued match assignment requires pairing tickets");
        }
        if (first.key().mode() != mode) {
            throw new IllegalArgumentException("mode must match queue key mode");
        }
        if (!first.key().kitId().equals(kitId)) {
            throw new IllegalArgumentException("kitId must match queue key kit");
        }
    }
}
