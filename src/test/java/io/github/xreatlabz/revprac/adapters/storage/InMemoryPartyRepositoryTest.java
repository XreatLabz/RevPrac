package io.github.xreatlabz.revprac.adapters.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.parties.Party;
import io.github.xreatlabz.revprac.domain.parties.PartyId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.parties.PartyRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InMemoryPartyRepositoryTest {

    @Test
    void createFindSaveDeleteAndSnapshotsRemainStable() {
        PartyRepository repository = new InMemoryPartyRepository();
        Party created = Party.create(partyId("first"), player("leader")).join(player("second"));

        assertTrue(repository.create(created));
        assertFalse(repository.create(Party.create(created.id(), player("other"))));
        assertEquals(created, repository.find(created.id()).orElseThrow());
        assertEquals(created, repository.findByMember(player("leader")).orElseThrow());

        Collection<Party> snapshot = repository.findAll();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        Party updated = created.join(player("third"));
        repository.save(updated);

        assertEquals(updated, repository.find(created.id()).orElseThrow());
        assertEquals(List.of(created), List.copyOf(snapshot));

        repository.delete(created.id());
        assertTrue(repository.find(created.id()).isEmpty());
        assertTrue(repository.findByMember(player("leader")).isEmpty());
    }

    @Test
    void repositoryRejectsOverlappingMembersAcrossParties() {
        PartyRepository repository = new InMemoryPartyRepository();
        Party first = Party.create(partyId("alpha"), player("leader")).join(player("shared"));
        Party second = Party.create(partyId("beta"), player("other"));
        assertTrue(repository.create(first));
        assertTrue(repository.create(second));

        Party overlappingCreate = Party.create(partyId("gamma"), player("shared"));
        assertFalse(repository.create(overlappingCreate));

        Party overlappingSave = second.join(player("shared"));
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> repository.save(overlappingSave));
        assertEquals("party members are already assigned to another party", failure.getMessage());
        assertEquals(second, repository.find(second.id()).orElseThrow());
    }

    private static PartyId partyId(String seed) {
        return new PartyId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
