package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class InMemoryMatchRepositoryTest {

    @Test
    void createFindLookupsSaveDeleteAndSnapshotsBehaveAtomically() {
        MatchRepository repository = new InMemoryMatchRepository();
        Match created = match("first", ruleset(true));

        assertTrue(repository.create(created));
        assertFalse(repository.create(match("first", ruleset(true))));
        assertFalse(repository.create(matchWithPlayers("second", created.participants(), ruleset(true))));
        assertEquals(created, repository.find(created.id()).orElseThrow());
        assertEquals(created, repository.findByPlayer(created.participants().playerOne()).orElseThrow());
        assertTrue(repository.findBySpectator(player("spectator")).isEmpty());

        Set<Match> firstSnapshot = Set.copyOf(repository.findAll());
        assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);

        Match withSpectator = created.tickCountdown().tickCountdown().addSpectator(player("spectator"));
        repository.save(withSpectator);

        assertEquals(withSpectator, repository.find(created.id()).orElseThrow());
        assertEquals(withSpectator, repository.findBySpectator(player("spectator")).orElseThrow());
        assertEquals(
                0,
                firstSnapshot.iterator().next().spectators().size(),
                "Earlier snapshots must stay immutable after later saves");

        repository.delete(created.id());
        assertTrue(repository.find(created.id()).isEmpty());
        assertTrue(repository.findByPlayer(created.participants().playerOne()).isEmpty());
    }

    @Test
    void createAllowsPlayersRetainedOnlyInCompletedMatches() {
        MatchRepository repository = new InMemoryMatchRepository();
        Match completed = match("retained", ruleset(true))
                .tickCountdown()
                .tickCountdown()
                .complete(MatchOutcome.shutdown());
        repository.save(completed);

        Match retryMatch = matchWithPlayers("retry", completed.participants(), ruleset(true));

        assertTrue(repository.create(retryMatch));
        assertEquals(retryMatch, repository.find(retryMatch.id()).orElseThrow());
    }

    @Test
    void concurrentCreateAllowsOnlyOneWinnerForTheSameParticipants() throws Exception {
        MatchRepository repository = new InMemoryMatchRepository();
        Match first = match("concurrent-first", ruleset(true));
        Match second = matchWithPlayers("concurrent-second", first.participants(), ruleset(true));
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
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean awaitAndCreate(
            MatchRepository repository,
            Match match,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "race should start promptly");
        return repository.create(match);
    }

    private static Match match(String seed, MatchRuleset ruleset) {
        return Match.create(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                new MatchParticipants(player(seed + "-one"), player(seed + "-two")),
                new ArenaId("arena-" + seed),
                new KitId("kit-" + seed),
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                ruleset);
    }

    private static Match matchWithPlayers(String seed, MatchParticipants participants, MatchRuleset ruleset) {
        return Match.create(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                participants,
                new ArenaId("arena-" + seed),
                new KitId("kit-" + seed),
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                ruleset);
    }

    private static MatchRuleset ruleset(boolean spectatorsEnabled) {
        return new MatchRuleset(2, 5, spectatorsEnabled);
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
