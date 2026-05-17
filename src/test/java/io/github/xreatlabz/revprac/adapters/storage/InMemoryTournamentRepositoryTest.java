package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.tournaments.Tournament;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentId;
import io.github.xreatlabz.revprac.ports.tournaments.TournamentRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InMemoryTournamentRepositoryTest {

    @Test
    void createFindSaveDeleteAndSnapshotsRemainStable() {
        TournamentRepository repository = new InMemoryTournamentRepository();
        Tournament created = Tournament.create(tournamentId("spring"), "Spring Cup", 8);

        assertTrue(repository.create(created));
        assertFalse(repository.create(Tournament.create(created.id(), "Duplicate", 8)));
        assertEquals(created, repository.find(created.id()).orElseThrow());

        Collection<Tournament> snapshot = repository.findAll();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        Tournament updated = created.open(Instant.parse("2026-05-17T16:00:00Z")).register(player("first"));
        repository.save(updated);

        assertEquals(updated, repository.find(created.id()).orElseThrow());
        assertEquals(List.of(created), List.copyOf(snapshot));

        repository.delete(created.id());
        assertTrue(repository.find(created.id()).isEmpty());
    }

    @Test
    void saveReplacesTheStoredSnapshotForTheSameTournamentId() {
        TournamentRepository repository = new InMemoryTournamentRepository();
        Tournament created = Tournament.create(tournamentId("summer"), "Summer Cup", 4);
        assertTrue(repository.create(created));

        Tournament updated = created.open(Instant.parse("2026-05-17T17:00:00Z"))
                .register(player("first"))
                .register(player("second"));
        repository.save(updated);

        assertEquals(updated, repository.find(created.id()).orElseThrow());
        assertEquals(1, repository.findAll().size());
    }

    private static TournamentId tournamentId(String seed) {
        return new TournamentId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
