package io.github.xreatlabz.revprac.application.parties;

import io.github.xreatlabz.revprac.domain.parties.Party;
import io.github.xreatlabz.revprac.domain.parties.PartyId;
import io.github.xreatlabz.revprac.domain.parties.PartyLeaveOutcome;
import io.github.xreatlabz.revprac.domain.parties.PartyQueueEligibilitySnapshot;
import io.github.xreatlabz.revprac.domain.parties.PartyStatus;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.parties.PartyRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PartyService {

    private final PartyRepository partyRepository;

    public PartyService(PartyRepository partyRepository) {
        this.partyRepository = Objects.requireNonNull(partyRepository, "partyRepository");
    }

    public PartyStatus createParty(PlayerId leaderId) {
        Objects.requireNonNull(leaderId, "leaderId");
        requireNoParty(leaderId);
        Party party = Party.create(new PartyId(UUID.randomUUID()), leaderId);
        if (!partyRepository.create(party)) {
            throw new IllegalStateException("player is already in a party");
        }
        return party.status();
    }

    public PartyStatus joinParty(PartyId partyId, PlayerId playerId) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(playerId, "playerId");
        requireNoParty(playerId);
        Party updatedParty = requireParty(partyId).join(playerId);
        partyRepository.save(updatedParty);
        return updatedParty.status();
    }

    public PartyLeaveOutcome leaveParty(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Party currentParty = requirePartyByMember(playerId);
        PartyLeaveOutcome outcome = currentParty.leave(playerId);
        if (outcome.disbanded()) {
            partyRepository.delete(currentParty.id());
            return outcome;
        }
        Party updatedParty = outcome.updatedParty().orElseThrow();
        partyRepository.save(updatedParty);
        return outcome;
    }

    public PartyStatus status(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        return requireParty(partyId).status();
    }

    public Optional<PartyStatus> statusByMember(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return partyRepository.findByMember(playerId).map(Party::status);
    }

    public PartyQueueEligibilitySnapshot queueEligibility(PartyId partyId, int requiredSize) {
        Objects.requireNonNull(partyId, "partyId");
        return requireParty(partyId).queueEligibility(requiredSize);
    }

    private void requireNoParty(PlayerId playerId) {
        if (partyRepository.findByMember(playerId).isPresent()) {
            throw new IllegalStateException("player is already in a party");
        }
    }

    private Party requireParty(PartyId partyId) {
        return partyRepository.find(partyId).orElseThrow(() -> new IllegalStateException("party not found"));
    }

    private Party requirePartyByMember(PlayerId playerId) {
        return partyRepository.findByMember(playerId).orElseThrow(() -> new IllegalStateException("player is not in a party"));
    }
}
