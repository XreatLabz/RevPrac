package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.DuelRequestRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDuelRequestRepository implements DuelRequestRepository {

    private static final Comparator<DuelRequest> REQUEST_LOOKUP_ORDER = Comparator.comparing(DuelRequest::createdAt)
            .thenComparing(request -> request.id().value());

    private final ConcurrentMap<DuelRequestId, DuelRequest> requests = new ConcurrentHashMap<>();

    @Override
    public Optional<DuelRequest> find(DuelRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return Optional.ofNullable(requests.get(requestId));
    }

    @Override
    public Optional<DuelRequest> findByPlayers(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        return requests.values().stream()
                .filter(request -> request.requesterId().equals(requesterId) && request.targetId().equals(targetId))
                .max(REQUEST_LOOKUP_ORDER);
    }

    @Override
    public Optional<DuelRequest> findPendingByPlayers(PlayerId requesterId, PlayerId targetId) {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        return requests.values().stream()
                .filter(request -> request.requesterId().equals(requesterId) && request.targetId().equals(targetId))
                .filter(request -> request.state() == DuelRequestState.PENDING)
                .findFirst();
    }

    @Override
    public Collection<DuelRequest> findAll() {
        return List.copyOf(requests.values());
    }

    @Override
    public boolean create(DuelRequest duelRequest) {
        Objects.requireNonNull(duelRequest, "duelRequest");
        return requests.putIfAbsent(duelRequest.id(), duelRequest) == null;
    }

    @Override
    public void save(DuelRequest duelRequest) {
        Objects.requireNonNull(duelRequest, "duelRequest");
        requests.put(duelRequest.id(), duelRequest);
    }

    @Override
    public void delete(DuelRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        requests.remove(requestId);
    }
}
