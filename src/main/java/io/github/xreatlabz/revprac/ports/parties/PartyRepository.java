package io.github.xreatlabz.revprac.ports.parties;

import io.github.xreatlabz.revprac.domain.parties.Party;
import io.github.xreatlabz.revprac.domain.parties.PartyId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Collection;
import java.util.Optional;

public interface PartyRepository {

    Optional<Party> find(PartyId partyId);

    Collection<Party> findAll();

    Optional<Party> findByMember(PlayerId playerId);

    boolean create(Party party);

    void save(Party party);

    void delete(PartyId partyId);
}
