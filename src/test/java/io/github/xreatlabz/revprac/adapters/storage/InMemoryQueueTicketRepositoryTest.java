package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class InMemoryQueueTicketRepositoryTest {

    @Test
    void createFindLookupsSaveDeleteAndSnapshotsBehaveAtomically() {
        QueueTicketRepository repository = new InMemoryQueueTicketRepository();
        QueueTicket created = ticket("first", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);

        assertTrue(repository.create(created));
        assertFalse(repository.create(ticket("first", "other-player", key("nodebuff"), 11L, 1000, QueueTicketState.SEARCHING)));
        assertFalse(repository.create(ticket("second", "player-one", key("nodebuff"), 12L, 1000, QueueTicketState.SEARCHING)));
        assertEquals(created, repository.find(created.id()).orElseThrow());
        assertEquals(created, repository.findByPlayer(created.playerId()).orElseThrow());
        assertEquals(Set.of(created), Set.copyOf(repository.findSearchingByKey(created.key())));

        Collection<QueueTicket> allSnapshot = repository.findAll();
        Collection<QueueTicket> searchingSnapshot = repository.findSearchingByKey(created.key());
        assertThrows(UnsupportedOperationException.class, allSnapshot::clear);
        assertThrows(UnsupportedOperationException.class, searchingSnapshot::clear);

        QueueTicket pairing = created.markPairing();
        repository.save(pairing);

        assertEquals(pairing, repository.find(created.id()).orElseThrow());
        assertEquals(
                QueueTicketState.SEARCHING,
                allSnapshot.iterator().next().state(),
                "Earlier all-ticket snapshots must not change after saves");
        assertEquals(
                Set.of(created),
                Set.copyOf(searchingSnapshot),
                "Earlier searching snapshots must not change after saves");

        repository.save(pairing.markMatched());
        QueueTicket retry = ticket("retry", "player-one", key("sumo"), 13L, 1000, QueueTicketState.SEARCHING);

        assertTrue(repository.create(retry), "terminal tickets must not block a future active ticket");
        assertEquals(retry, repository.findByPlayer(created.playerId()).orElseThrow());

        repository.delete(retry.id());

        assertTrue(repository.find(retry.id()).isEmpty());
        assertTrue(repository.findByPlayer(created.playerId()).isEmpty());

        repository.deleteByPlayer(created.playerId());

        assertTrue(repository.find(created.id()).isEmpty());
    }

    @Test
    void saveRejectsDifferentActiveTicketForTheSamePlayer() {
        QueueTicketRepository repository = new InMemoryQueueTicketRepository();
        QueueTicket existing = ticket("existing", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        QueueTicket duplicate = ticket("duplicate", "player-one", key("sumo"), 11L, 1000, QueueTicketState.SEARCHING);
        assertTrue(repository.create(existing));

        IllegalStateException duplicateActive = assertThrows(
                IllegalStateException.class,
                () -> repository.save(duplicate));

        assertEquals("player already has an active queue ticket", duplicateActive.getMessage());
        assertTrue(repository.find(duplicate.id()).isEmpty());
        assertEquals(existing, repository.findByPlayer(existing.playerId()).orElseThrow());

        QueueTicket pairing = existing.markPairing();
        repository.save(pairing);

        assertEquals(pairing, repository.find(existing.id()).orElseThrow());
    }

    @Test
    void claimPairMovesSearchingTicketsToPairingAndRestoreOnlyChangesPairingTickets() {
        QueueTicketRepository repository = new InMemoryQueueTicketRepository();
        QueueKey queueKey = key("nodebuff");
        QueueTicket first = ticket("first", "player-one", queueKey, 10L, 1100, QueueTicketState.SEARCHING);
        QueueTicket second = ticket("second", "player-two", queueKey, 12L, 1035, QueueTicketState.SEARCHING);
        assertTrue(repository.create(first));
        assertTrue(repository.create(second));

        Optional<QueuedMatchAssignment> assignment = repository.claimPair(first.id(), second.id());

        assertTrue(assignment.isPresent());
        assertEquals(QueueTicketState.PAIRING, assignment.orElseThrow().first().state());
        assertEquals(QueueTicketState.PAIRING, assignment.orElseThrow().second().state());
        assertEquals(65, assignment.orElseThrow().ratingDelta());
        assertEquals(QueueTicketState.PAIRING, repository.find(first.id()).orElseThrow().state());
        assertEquals(QueueTicketState.PAIRING, repository.find(second.id()).orElseThrow().state());
        assertTrue(repository.findSearchingByKey(queueKey).isEmpty());

        repository.restoreSearching(first.id(), second.id());

        assertEquals(QueueTicketState.SEARCHING, repository.find(first.id()).orElseThrow().state());
        assertEquals(QueueTicketState.SEARCHING, repository.find(second.id()).orElseThrow().state());

        QueuedMatchAssignment secondAssignment = repository.claimPair(first.id(), second.id()).orElseThrow();
        repository.save(secondAssignment.first().markMatched());

        repository.restoreSearching(first.id(), second.id());

        assertEquals(QueueTicketState.MATCHED, repository.find(first.id()).orElseThrow().state());
        assertEquals(QueueTicketState.SEARCHING, repository.find(second.id()).orElseThrow().state());
    }

    @Test
    void claimPairRejectsInvalidPairsWithoutMutatingTickets() {
        QueueTicketRepository sameTicketRepository = new InMemoryQueueTicketRepository();
        QueueTicket sameTicket = ticket("same", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        assertTrue(sameTicketRepository.create(sameTicket));

        assertTrue(sameTicketRepository.claimPair(sameTicket.id(), sameTicket.id()).isEmpty());
        assertEquals(QueueTicketState.SEARCHING, sameTicketRepository.find(sameTicket.id()).orElseThrow().state());

        QueueTicketRepository missingRepository = new InMemoryQueueTicketRepository();
        QueueTicket present = ticket("present", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        assertTrue(missingRepository.create(present));

        assertTrue(missingRepository
                .claimPair(present.id(), new QueueTicketId(UUID.nameUUIDFromBytes("missing".getBytes())))
                .isEmpty());
        assertEquals(QueueTicketState.SEARCHING, missingRepository.find(present.id()).orElseThrow().state());

        QueueTicketRepository stateRepository = new InMemoryQueueTicketRepository();
        QueueTicket pairing = ticket("pairing", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        QueueTicket searching = ticket("searching", "player-two", key("nodebuff"), 11L, 1000, QueueTicketState.SEARCHING);
        assertTrue(stateRepository.create(pairing));
        assertTrue(stateRepository.create(searching));
        stateRepository.save(pairing.markPairing());

        assertTrue(stateRepository.claimPair(pairing.id(), searching.id()).isEmpty());
        assertEquals(QueueTicketState.PAIRING, stateRepository.find(pairing.id()).orElseThrow().state());
        assertEquals(QueueTicketState.SEARCHING, stateRepository.find(searching.id()).orElseThrow().state());

        QueueTicketRepository keyRepository = new InMemoryQueueTicketRepository();
        QueueTicket nodebuff = ticket("nodebuff", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        QueueTicket sumo = ticket("sumo", "player-two", key("sumo"), 11L, 1000, QueueTicketState.SEARCHING);
        assertTrue(keyRepository.create(nodebuff));
        assertTrue(keyRepository.create(sumo));

        assertTrue(keyRepository.claimPair(nodebuff.id(), sumo.id()).isEmpty());
        assertEquals(QueueTicketState.SEARCHING, keyRepository.find(nodebuff.id()).orElseThrow().state());
        assertEquals(QueueTicketState.SEARCHING, keyRepository.find(sumo.id()).orElseThrow().state());

        QueueTicketRepository samePlayerRepository = new InMemoryQueueTicketRepository();
        QueueTicket first = ticket("first", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        QueueTicket samePlayer = ticket("same-player", "player-one", key("nodebuff"), 11L, 1000, QueueTicketState.SEARCHING);
        assertTrue(samePlayerRepository.create(first));
        IllegalStateException duplicateActive = assertThrows(
                IllegalStateException.class,
                () -> samePlayerRepository.save(samePlayer));

        assertEquals("player already has an active queue ticket", duplicateActive.getMessage());
        assertEquals(QueueTicketState.SEARCHING, samePlayerRepository.find(first.id()).orElseThrow().state());
        assertTrue(samePlayerRepository.find(samePlayer.id()).isEmpty());
    }

    @Test
    void concurrentCreateAllowsOnlyOneActiveTicketForTheSamePlayer() throws Exception {
        QueueTicketRepository repository = new InMemoryQueueTicketRepository();
        QueueTicket first = ticket("concurrent-first", "player-one", key("nodebuff"), 10L, 1000, QueueTicketState.SEARCHING);
        QueueTicket second = ticket("concurrent-second", "player-one", key("sumo"), 11L, 1000, QueueTicketState.SEARCHING);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> awaitAndCreate(repository, first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> awaitAndCreate(repository, second, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers should be ready before the race starts");
            start.countDown();

            boolean createdFirst = firstResult.get(5, TimeUnit.SECONDS);
            boolean createdSecond = secondResult.get(5, TimeUnit.SECONDS);

            assertEquals(1, (createdFirst ? 1 : 0) + (createdSecond ? 1 : 0));
            assertEquals(1, repository.findAll().size());
            assertTrue(repository.findByPlayer(first.playerId()).isPresent());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean awaitAndCreate(
            QueueTicketRepository repository,
            QueueTicket ticket,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "race should start promptly");
        return repository.create(ticket);
    }

    private static QueueTicket ticket(
            String seed,
            String playerSeed,
            QueueKey key,
            long joinedAtTick,
            int searchRating,
            QueueTicketState state) {
        return new QueueTicket(
                new QueueTicketId(UUID.nameUUIDFromBytes(seed.getBytes())),
                player(playerSeed),
                key,
                joinedAtTick,
                searchRating,
                state);
    }

    private static QueueKey key(String kitSeed) {
        return new QueueKey(QueueMode.RANKED, new KitId(kitSeed));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
