package io.github.xreatlabz.revprac.domain.queues;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;

public record QueueTicket(
        QueueTicketId id,
        PlayerId playerId,
        QueueKey key,
        long joinedAtTick,
        int searchRating,
        QueueTicketState state) {

    public QueueTicket {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        if (joinedAtTick < 0) {
            throw new IllegalArgumentException("joinedAtTick must be non-negative");
        }
        if (key.mode() == QueueMode.RANKED) {
            if (searchRating <= 0) {
                throw new IllegalArgumentException("searchRating must be positive for ranked tickets");
            }
        } else {
            searchRating = 0;
        }
    }

    public QueueTicket markPairing() {
        return transition(QueueTicketState.SEARCHING, QueueTicketState.PAIRING);
    }

    public QueueTicket markMatched() {
        return transition(QueueTicketState.PAIRING, QueueTicketState.MATCHED);
    }

    public QueueTicket cancel() {
        return transition(QueueTicketState.SEARCHING, QueueTicketState.CANCELLED);
    }

    public QueueTicket expire() {
        return transition(QueueTicketState.SEARCHING, QueueTicketState.EXPIRED);
    }

    private QueueTicket transition(QueueTicketState expectedState, QueueTicketState nextState) {
        if (state != expectedState) {
            throw new IllegalStateException("cannot transition queue ticket from " + state + " to " + nextState);
        }
        return new QueueTicket(id, playerId, key, joinedAtTick, searchRating, nextState);
    }
}
