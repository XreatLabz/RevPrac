package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.domain.parties.Party;
import io.github.xreatlabz.revprac.domain.parties.PartyId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.parties.PartyRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryPartyRepository implements PartyRepository {

    private final Object mutex = new Object();
    private final ConcurrentMap<PartyId, Party> parties = new ConcurrentHashMap<>();

    @Override
    public Optional<Party> find(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        synchronized (mutex) {
            return Optional.ofNullable(parties.get(partyId));
        }
    }

    @Override
    public Collection<Party> findAll() {
        synchronized (mutex) {
            return List.copyOf(parties.values());
        }
    }

    @Override
    public Optional<Party> findByMember(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (mutex) {
            return parties.values().stream()
                    .filter(party -> party.contains(playerId))
                    .findFirst();
        }
    }

    @Override
    public boolean create(Party party) {
        Objects.requireNonNull(party, "party");
        synchronized (mutex) {
            if (parties.containsKey(party.id()) || overlappingMembersExist(party, party.id())) {
                return false;
            }
            parties.put(party.id(), party);
            return true;
        }
    }

    @Override
    public void save(Party party) {
        Objects.requireNonNull(party, "party");
        synchronized (mutex) {
            if (overlappingMembersExist(party, party.id())) {
                throw new IllegalStateException("party members are already assigned to another party");
            }
            parties.put(party.id(), party);
        }
    }

    @Override
    public void delete(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        synchronized (mutex) {
            parties.remove(partyId);
        }
    }

    private boolean overlappingMembersExist(Party candidate, PartyId retainedPartyId) {
        return parties.values().stream()
                .filter(storedParty -> !storedParty.id().equals(retainedPartyId))
                .anyMatch(storedParty -> candidate.members().stream().anyMatch(storedParty::contains));
    }
}
