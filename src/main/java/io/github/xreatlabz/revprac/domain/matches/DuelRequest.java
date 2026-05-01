package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.Objects;

public record DuelRequest(
        DuelRequestId id,
        PlayerId requesterId,
        PlayerId targetId,
        ArenaId arenaId,
        KitId kitId,
        DuelRequestState state,
        Instant createdAt,
        Instant expiresAt) {

    public DuelRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");

        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("duel request must target a different player");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public DuelRequest accept() {
        return transitionTo(DuelRequestState.ACCEPTED);
    }

    public DuelRequest decline() {
        return transitionTo(DuelRequestState.DECLINED);
    }

    public DuelRequest cancel() {
        return transitionTo(DuelRequestState.CANCELLED);
    }

    public DuelRequest expire() {
        return transitionTo(DuelRequestState.EXPIRED);
    }

    private DuelRequest transitionTo(DuelRequestState nextState) {
        if (state != DuelRequestState.PENDING) {
            throw new IllegalStateException("duel request is no longer pending");
        }
        return new DuelRequest(id, requesterId, targetId, arenaId, kitId, nextState, createdAt, expiresAt);
    }
}
