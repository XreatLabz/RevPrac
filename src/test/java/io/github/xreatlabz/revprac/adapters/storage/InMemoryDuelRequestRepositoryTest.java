package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.DuelRequestRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InMemoryDuelRequestRepositoryTest {

    @Test
    void createFindPairLookupSaveDeleteAndSnapshotsBehaveAtomically() {
        DuelRequestRepository repository = new InMemoryDuelRequestRepository();
        DuelRequest pending = request("first", "requester-one", "target-one", DuelRequestState.PENDING);

        assertTrue(repository.create(pending));
        assertFalse(repository.create(request("first", "requester-one", "target-one", DuelRequestState.PENDING)));
        assertEquals(pending, repository.find(pending.id()).orElseThrow());
        assertEquals(pending, repository.findByPlayers(pending.requesterId(), pending.targetId()).orElseThrow());

        Set<DuelRequest> firstSnapshot = Set.copyOf(repository.findAll());
        assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);

        DuelRequest accepted = pending.accept();
        repository.save(accepted);

        assertEquals(accepted, repository.find(pending.id()).orElseThrow());
        assertEquals(
                DuelRequestState.PENDING,
                firstSnapshot.iterator().next().state(),
                "Earlier snapshots must stay immutable after later saves");

        repository.delete(pending.id());
        assertTrue(repository.find(pending.id()).isEmpty());
        assertTrue(repository.findByPlayers(pending.requesterId(), pending.targetId()).isEmpty());
    }

    @Test
    void pendingPairLookupIgnoresRetainedAcceptedHistory() {
        DuelRequestRepository repository = new InMemoryDuelRequestRepository();
        DuelRequest accepted = request("accepted", "repeat-requester", "repeat-target", DuelRequestState.PENDING)
                .accept();
        DuelRequest pending = request("pending", "repeat-requester", "repeat-target", DuelRequestState.PENDING);

        assertTrue(repository.create(accepted));
        assertTrue(repository.create(pending));

        assertEquals(pending, repository.findPendingByPlayers(pending.requesterId(), pending.targetId()).orElseThrow());
    }

    @Test
    void pairLookupReturnsNewestRequestWhenAcceptedHistoryAndPendingCoexist() {
        DuelRequestRepository repository = new InMemoryDuelRequestRepository();
        DuelRequest acceptedHistory = request(
                        "accepted-history",
                        "deterministic-requester",
                        "deterministic-target",
                        DuelRequestState.PENDING,
                        Instant.parse("2026-05-01T12:00:00Z"))
                .accept();
        DuelRequest newerPending = request(
                "newer-pending",
                "deterministic-requester",
                "deterministic-target",
                DuelRequestState.PENDING,
                Instant.parse("2026-05-01T12:01:00Z"));

        assertTrue(repository.create(acceptedHistory));
        assertTrue(repository.create(newerPending));

        assertEquals(
                newerPending,
                repository.findByPlayers(newerPending.requesterId(), newerPending.targetId()).orElseThrow());
        assertEquals(
                newerPending,
                repository.findPendingByPlayers(newerPending.requesterId(), newerPending.targetId()).orElseThrow());
    }

    private static DuelRequest request(String seed, String requesterSeed, String targetSeed, DuelRequestState state) {
        return request(seed, requesterSeed, targetSeed, state, Instant.parse("2026-05-01T12:00:00Z"));
    }

    private static DuelRequest request(
            String seed, String requesterSeed, String targetSeed, DuelRequestState state, Instant createdAt) {
        return new DuelRequest(
                new DuelRequestId(UUID.nameUUIDFromBytes(seed.getBytes())),
                player(requesterSeed),
                player(targetSeed),
                new ArenaId("arena-" + seed),
                new KitId("kit-" + seed),
                state,
                createdAt,
                createdAt.plusSeconds(30));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
